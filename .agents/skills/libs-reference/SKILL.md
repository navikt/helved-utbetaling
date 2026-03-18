---
name: libs-reference
description: Library API reference for helved-utbetaling internal /libs
license: MIT
compatibility: opencode
metadata:
  audience: ai-assistant
  language: kotlin
  framework: ktor
  domain: nav-payment-system
---

# Library API Reference

> **Parent context:** See root `AGENTS.md` for conventions, build system, and patterns.

## Production Libraries

### Infrastructure - Database

#### **libs:jdbc** - Database Access
**Location:** `libs/jdbc/main/libs/jdbc/`

**Main Exports:**
- `Dao<T>` interface: Pattern for database operations
- `Jdbc` object: CoroutineContext element for connection management
- `Migrator`: Schema migration system
- `transaction { }`: Suspending DB transaction block
- `concurrency.*`: Connection management primitives

**Usage Pattern:**
```kotlin
object UserDao : Dao<User> {
    override val table = "users"
    override fun from(rs: ResultSet) = User(
        id = rs.getInt("id"),
        name = rs.getString("name")
    )
    
    suspend fun findById(id: Int): User? = withContext(Jdbc.context) {
        query("SELECT * FROM $table WHERE id = ?") { stmt ->
            stmt.setInt(1, id)
        }.firstOrNull()
    }
    
    suspend fun insert(user: User): Int = withContext(Jdbc.context) {
        update("INSERT INTO $table (name) VALUES (?)") { stmt ->
            stmt.setString(1, user.name)
        }
    }
}
```

**Key Patterns:**
- Companion objects implement `Dao<T>`
- Always use `withContext(Jdbc.context)` for DB operations
- Use `transaction { }` for multi-statement atomic operations
- `query()` returns `List<T>`, `update()` returns affected row count
- Custom mapper: `query(sql, mapper = { rs -> CustomType(...) }) { stmt -> ... }`

**Dependencies:** HikariCP, PostgreSQL driver, kotlinx-coroutines

---

#### **libs:jdbc-test** - Database Testing
**Location:** `libs/jdbc-test/main/libs/jdbc-test/`

**Main Exports:**
- `PostgresContainer`: Testcontainers PostgreSQL setup
- `JdbcUtils`: Test utilities

**Usage Pattern:**
```kotlin
object TestRuntime {
    val datasource: DataSource = PostgresContainer.dataSource
    val context: CoroutineContext = Jdbc.context + datasource.asContextElement()
}

@Test fun `test database operation`() = runTest(TestRuntime.context) {
    UserDao.insert(User(name = "Test"))
    val user = UserDao.findById(1)
    assertNotNull(user)
}
```

**Reusable Containers:** Containers persist between test runs. If stopped: `docker start postgres`

---

### Infrastructure - Messaging

#### **libs:kafka** - Kafka Streams DSL
**Location:** `libs/kafka/main/libs/kafka/`

**Main Exports:**
- `topology { }`: DSL for building Kafka Streams topologies
- `Topic<K, V>`: Type-safe topic abstraction
- `Table<K, V>`, `Store<K, V>`: State store abstractions
- `Serde`: Serialization helpers (JSON, XML, etc.)
- Stream operations: `consume()`, `.map()`, `.branch()`, `.produce()`
- `ConsumerProducer`: Vanilla Kafka producer/consumer wrappers
- `KafkaStreams`: Streams runtime

**Usage Pattern:**
```kotlin
object Topics {
    val input = Topic<String, PaymentRequest>(
        name = "helved.input.v1",
        keySerde = Serdes.String(),
        valueSerde = JsonSerde(PaymentRequest::class)
    )
    val output = Topic<String, PaymentResult>(
        name = "helved.output.v1",
        keySerde = Serdes.String(),
        valueSerde = JsonSerde(PaymentResult::class)
    )
}

val topology = topology("app-name") {
    Topics.input.consume { key, value ->
        val result = processPayment(value)
        Topics.output.produce(key, result)
    }
}

// With state store
val topology = topology("app-name") {
    val store = Topics.stateStore.globalKTable()
    
    Topics.input.consume { key, value ->
        val state = store[key]
        // Process with state
        Topics.output.produce(key, result)
    }
}
```

