---
name: database
description: Custom JDBC patterns for helved-utbetaling - Migrator, Dao<T>, transaction { }, CoroutineDatasource. Triggers - "write a migration", "create a DAO", "database query", "transaction", "/database".
license: MIT
compatibility: opencode
metadata:
  audience: ai-assistant
  language: kotlin
  framework: jdbc-postgres-hikari
  domain: nav-payment-system
---

# Database

## When to use me

- Trigger: "write a migration" — adding a new `V<n>__*.sql` file under `<module>/migrations/`
- Trigger: "create a DAO" — defining a new `companion object : Dao<T>` for a table
- Trigger: "database query" — using `query()` / `update()` from a DAO inside `withContext(jdbcCtx)`
- Trigger: "transaction" — wrapping multi-statement DB work in `transaction { }`
- Trigger: "/database" — wiring `Jdbc.initialize(config).context()` into an app entry point

## Migration system

**Custom in-house `Migrator` class — no third-party schema-migration tool is used in this repo.**

Migration files live under `<module>/migrations/` and follow `V<n>__<description>.sql`.
The version is the first integer in the filename. Versions must increment by 1.
Checksums are MD5 of file contents and may NOT change after a script is applied.

```kotlin
// Source: libs/jdbc/main/libs/jdbc/Migrator.kt:39-49
    suspend fun migrate() {
        // Bootstrap the migrations table if it doesn't exist yet. Idempotent.
        executeSql(Resource.read("/migrations.sql"))

        val migrations = Migration.all().sortedBy { it.version }
        val candidates = files.map(MigrationWithFile::from).sortedBy { it.migration.version }

        // Fast-path: DB is already at HEAD with matching checksums.
        // Skips the per-call validation/log noise so test forks that clone
        // a pre-migrated template don't pay this cost twice.
        if (atHead(migrations, candidates)) return
```

```kotlin
// Source: libs/jdbc/main/libs/jdbc/Migrator.kt:228-233
private fun version(name: String): Int {
    return DIGIT_PATTERN.findAll(name)
        .map { it.value.toInt() }
        .firstOrNull() // first digit is the version
        ?: error("Version must be included in sql-filename: $name")
}
```

**Default migrations location** is `main/migrations` relative to the running module:

```kotlin
// Source: libs/jdbc/main/libs/jdbc/Jdbc.kt:74
    val migrations: List<File> = listOf(File("main/migrations"))
```

**Naming convention** (verified across utsjekk, urskog, vedskiva, peisschtappern, speiderhytta):

```
V1__create_table_oppdrag_metadata.sql
V2__widen_hash_key_to_text.sql
V20__utbetaling.sql
```

Rules enforced by `Migrator`:
- Versions must be sequential — `isValidSequence` requires `right.version == left.version + 1`
- Checksum mismatch on an applied migration → error
- Applied script missing on disk → error
- Each script runs inside `transaction { }` (auto-rollback on failure)

Run migrations once at app startup using `runBlocking`:

```kotlin
// Pattern used in app entry points
runBlocking {
    withContext(jdbcCtx) {
        Migrator(File("main/migrations")).migrate()
    }
}
```

## Dao<T> pattern

`Dao<T>` is a Kotlin interface implemented by a **companion object** of the row type.
The interface gives you `query()` and `update()` for free — no ORM, no reflection.

```kotlin
// Source: libs/jdbc/main/libs/jdbc/Dao.kt:10-48
interface Dao<T: Any> {

    val table: String

    fun from(rs: ResultSet): T

    suspend fun statement(sql: String): PreparedStatement = 
        currentCoroutineContext().connection.prepareStatement(sql)

    suspend fun query(sql: String, block: (PreparedStatement) -> Unit = {}): List<T> {
        return statement(sql).use { stmt ->
            block(stmt)
            secureLog.debug(stmt.toString())
            stmt.executeQuery().map(::from).also {
                jdbcLog.debug("$sql :: ${it.size} rows found.")
            }
        }
    }

    suspend fun <U> query(sql: String, mapper: (ResultSet) -> U, block: (PreparedStatement) -> Unit): List<U?> {
        return statement(sql).use { stmt ->
            block(stmt)
            secureLog.debug(stmt.toString())
            stmt.executeQuery().map(mapper).also {
                jdbcLog.debug("$sql :: ${it.size} rows found.")
            }
        }
    }

    suspend fun update(sql: String, block: (PreparedStatement) -> Unit): Int {
        return statement(sql).use { stmt -> 
            block(stmt)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate().also {
                jdbcLog.debug("$sql :: $it rows affected.")
            }
        }
    }
}
```

**Canonical implementation** — data class with `companion object : Dao<T>`:

```kotlin
// Source: apps/utsjekk/main/utsjekk/utbetaling/UtbetalingDao.kt:23-53
data class UtbetalingDao(
    val data: Utbetaling,
    val status: Status = Status.IKKE_PÅBEGYNT,
    val stønad: Stønadstype = data.stønad,
    val created_at: LocalDateTime = LocalDateTime.now(),
    val updated_at: LocalDateTime = created_at,
    val deleted_at: LocalDateTime? = null,
) {
    companion object : Dao<UtbetalingDao> {
        override val table = "utbetaling"

        override fun from(rs: ResultSet) = UtbetalingDao(
            data = objectMapper.readValue(rs.getString("data"), Utbetaling::class.java),
            stønad = rs.getString("stønad").let(Stønadstype::valueOf),
            status = rs.getString("status").let(Status::valueOf),
            created_at = rs.getTimestamp("created_at").toLocalDateTime(),
            updated_at = rs.getTimestamp("updated_at").toLocalDateTime(),
            deleted_at = rs.getTimestamp("deleted_at")?.toLocalDateTime(),
        )

        suspend fun findOrNull(id: UtbetalingId, history: Boolean = false): UtbetalingDao? {
            val sql = """
                SELECT * FROM $table
                WHERE utbetaling_id = ?
                ORDER BY created_at DESC
                LIMIT 1
            """.trimIndent()

            return query(sql) { stmt -> stmt.setObject(1, id.id) }
                .singleOrNull { it.deleted_at == null || history }
        }
```

Rules:
- DAOs are **not injected** — they are companion objects on the row type
- The connection is read from the coroutine context (see `CoroutineDatasource` below)
- `query()` returns `List<T>`, `update()` returns affected row count
- Use `$table` interpolation in SQL strings to keep table name DRY

## transaction { } suspending function

`transaction { }` is a suspending inline function that opens or reuses a transaction on the
current coroutine context. Auto-commits on success, rolls back on any throwable.

```kotlin
// Source: libs/jdbc/main/libs/jdbc/concurrency/Transaction.kt:18-43
@OptIn(ExperimentalContracts::class)
suspend inline fun <T> transaction(crossinline block: suspend CoroutineScope.() -> T): T {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    val existingTransaction = coroutineContext[CoroutineTransaction]

    return when {
        existingTransaction == null -> {
            withConnection {
                runTransactionally {
                    block()
                }
            }
        }

        !existingTransaction.completed -> {
            withContext(coroutineContext) {
                block()
            }
        }

        else -> error("Nested transactions not supported")
    }
}
```

Behaviour:
- A new top-level call opens a connection and a transaction
- A nested call inside an active `transaction { }` reuses the outer one (no nested commit)
- A nested call inside a *completed* transaction throws — design to avoid this
- On any thrown `Throwable` the connection is rolled back

Use it for any multi-statement work that must be atomic (insert + update, batch deletes, etc.).
Single-statement DAO calls do NOT need an explicit `transaction { }` — `Dao.query/update` runs
on the connection bound to the coroutine context.

## CoroutineDatasource (jdbcCtx)

`CoroutineDatasource` is a `CoroutineContext.Key` element that carries the `DataSource` on the
coroutine context. Each app constructs ONE per `DataSource` and threads it through DI.

```kotlin
// Source: libs/jdbc/main/libs/jdbc/concurrency/Contexts.kt:24-30
class CoroutineDatasource(
    val datasource: DataSource,
) : AbstractCoroutineContextElement(CoroutineDatasource) {
    companion object Key : CoroutineContext.Key<CoroutineDatasource>

    override fun toString() = "CoroutineDataSource($datasource)"
}
```

Construction at app startup:

```kotlin
// Source: libs/jdbc/main/libs/jdbc/Jdbc.kt:42-48
/**
 * Wrap a [DataSource] as a [CoroutineDatasource] suitable for use as a CoroutineContext element.
 *
 * Each app/test should construct its own [CoroutineDatasource] from its [DataSource] and pass it
 * explicitly through DI, enabling multiple datasources to coexist in a single JVM.
 */
fun DataSource.context(): CoroutineDatasource = CoroutineDatasource(this)
```

Wiring flow in `Application.<appName>()`:

```kotlin
val datasource = Jdbc.initialize(config.jdbc)
val jdbcCtx: CoroutineDatasource = datasource.context()

// Then either pass jdbcCtx to services explicitly:
val service = MyService(jdbcCtx)

// Or use the jdbc(ds) { } helper for ad-hoc blocks:
```

```kotlin
// Source: libs/jdbc/main/libs/jdbc/Jdbc.kt:56-64
suspend inline fun <T> jdbc(
    ds: CoroutineDatasource,
    io: Boolean = false,
    crossinline block: suspend CoroutineScope.() -> T,
): T = if (io) {
    withContext(ds + Dispatchers.IO) { block() }
} else {
    withContext(ds) { block() }
}
```

Rules:
- NEVER store `jdbcCtx` in a top-level `val` — pass it via constructor injection
- Service methods doing DB work use `withContext(jdbcCtx) { ... }` (root `AGENTS.md:138`)
- The same JVM may host multiple datasources — keep each `jdbcCtx` scoped to its app
- `connection` and `datasource` extension properties on `CoroutineContext` throw if missing

## Cross-references

- For test setup (`PostgresContainer`, `runTest(TestRuntime.context)`, `DataSource.await { }`) see the `testing` skill.
- For library API surface (`Jdbc`, `Migrator`, `Dao`, `transaction`, `jdbc { }`) see the `libs-reference` skill.
- For broader rules (naming, error handling, coroutine patterns) see root `AGENTS.md:130-140`.
