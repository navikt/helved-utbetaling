---
name: testing
description: Test patterns for helved-utbetaling — TestRuntime singleton, runTest with CoroutineDatasource context, DataSource.await polling, Testcontainers reuse, and hand-rolled fakes (no mocking framework). Triggers — write tests, test fails, TestRuntime, runTest, /testing.
license: MIT
compatibility: opencode
metadata:
  audience: ai-assistant
  language: kotlin
  framework: junit5
  domain: nav-payment-system
---

# Testing

> **Parent context:** Root `AGENTS.md:81-113` documents the testing rules. This skill provides verbatim code citations from the canonical reference app `apps/utsjekk/test/`. For test-lib API details see the `libs-reference` skill.

## When to use me

- Trigger: write tests for a new app or route
- Trigger: test fails or behaves flakily and needs the right context wrapper
- Trigger: wire up a new TestRuntime for an app
- Trigger: poll the database for an async write with DataSource.await
- Trigger: build a hand-rolled fake instead of reaching for a mocking framework

## TestRuntime pattern

Each app exposes a singleton `object TestRuntime` that lazily wires fakes, the Postgres container, the Kafka mock, and the Ktor test server. Lazy `by lazy` ensures one-time construction across parallel test classes.

```kotlin
// Source: apps/utsjekk/test/TestRuntime.kt:36-58
object TestRuntime {
    private val postgres: PostgresContainer by lazy {
        PostgresContainer(
            appname = "utsjekk",
            migrationDirs = listOf(File("migrations")),
            migrate = ::migrateTemplate,
        )
    }
    // Kafka mock and the ktor app form a chicken/egg pair: the StreamsMock
    // instance must exist before the ktor module runs (the module wires it
    // into the topology), but `kafka.testTopic(...)` only works AFTER the
    // module has called `connect()` which initializes the underlying
    // TopologyTestDriver. We hold the mock privately and force ktor init on
    // any external `kafka` access.
    private val kafkaMock: StreamsMock by lazy { StreamsMock() }
    val kafka: StreamsMock
        get() {
            ktor // ensures topology is connected
            return kafkaMock
        }
    val azure: AzureFake by lazy { AzureFake() }
    val simulering: SimuleringFake by lazy { SimuleringFake() }
    val jdbc: DataSource by lazy { Jdbc.initialize(postgres.config) }
    val context: CoroutineDatasource by lazy { CoroutineDatasource(jdbc) }
```

The `KtorRuntime` wires the actual app module with the lazy fakes and registers `onClose` cleanup that truncates DB tables between test classes:

```kotlin
// Source: apps/utsjekk/test/TestRuntime.kt:78-103
    val ktor: KtorRuntime<Config> by lazy {
        KtorRuntime<Config>(
            appName = "utsjekk",
            module = {
                utsjekk(
                    config,
                    kafkaMock,
                    jdbcCtx = context,
                    topology = kafkaMock.append(createTopology(context)) {
                        consume(Tables.saker)
                    }
                )
            },
            onClose = {
                jdbc.truncate(
                    "utsjekk",
                    IverksettingDao.table,
                    IverksettingResultatDao.table,
                    UtbetalingDao.table,
                )
                postgres.close()
                simulering.close()
                azure.close()
            },
        )
    }
```

## runTest with context

Wrap every coroutine test with `runTest(TestRuntime.context)` so DAO calls inherit the right `CoroutineDatasource`. Test names are backtick-quoted Norwegian sentences:

```kotlin
// Source: apps/utsjekk/test/utsjekk/routes/IverksettingRouteTest.kt:31-45
    @Test
    fun `start iverksetting av vedtak uten utbetaling`() = runTest(TestRuntime.context) {
        withContext(TestRuntime.context) {
            val dto = TestData.dto.iverksetting(
                vedtak = TestData.dto.vedtaksdetaljer(
                    utbetalinger = emptyList(),
                ),
            )
            httpClient.post("/api/iverksetting/v2") {
                bearerAuth(TestRuntime.azure.generateToken())
                contentType(ContentType.Application.Json)
                setBody(dto)
            }.let {
                assertEquals(HttpStatusCode.Accepted, it.status)
            }
```

`TestData` is an object with factory methods that supply sensible defaults for fixtures — keep the test body focused on what differs from default:

```kotlin
// Source: apps/utsjekk/test/TestData.kt:12-16
object TestData {
    val DEFAULT_FAGSYSTEM: Fagsystem = Fagsystem.DAGPENGER
    const val DEFAULT_SAKSBEHANDLER: String = "A123456"
    const val DEFAULT_BESLUTTER: String = "B23456"
```

## DataSource.await polling

Async pipelines (Kafka → DAO writes) require polling the DB until the expected row appears. `DataSource.await` runs `query()` every 10 ms until it returns non-null, with a 3 s default timeout:

```kotlin
// Source: libs/jdbc-test/main/libs/jdbc/JdbcUtils.kt:24-36
fun <T> DataSource.await(timeoutMs: Long = 3_000, query: suspend () -> T?): T? =
    runBlocking {
        withTimeoutOrNull(timeoutMs) {
            channelFlow {
                withContext(context() + Dispatchers.IO) {
                    while (true) transaction {
                        query()?.let { send(it) }
                        delay(10)
                    }
                }
            }.firstOrNull()
        }
    }
```

Usage from a route test — wait for the result row before asserting status:

