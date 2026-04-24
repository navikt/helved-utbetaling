package libs.jdbc

import libs.jdbc.JdbcConfig
import libs.utils.env
import libs.utils.logger
import org.testcontainers.postgresql.PostgreSQLContainer
import java.io.File
import java.security.MessageDigest

private val testLog = logger("test")

/**
 * A reusable Postgres test container that:
 *
 *  - Shares a single Postgres cluster across ALL apps in the build
 *    (label "helved-pg"). One container, many databases.
 *  - For each app, lazily creates a TEMPLATE database populated by running
 *    the supplied migration callback once. The template is keyed by a hash
 *    of the app name + migration files; if migrations change the template
 *    is dropped and rebuilt.
 *  - For each Gradle test worker (JVM fork) cheaply clones the template
 *    into `<app>_w<workerId>` via `CREATE DATABASE ... TEMPLATE template_<app>`.
 *
 *  This lets parallel JVM forks have isolated, pre-migrated databases at
 *  near-zero cost per fork.
 */
class PostgresContainer(
    private val appname: String,
    waitForContainerMs: Long = 30_000,
    /**
     * Optional migration paths used to:
     *   1) compute a stable hash that invalidates the template when files
     *      change, and
     *   2) be passed to the migration callback when the template is built.
     *
     * If empty, the template is built without any migrations (test schema
     * starts blank) and the per-fork DB is just an empty clone.
     */
    private val migrationDirs: List<File> = emptyList(),
    /**
     * Called once per JVM the first time the template needs to be created
     * (or recreated due to changed migrations). Receives a JdbcConfig
     * pointing at the template database; the caller is expected to apply
     * its full migration suite to it.
     *
     * If null, the template is left empty.
     */
    private val migrate: ((JdbcConfig) -> Unit)? = null,
) : AutoCloseable {

    private val container = PostgreSQLContainer("postgres:15").apply {
        if (!isGHA()) {
            withLabel("service", SHARED_LABEL)
            withReuse(true)
            withNetwork(null)
            withCreateContainerCmdModifier { cmd ->
                cmd.withName("$SHARED_LABEL-${System.currentTimeMillis()}")
                cmd.hostConfig?.apply {
                    withMemory(1024 * 1024 * 1024)
                    withMemorySwap(2048 * 1024 * 1024)
                }
            }
        }
        start()
    }

    private fun waitUntilJdbcAvailable(retries: Int, delayMillis: Long) {
        repeat(retries) {
            try {
                java.sql.DriverManager.getConnection(
                    container.jdbcUrl,
                    container.username,
                    container.password,
                ).use { con ->
                    if (!con.isClosed) return
                }
            } catch (e: Exception) {
                Thread.sleep(delayMillis)
            }
        }
        error("Postgres container did not become available after ${retries * delayMillis} ms")
    }

    val config: JdbcConfig by lazy {
        waitUntilJdbcAvailable(30, waitForContainerMs / 30)

        val templateName = "template_${safe(appname)}"
        val migrationsHash = computeMigrationsHash()
        ensureTemplate(templateName, migrationsHash)

        val forkDbName = perForkDbName(appname)
        cloneFromTemplate(templateName, forkDbName)

        JdbcConfig(
            host = container.host,
            port = container.firstMappedPort.toString(),
            database = forkDbName,
            username = container.username,
            password = container.password,
            migrations = migrationDirs.ifEmpty { listOf(File("main/migrations")) },
        )
    }

    private fun perForkDbName(app: String): String {
        val workerId = System.getProperty("org.gradle.test.worker")
            ?: System.nanoTime().toString().takeLast(8)
        return "${safe(app)}_w$workerId"
    }

    private fun safe(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9_]"), "_")

    /**
     * Hash all migration files (filename + contents) so that any change to
     * the schema invalidates the cached template.
     */
    private fun computeMigrationsHash(): String {
        val md = MessageDigest.getInstance("SHA-256")
        migrationDirs
            .flatMap { dir -> dir.listFiles { f -> f.extension == "sql" }?.toList() ?: emptyList() }
            .sortedBy { it.name }
            .forEach { file ->
                md.update(file.name.toByteArray())
                md.update(file.readBytes())
            }
        return md.digest().joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun ensureTemplate(templateName: String, migrationsHash: String) {
        // Use an advisory lock so concurrent JVM forks/modules creating
        // the SAME template don't race.
        val lockKey = templateName.hashCode().toLong()
        adminConnection().use { con ->
            con.autoCommit = true
            con.createStatement().use { st ->
                st.execute("SELECT pg_advisory_lock($lockKey)")
                try {
                    val storedHash = readTemplateHash(con, templateName)
                    if (storedHash == migrationsHash) {
                        return
                    }

                    if (storedHash != null) {
                        testLog.info("Template $templateName stale (was $storedHash, want $migrationsHash) — rebuilding")
                        terminateConnections(con, templateName)
                        st.execute("ALTER DATABASE $templateName WITH IS_TEMPLATE FALSE")
                        st.execute("DROP DATABASE $templateName")
                    }

                    st.execute("CREATE DATABASE $templateName")
                    val templateConfig = JdbcConfig(
                        host = container.host,
                        port = container.firstMappedPort.toString(),
                        database = templateName,
                        username = container.username,
                        password = container.password,
                        migrations = migrationDirs.ifEmpty { listOf(File("main/migrations")) },
                    )
                    migrate?.invoke(templateConfig)
                    // Defensive: in case the migrate callback didn't close
                    // its pool, kick any stragglers off the template DB so
                    // we can mark it IS_TEMPLATE and other forks can clone.
                    terminateConnections(con, templateName)
                    writeTemplateHash(con, templateName, migrationsHash)
                    st.execute("ALTER DATABASE $templateName WITH IS_TEMPLATE TRUE")
                    testLog.info("Template $templateName ready (hash=$migrationsHash)")
                } finally {
                    st.execute("SELECT pg_advisory_unlock($lockKey)")
                }
            }
        }
    }

    private fun readTemplateHash(con: java.sql.Connection, templateName: String): String? {
        con.createStatement().use { st ->
            st.executeQuery(
                "SELECT 1 FROM pg_database WHERE datname = '$templateName'"
            ).use { rs ->
                if (!rs.next()) return null
            }
            // Hash is stored as a database comment (cheap, persistent).
            st.executeQuery(
                "SELECT shobj_description(d.oid, 'pg_database') FROM pg_database d WHERE datname = '$templateName'"
            ).use { rs ->
                rs.next()
                return rs.getString(1)
            }
        }
    }

    private fun writeTemplateHash(con: java.sql.Connection, templateName: String, hash: String) {
        con.createStatement().use { st ->
            st.execute("COMMENT ON DATABASE $templateName IS '$hash'")
        }
    }

    private fun terminateConnections(con: java.sql.Connection, dbName: String) {
        con.createStatement().use { st ->
            st.execute(
                "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
                    "WHERE datname = '$dbName' AND pid <> pg_backend_pid()"
            )
        }
    }

    private fun cloneFromTemplate(templateName: String, forkDbName: String) {
        val lockKey = forkDbName.hashCode().toLong()
        adminConnection().use { con ->
            con.autoCommit = true
            con.createStatement().use { st ->
                st.execute("SELECT pg_advisory_lock($lockKey)")
                try {
                    val exists = st.executeQuery(
                        "SELECT 1 FROM pg_database WHERE datname = '$forkDbName'"
                    ).use { it.next() }

                    if (exists) {
                        // Reusing a fork DB across runs (same worker id, same JVM
                        // restart): drop and re-clone to ensure a clean slate.
                        terminateConnections(con, forkDbName)
                        st.execute("DROP DATABASE $forkDbName")
                    }
                    // CREATE DATABASE ... TEMPLATE fails if anyone is connected
                    // to the source. Be defensive in case a previous run left
                    // a stray connection open.
                    terminateConnections(con, templateName)
                    st.execute("CREATE DATABASE $forkDbName TEMPLATE $templateName")
                } finally {
                    st.execute("SELECT pg_advisory_unlock($lockKey)")
                }
            }
        }
    }

    private fun adminConnection(): java.sql.Connection =
        java.sql.DriverManager.getConnection(
            container.jdbcUrl,
            container.username,
            container.password,
        )

    override fun close() {
        // GitHub Actions service containers or testcontainers is not reusable and must be closed
        if (isGHA()) {
            container.close()
        }
    }

    companion object {
        /**
         * Single shared label — all apps share one Postgres cluster, with
         * databases isolated per app/fork.
         */
        private const val SHARED_LABEL = "helved-pg"
    }
}

private fun isGHA(): Boolean = env("GITHUB_ACTIONS", false)

/**
 * Default migrate callback for [PostgresContainer]: spins up a short-lived
 * Hikari pool against the template database, runs the supplied migrations,
 * and closes the pool so the template DB can be cloned.
 *
 * Use as:
 * ```
 * PostgresContainer("urskog", migrationDirs, migrate = ::migrateTemplate)
 * ```
 * or pass an inline lambda when the migration list differs from the path.
 */
fun migrateTemplate(config: JdbcConfig) {
    val ds = Jdbc.initialize(config)
    try {
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withContext(libs.jdbc.concurrency.CoroutineDatasource(ds)) {
                Migrator(config.migrations).migrate()
            }
        }
    } finally {
        // The template DB cannot be marked IS_TEMPLATE / cloned while
        // connections are open against it, so release the pool here.
        (ds as? AutoCloseable)?.close()
    }
}