**Key Patterns:**
- Define `Topic` objects in a `Topics` object per app
- `consume { }` for stream processing
- `.map()`, `.filter()`, `.branch()` for transformations
- `globalKTable()` for read-only state stores
- NOT coroutine-based (different from HTTP/DB layers)
- Use `Result.catch { }` pattern for error handling in processors

**Dependencies:** Kafka Streams, Jackson for JSON

---

#### **libs:kafka-test** - Kafka Streams Testing
**Location:** `libs/kafka-test/main/libs/kafka-test/`

**Main Exports:**
- `StreamsMock`: In-memory Kafka Streams testing
- `TestTopic<K, V>`: Test topic wrapper
- `ProducerConsumerFake`: Fake producer/consumer
- `VanillaKafkaMock`: Vanilla Kafka testing utilities

**Usage Pattern:**
```kotlin
object TestRuntime {
    val streamsMock = StreamsMock()
}

@Test fun `test kafka topology`() {
    val topology = createTopology()
    TestRuntime.streamsMock.start(topology)
    
    val inputTopic = TestTopic(Topics.input)
    val outputTopic = TestTopic(Topics.output)
    
    inputTopic.produce("key1", PaymentRequest(...))
    
    val output = outputTopic.consume()
    assertEquals("key1", output.key)
    assertNotNull(output.value)
}
```

---

#### **libs:mq** - IBM MQ Integration
**Location:** `libs/mq/main/libs/mq/`

**Main Exports:**
- `DefaultMQ`: MQ connection manager
- `MQProducer`: Send messages to MQ queues
- `MQConsumer`: Consume messages from MQ queues

**Usage Pattern:**
```kotlin
val mq = DefaultMQ(config)
val producer = mq.createProducer("QUEUE.NAME")

producer.send(oppdragXml.toByteArray())

val consumer = mq.createConsumer("KVITTERING.QUEUE")
consumer.receive { message ->
    // Process kvittering
}
```

**Dependencies:** JMS, IBM MQ client

---

#### **libs:mq-test** - IBM MQ Testing
**Location:** `libs/mq-test/main/libs/mq-test/`

**Main Exports:**
- `MQFake`: In-memory MQ fake for testing

**Usage Pattern:**
```kotlin
object TestRuntime {
    val mqFake = MQFake()
}

@Test fun `test MQ integration`() {
    val producer = TestRuntime.mqFake.createProducer("QUEUE")
    producer.send("test message".toByteArray())
    
    val messages = TestRuntime.mqFake.getMessages("QUEUE")
    assertEquals(1, messages.size)
}
```

---

### Infrastructure - HTTP & Auth

#### **libs:http** - HTTP Client Factory
**Location:** `libs/http/main/libs/http/`

**Main Exports:**
- `HttpClientFactory.new()`: Creates Ktor HTTP client with sensible defaults

**Usage Pattern:**
```kotlin
val client = HttpClientFactory.new {
    install(JsonFeature) {
        serializer = JacksonSerializer()
    }
}

val response = client.get("https://api.example.com/data")
```

**Dependencies:** Ktor client (CIO engine)

---

#### **libs:ktor** - Ktor Plugins
**Location:** `libs/ktor/main/libs/ktor/`

**Main Exports:**
- `CallLog`: Request timing and logging plugin

**Usage Pattern:**
```kotlin
fun Application.module() {
    install(CallLog) // Logs request timing and path
    install(ContentNegotiation) { jackson() }
    routing {
        get("/") { call.respond("OK") }
    }
}
```

---

#### **libs:ktor-test** - Ktor Testing
**Location:** `libs/ktor-test/main/libs/ktor-test/`

**Main Exports:**
- `KtorRuntime`: Test server and client setup

**Usage Pattern:**
```kotlin
object TestRuntime {
    val ktorRuntime = KtorRuntime(Application::myApp)
    val httpClient = ktorRuntime.client
}

@Test fun `test HTTP endpoint`() = runTest {
    val response = TestRuntime.httpClient.get("/api/health")
    assertEquals(HttpStatusCode.OK, response.status)
}
```

