---
license: Apache-2.0
module: http4k-connect-core-fake
---

# http4k-connect-core-fake Reference

Testing support for http4k-connect — base classes for building fakes with chaos support.

## WithRunningFake (Integration Test Base)

```kotlin
abstract class WithRunningFake(fn: () -> ChaoticHttpHandler) : PortBasedTest {
    // Starts fake on random port, provides http: HttpHandler pointed at it
}

class MyServiceTest : WithRunningFake({ FakeMyService() }) {
    @Test
    fun `can do thing`() {
        val client = MyService.Http(ApiKey.of("test"), http)
        assertThat(client.someAction(), equalTo(Success(expectedValue)))
    }
}
```

## FakeSystemContract (Chaos Testing)

```kotlin
// All fakes extend ChaoticHttpHandler which supports chaos behavior
val fake = FakeMyService()

// Inject chaos
fake.returnStatus(Status.SERVICE_UNAVAILABLE)

// Reset to normal
fake.behave()

// Test contract
abstract class FakeMyServiceTest : FakeSystemContract() {
    abstract val fake: ChaoticHttpHandler
}
```

## ChaoticHttpHandler

```kotlin
abstract class ChaoticHttpHandler : HttpHandler {
    fun returnStatus(status: Status)         // always return this status
    fun behave()                              // return to normal
    fun start(port: Int = 0): Http4kServer   // start as HTTP server
}
```

## Writing a Fake

```kotlin
class FakeMyService(
    val storage: Storage<MyEntity> = Storage.InMemory(),
    clock: Clock = Clock.systemUTC()
) : ChaoticHttpHandler() {
    override val app = routes(
        "/api/items" bind GET to { req ->
            Response(OK).with(Body.auto<List<MyEntity>>().toLens() of storage.keySet().mapNotNull { storage[it] })
        },
        "/api/items/{id}" bind GET to { req ->
            val id = Path.of("id")(req)
            storage[id]?.let { Response(OK).with(Body.auto<MyEntity>().toLens() of it) }
                ?: Response(NOT_FOUND)
        }
    )

    fun client() = MyService.Http(ApiKey.of("test"), this)
}
```

## Gotchas

- Fakes start on random ports (`start(0)`) for test isolation
- `WithRunningFake` starts/stops the server around each test (`@BeforeEach`/`@AfterEach`)
- Use `fake.client()` convenience methods to avoid repeating auth setup in tests
- Chaos behavior (`returnStatus`) is reset automatically between `WithRunningFake` tests