```kotlin
// Source: apps/utsjekk/test/utsjekk/routes/IverksettingRouteTest.kt:47-56
            val status = runBlocking {
                TestRuntime.jdbc.await {
                    IverksettingResultatDao.select {
                        this.fagsystem = Fagsystem.TILLEGGSSTØNADER
                        this.sakId = SakId(dto.sakId)
                        this.behandlingId = BehandlingId(dto.behandlingId)
                    }.firstOrNull {
                        it.oppdragResultat != null
                    }
                }
```

A symmetric `awaitNull` is also exported for the inverse case (wait for absence — see `libs/jdbc-test/main/libs/jdbc/JdbcUtils.kt:38-50`).

## Test naming convention

- Backtick-quoted descriptive sentences — Norwegian preferred for domain behaviour.
- Match the user-visible flow; do not encode method names.
- Examples from the canonical app:
  - `start iverksetting av vedtak uten utbetaling`
  - `start iverksetting`
  - `start iverksetting av tilleggsstønader`

```kotlin
// Source: apps/utsjekk/test/utsjekk/routes/IverksettingRouteTest.kt:68-69
    @Test
    fun `start iverksetting`() = runTest(TestRuntime.context) {
```

Run a single backtick-named test from the CLI by quoting the full sentence after `--tests`:

```sh
./gradlew apps:utsjekk:test --tests "utsjekk.iverksetting.IverksettingRouteTest.start iverksetting av vedtak uten utbetaling"
```

## Testcontainers reuse

`PostgresContainer` shares one Postgres cluster across every app in the build (label `helved-pg`) and clones a per-fork DB from a migrated template. Containers persist between test runs (`testcontainers.reuse.enable=true` in `~/.testcontainers.properties`).

```kotlin
// Source: libs/jdbc-test/main/libs/jdbc/PostgresContainer.kt:51-65
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
```

If a container is stopped and a test fails with a 409 conflict, restart it manually:

```sh
docker start mq postgres
```

To replace stale containers after a base-image update:

```sh
docker stop mq postgres
docker container prune
```

## Fakes (no mocking framework)

This codebase deliberately avoids mocking frameworks. Instead, every external collaborator gets a hand-rolled fake — usually a real in-process implementation backed by `embeddedServer(Netty, port = 0, ...)` for HTTP fakes or an in-memory data structure for JMS/MQ.

HTTP fake — `AzureFake` boots a real Ktor server bound to a random port and serves `/jwks` and `/token`:

```kotlin
// Source: apps/utsjekk/test/fakes/AzureFake.kt:22-41
class AzureFake : AutoCloseable {
    private val azure = embeddedServer(Netty, port = 0, module = Application::azure).apply { start() }

    val config by lazy {
        AzureConfig(
            tokenEndpoint = "http://localhost:${azure.engine.port}/token".let(::URI).toURL(),
            jwks = "http://localhost:${azure.engine.port}/jwks".let(::URI).toURL(),
            issuer = "test",
            clientId = "hei",
            clientSecret = "på deg"
        )
    }

    private val jwksGenerator = JwkGenerator(config.issuer, config.clientId)

    fun generateToken(azp_name: String = Azp.TILLEGGSSTØNADER) =
        jwksGenerator.generate(listOf(Claim("azp_name", azp_name)))

    override fun close() = azure.stop(0, 0)
}
```

MQ fake — `FakeMQ` implements the `MQ` interface with an in-memory `JMSContextFake`, captures sent messages, and exposes a `fakeReply` hook for synchronous reply scenarios:

```kotlin
// Source: apps/urskog/test/urskog/FakeMQ.kt:13-32
class FakeMQ: MQ {
    override fun depth(queue: MQQueue): Int = context.sent[queue]?.size ?: -1
    override fun <T : Any> transaction(block: (JMSContext) -> T): T = block(context)
    override fun <T : Any> transacted(ctx: JMSContext, block: () -> T): T = block()
    override val context = JMSContextFake()

    /**
     *  fakeReply(TestRuntime.config.oppdrag.sendKø) { msg ->
     *      val oppdrag = mapper.readValue(msg.text)
     *      val response = oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
     *      TextMessageFake(mapper.writeValueAsString(response))
     *  }
     */ 
    fun fakeReply(dest: Destination, reply: (TextMessage) -> TextMessage) {
        context.fakeReply(dest, reply)
    }

    fun reset() {
        context.sent.forEach { it.value.clear() }
    }
```

Rules of thumb when writing a fake:

- Implement the same interface as production (`MQ`, `WS`, etc.) — no reflection tricks.
- Expose state for assertions (`sent`, `depth()`, `sentOppdrag()`).
- Make it `AutoCloseable` if it owns sockets/threads, and wire the close into `TestRuntime.ktor.onClose`.
- Reset between tests via a `reset()` method or via `TRUNCATE` in `onClose`.

## Cross-references

- Root `AGENTS.md:81-113` — testing rules and infrastructure inventory.
- `libs-reference` skill — `libs/jdbc-test`, `libs/kafka-test`, `libs/auth-test`, `libs/ktor-test`, `libs/mq-test` API details.
- `apps/utsjekk/test/` — canonical reference for full-stack tests.
- `apps/abetal/test/` — reference for pure Kafka Streams tests (no DB).
- `apps/urskog/test/` — reference for MQ + WS coordination tests.