---

#### **libs:auth** - Authentication
**Location:** `libs/auth/main/libs/auth/`

**Main Exports:**
- `TokenValidator`: JWT token validation (Azure AD, TokenX)
- `TokenClient`: Token acquisition client
- `AzureTokenProvider`: Azure AD token provider
- `TokenConfig`: Configuration for token validation

**Usage Pattern:**
```kotlin
val tokenValidator = TokenValidator(config)

fun Application.module() {
    install(Authentication) {
        bearer("azure-ad") {
            authenticate { token ->
                tokenValidator.validate(token)
            }
        }
    }
    
    routing {
        authenticate("azure-ad") {
            get("/protected") {
                val principal = call.principal<TokenPrincipal>()
                call.respond("Hello ${principal.subject}")
            }
        }
    }
}
```

**Dependencies:** Ktor auth, Nimbus JOSE JWT

---

#### **libs:auth-test** - Auth Testing
**Location:** `libs:auth-test/main/libs/auth-test/`

**Main Exports:**
- `JwkGenerator`: Generates test JWTs

**Usage Pattern:**
```kotlin
val jwt = JwkGenerator.generateToken(
    subject = "test-user",
    issuer = "test-issuer",
    audience = "test-audience"
)

val client = TestRuntime.httpClient
client.get("/protected") {
    bearerAuth(jwt)
}
```

---

### Infrastructure - Web Services

#### **libs:ws** - SOAP Web Services
**Location:** `libs/ws/main/libs/ws/`

**Main Exports:**
- `SoapClient`: Generic SOAP client
- `Sts`: STS (Security Token Service) integration for SOAP auth

**Usage Pattern:**
```kotlin
val stsClient = Sts(config)
val soapClient = SoapClient(
    endpoint = "https://soap.example.com/service",
    stsClient = stsClient
)

val request = SimulerBeregningRequest(...)
val response = soapClient.call<SimulerBeregningResponse>(request)
```

**Dependencies:** CXF, JAXB

---

### Infrastructure - Observability

#### **libs:tracing** - OpenTelemetry Tracing
**Location:** `libs/tracing/main/libs/tracing/`

**Main Exports:**
- `Tracing`: OpenTelemetry setup
- `tracer`: Global tracer instance
- Context propagation helpers

**Usage Pattern:**
```kotlin
val span = tracer.spanBuilder("operation-name").startSpan()
try {
    span.makeCurrent().use {
        performOperation()
    }
    span.setStatus(StatusCode.OK)
} catch (e: Exception) {
    span.recordException(e)
    span.setStatus(StatusCode.ERROR)
} finally {
    span.end()
}
```

**Dependencies:** OpenTelemetry SDK

---

### Infrastructure - Utilities

#### **libs:cache** - Token Caching
**Location:** `libs/cache/main/libs/cache/`

**Main Exports:**
- `Cache<T>`: Generic cache with expiry
- `TokenCache`: Specialized token cache
- `CacheKey`: Key abstraction

**Usage Pattern:**
```kotlin
val cache = Cache<String, Token>(
    ttl = 3600.seconds,
    loader = { key -> fetchToken(key) }
)

val token = cache.get("azure-ad")
```

---

#### **libs:utils** - Common Utilities
**Location:** `libs/utils/main/libs/utils/`

**Main Exports:**
- `Result<V, E>`: Rust-style Result type with `Ok`/`Err`
- `Env`: Environment variable helpers
- `Log`: Logging utilities (`appLog`, `secureLog`)
- `Resource`: Resource loading
- `CsvReader`: CSV parsing
- String extensions

**Usage Pattern:**
```kotlin
// Result type
val result: Result<Int, String> = Result.catch {
    riskyOperation()
}.mapError { e -> e.message ?: "Unknown error" }

result.fold(
    onSuccess = { value -> println("Success: $value") },
    onFailure = { error -> println("Error: $error") }
)

// Environment
val dbUrl = env("DATABASE_URL") // Throws if missing
val dbUrlOpt = envOrNull("DATABASE_URL") // Returns null if missing

// Logging
appLog.info("Application started")
secureLog.debug("Processing payment for personident: $personident")
```

---

## Common Integration Patterns

### Ktor + Auth + JDBC
```kotlin
fun Application.myApp() {
    val datasource = HikariDataSource(hikariConfig)
    val tokenValidator = TokenValidator(authConfig)
    
    install(Authentication) {
        bearer("azure-ad") {
            authenticate { tokenValidator.validate(it) }
        }
    }
    
    routing {
        authenticate("azure-ad") {
            get("/data") {
                withContext(Jdbc.context + datasource.asContextElement()) {
                    val data = MyDao.findAll()
                    call.respond(data)
                }
            }
        }
    }
}
```

### Kafka + JDBC (Transaction Pattern)
```kotlin
// Kafka Streams does NOT support coroutines
// For transactional guarantees, use DB as coordination layer (see urskog pattern)

val topology = topology("app") {
    Topics.input.consume { key, value ->
        val result = process(value)
        store.put(key, result)
        // Separate coroutine-based consumer reads from store and writes to DB
    }
}
```

### Kafka + HTTP (Status Sync Pattern)
```kotlin
// utsjekk pattern: sync Kafka status to PostgreSQL for HTTP queries

val topology = topology("app") {
    Topics.status.consume { key, statusUpdate ->
        statusStore.put(key, statusUpdate)
    }
}

routing {
    get("/status/{id}") {
        withContext(Jdbc.context) {
            val status = StatusDao.findById(call.parameters["id"]!!)
            call.respond(status ?: HttpStatusCode.NotFound)
        }
    }
}

launch {
    while (true) {
        syncStateToDb()
        delay(5.seconds)
    }
}
```

---

## Testing Integration

### Full Stack Test (HTTP + DB + Kafka)
```kotlin
object TestRuntime {
    val datasource = PostgresContainer.dataSource
    val streamsMock = StreamsMock()
    val context = Jdbc.context + datasource.asContextElement()
    val ktorRuntime = KtorRuntime(Application::myApp)
    val httpClient = ktorRuntime.client
}

@Test fun `full integration test`() = runTest(TestRuntime.context) {
    TestRuntime.streamsMock.start(createTopology())
    
    val inputTopic = TestTopic(Topics.input)
    inputTopic.produce("key1", PaymentRequest(...))
    
    TestRuntime.datasource.await {
        MyDao.findById("key1") != null
    }
    
    val response = TestRuntime.httpClient.get("/payment/key1")
    assertEquals(HttpStatusCode.OK, response.status)
}
```

**Key Testing Utilities:**
- `DataSource.await { }`: Polls DB until condition is true (async processing)
- `runTest(TestRuntime.context)`: Wraps test with correct coroutine context
- `TestTopic.produce()`: Produces to in-memory Kafka
- `StreamsMock.start()`: Starts Kafka Streams topology in-memory

---

## Dependency Management

When adding library dependencies between modules:
```kotlin
dependencies {
    implementation(project(":libs:jdbc"))
    implementation(project(":libs:kafka"))
    implementation(project(":models"))
    
    testImplementation(project(":libs:jdbc-test"))
    testImplementation(project(":libs:kafka-test"))
}
```

Versions are declared inline (no version catalog):
```kotlin
val ktorVersion = "3.0.2"
implementation("io.ktor:ktor-server-core:$ktorVersion")
```

---

## Library Maintenance

### Adding a New Library

1. Create `libs/newlib/main/libs/newlib/` for sources
2. Create `libs/newlib/build.gradle.kts` with dependencies
3. Add to `settings.gradle.kts`
4. Follow naming conventions (see root AGENTS.md)
5. Add exports to this file

### Test Library Companion

If creating a production library, consider creating a test companion:
- `libs:jdbc` -> `libs:jdbc-test`
- `libs:kafka` -> `libs:kafka-test`
- `libs:auth` -> `libs:auth-test`
- Pattern: Production lib = real impl, test lib = fakes/mocks/Testcontainers
