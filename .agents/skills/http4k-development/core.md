---
license: Apache-2.0
module: http4k-core
---

# http4k-core Reference

The foundation module. Provides `HttpHandler`, `Filter`, `Request`, `Response`, routing, lenses, events, and the built-in `SunHttp` server.

## Type Aliases

```kotlin
typealias HttpHandler = (Request) -> Response
typealias Headers = Parameters
typealias Parameters = List<Parameter>
typealias Parameter = Pair<String, String?>
```

## Request and Response

Both implement `HttpMessage` — immutable, all methods return modified copies.

### Request Construction

```kotlin
Request(Method.GET, "http://example.com/path")
Request(Method.GET, Uri.of("http://example.com"))
Request(Method.POST, "/submit").body("payload")
```

### Response Construction

```kotlin
Response(Status.OK)
Response(Status.CREATED).body("""{"id": 1}""")
Response(Status.NOT_FOUND).body("not found")
```

### Modifying Messages

```kotlin
// Headers — cumulative by default
request.header("X-Foo", "bar")            // adds header
request.replaceHeader("X-Foo", "baz")     // replaces all values for key
request.removeHeader("X-Foo")             // removes all values for key

// Queries — also cumulative
request.query("page", "1")               // adds query param
request.removeQuery("page")              // removes all values for key

// Body
request.body("string body")
request.body(inputStream)
request.body(Body("bytes".toByteArray()))

// The `with` function applies multiple modifications
request.with(
    Query.required("id") of "123",
    Header.required("X-Token") of "abc"
)
```

### Gotchas

- **Headers are cumulative**: `request.header("X-Foo", "a").header("X-Foo", "b")` results in TWO `X-Foo` headers. Use `replaceHeader` to overwrite.
- **Query params are cumulative**: Same behavior as headers. Use `removeQuery` before re-adding to replace.
- **StreamBody reads once**: `body.stream` can only be consumed once. Use `body.payload` to buffer into memory (pulls entire stream). Always `close()` a StreamBody if not returning it to the caller.
- **Body equality**: Comparing two `MemoryBody` instances checks content. Comparing `StreamBody` instances consumes the stream.

## Status

```kotlin
Status.OK                     // 200
Status.CREATED                // 201
Status.NOT_FOUND              // 404
Status.INTERNAL_SERVER_ERROR  // 500

// Custom description — CR and LF characters are rejected
Status(200, "Everything is fine")  // OK
// Status(200, "Bad\r\nDescription") // throws IllegalArgumentException

// Range checks
status.successful             // 200-299
status.clientError            // 400-499
status.serverError            // 500-599
status.informational          // 100-199
status.redirection            // 300-399
```

### Gotchas

- **Status description rejects CR/LF**: Constructing a `Status` with `\r` or `\n` in the description throws `IllegalArgumentException`. This prevents HTTP response splitting attacks when building `Status` from user-supplied data.

## Uri

```kotlin
Uri.of("http://user:pass@host:8080/path?q=v#frag")

uri.scheme("https")
uri.host("newhost")
uri.port(8080)
uri.path("/new/path")
uri.query("key", "value")          // adds query param
uri.removeQuery("key")
uri.extend(otherUri)               // combine paths and queries
uri.relative("other-path")         // resolve relative URI against base
uri.credentials(Credentials("u", "p"))

// Same-origin check (scheme + host + effective port match)
uri.isSameOrigin(other)            // treats http:80 and https:443 as default ports
```

## Filter

```kotlin
fun interface Filter : (HttpHandler) -> HttpHandler
```

### Construction

```kotlin
val timing = Filter { next ->
    { request ->
        val start = System.nanoTime()
        next(request).also { println("Took ${System.nanoTime() - start}ns") }
    }
}
```

### Chaining

```kotlin
// Filters apply left to right: filter1 runs first, then filter2, then handler
val app = filter1.then(filter2).then(handler)

// NoOp filter — does nothing, useful as a chain starter
Filter.NoOp.then(myFilter).then(handler)
```

### Gotchas

- **Initialization runs once**: The outer lambda in `Filter { next -> ... }` executes once when `.then()` is called. Only the inner lambda `{ request -> ... }` runs per request.
- **Chaining is immutable**: `.then()` returns a new function; it does not modify the filter.
- **Filter applies to routing misses too**: A filter applied with `filter.then(routes(...))` runs even when no route matches (the 404 response still passes through the filter).

### Built-in Server Filters

```kotlin
// CORS
ServerFilters.Cors(CorsPolicy(
    originPolicy = OriginPolicy.AllowAll(),
    headers = listOf("content-type"),
    methods = listOf(GET, POST),
    credentials = true
))

// Basic Auth
ServerFilters.BasicAuth("realm") { credentials -> credentials.user == "admin" }

// Bearer Auth
ServerFilters.BearerAuth("realm") { token -> token == "valid-token" }

// Request Context initialization (required for RequestKey lenses)
ServerFilters.InitialiseRequestContext(contexts)

// Catch all exceptions
ServerFilters.CatchAll { Response(INTERNAL_SERVER_ERROR) }

// GZip
ServerFilters.GZip()
```

### Built-in Client Filters

```kotlin
// Set base URI for relative requests
ClientFilters.SetBaseUriFrom(Uri.of("http://api.example.com"))

// Set host header
ClientFilters.SetHostFrom(Uri.of("http://backend:8080"))

// Follow redirects (handles both absolute and relative Location headers)
// HTTPS → HTTP downgrades are NOT followed — the redirect response is returned as-is
ClientFilters.FollowRedirects()

// Request tracing (Zipkin-style)
ServerFilters.RequestTracing()   // server-side
ClientFilters.RequestTracing()   // client-side propagation

// Report HTTP transactions to the Events system
ServerFilters.ReportHttpTransaction(events)  // emits HttpEvent.Incoming
ClientFilters.ReportHttpTransaction(events)  // emits HttpEvent.Outgoing
```

## Lens System

Type-safe, composable extraction/injection of values from HTTP messages.

### Query Lenses

```kotlin
Query.required("name")                          // String, throws on missing
Query.optional("name")                          // String?, null on missing
Query.int().required("page")                    // Int, throws on missing
Query.map(String::toInt).required("page")       // equivalent custom mapping
Query.multi.required("ids")                     // List<String?>, all values
Query.enum<Method>().required("method")         // Enum parsing
Query.enum<Method>(caseSensitive = false).required("m")  // Case-insensitive
Query.csv().required("tags")                    // Comma-separated list
Query.noValue().required("flag")                // Presence-only (?flag)
```

### Header Lenses

```kotlin
Header.required("X-Custom")
Header.optional("X-Custom")
Header.CONTENT_TYPE                             // Pre-built ContentType lens
Header.LOCATION                                 // Pre-built Uri lens
Header.ALLOW                                    // RFC 9110 §10.2.1 — multi-value enum of allowed HTTP methods
```

Header lenses strip surrounding quotes from values per RFC 2045 (e.g., `"value"` becomes `value`).

### Path Lenses

```kotlin
Path.of("id")                                   // String path param
Path.int().of("id")                             // Int path param
Path.uuid().of("id")                            // UUID path param
Path.map(::UserId) { it.value }.of("userId")    // Custom type
```

### FormField Lenses

```kotlin
FormField.required("username")
FormField.optional("nickname")
FormField.multi.required("tags")
```

### Body Lenses

Body lenses are typically provided by format modules (jackson, moshi, etc.) but can be built manually:

```kotlin
Body.string(ContentType.TEXT_PLAIN).toLens()
```

### Custom Type Mapping

```kotlin
// map(parse, serialize)
val userId = Query.map(::UserId) { it.value }.required("userId")

val id: UserId = userId(request)                // extract
val req = userId(UserId("abc"), request)        // inject
val req2 = request.with(userId of UserId("abc")) // inject with `of`
```

### Lens Failures

- `Missing` — required value not present
- `Invalid` — value present but transformation/parsing failed
- `Unsupported` — content type not supported

```kotlin
try {
    val name = Query.required("name")(request)
} catch (e: LensFailure) {
    // e.failures contains list of Missing/Invalid/Unsupported
    // e.overall() returns the worst failure type
}
```

### Validation with `with`

```kotlin
val strict = Body.auto<MyType>().toLens()
val request = Request(POST, "/").with(strict of MyType(...))
```

## Routing

### Basic Routes

```kotlin
val app = routes(
    "/users" bind GET to listUsers,
    "/users/{id}" bind GET to getUser,
    "/users" bind POST to createUser
)
```

### Path Parameters

```kotlin
"/users/{id}" bind GET to { request ->
    val id = request.path("id")!!   // extracted from URI template
    Response(OK).body("User $id")
}

// Regex capture (greedy)
"/files/{path:.*}" bind GET to { request ->
    val path = request.path("path")!!  // matches "a/b/c"
    servePath(path)
}
```

### Nested Routes

```kotlin
val app = routes(
    "/api" bind routes(
        "/v1" bind routes(
            "/users" bind GET to listUsers
        )
    )
)
// Matches: GET /api/v1/users
```

### Applying Filters to Routes

```kotlin
// Filter wraps the entire routing handler (including 404s)
val app = authFilter.then(routes(
    "/public" bind GET to publicHandler,
    "/private" bind GET to privateHandler
))

// Filter on specific sub-routes only
val app = routes(
    "/public" bind GET to publicHandler,
    "/private" bind authFilter.then(routes(
        "/" bind GET to privateHandler
    ))
)
```

### Router Predicates

```kotlin
// Method + header
GET.and(header("Accept", "text/event-stream"))

// Query-based routing
query("format", "json")
query("format") { it.startsWith("json") }
queries("required1", "required2")

// Header-based routing
header("X-Version", "2")
headers("Authorization")

// Body-based routing
body { bodyString: String -> bodyString.contains("xml") }

// Composite
GET.and(header("Accept", "application/json").and(query("v", "2")))
```

### Gotchas

- **First match wins**: Routes are tried in declaration order. Put specific routes before general ones.
- **Method mismatch = 405**: If the path matches but the method doesn't, the response is `METHOD_NOT_ALLOWED`, not `NOT_FOUND`.
- **`request.path()` requires routing**: Calling `path()` on a non-routed request throws `IllegalStateException`. Only use inside a routed handler.
- **Path matching is exact**: `/a` does NOT match `/a/b`. Use `"/a/{rest:.*}"` for prefix matching.
- **Trailing slashes matter**: `/users` and `/users/` are different routes.

## Forms

```kotlin
// Read form fields
val name = request.form("name")         // first value or null
val all = request.formAsMap()           // Map<String, List<String?>>

// Create form body
val form = listOf("name" to "Alice", "age" to "30")
Request(POST, "/submit").body(form.toBody())
```

## Events

```kotlin
typealias Events = (Event) -> Unit

// Built-in implementations
StdOutEvents
StdErrEvents
RecordingEvents()   // for testing

// Custom events
data class UserCreated(val userId: String) : Event

// Emit
val events: Events = StdOutEvents
events(UserCreated("123"))

// Combine
val combined = StdOutEvents.and(metricsEvents)

// Add metadata
events(UserCreated("123") + ("source" to "api") + ("region" to "us-east"))

// Filter pipeline
val filtered = EventFilter { next -> { event ->
    next(event + ("timestamp" to Clock.System.now()))
} }.then(events)
```

### RecordingEvents (Testing)

```kotlin
val events = RecordingEvents()
val app = myApp(events)
app(Request(GET, "/"))

val captured = events.toList()
assertThat(captured, hasSize(equalTo(1)))
```

## Server

```kotlin
// Built-in SunHttp (no extra dependencies)
val server = app.asServer(SunHttp(8080)).start()
server.port()   // actual bound port
server.stop()

// With Loom virtual threads
val server = app.asServer(SunHttpLoom(8080)).start()

// Graceful shutdown (server module dependent)
// SunHttp supports Immediate stop mode only
```

`Http4kServer` implements `AutoCloseable` — use with `.use {}` for automatic cleanup.

## Client

```kotlin
// Built-in URL connection client (no extra dependencies)
val client: HttpHandler = URLConnectionHttpClient()
val response = client(Request(GET, "http://example.com"))

// With timeouts
val client = URLConnectionHttpClient(
    readTimeout = Duration.ofSeconds(30),
    connectionTimeout = Duration.ofSeconds(10)
)
```

`Java8HttpClient` is a deprecated alias for `URLConnectionHttpClient`.

## Cookie Storage

`ClientFilters.Cookies` manages cookies across requests using a `CookieStorage` implementation:

```kotlin
// DefaultCookieStorage — RFC 6265 compliant (recommended)
// Scopes cookies by domain, path, and scheme — cookies sent only to matching origins
val client = ClientFilters.Cookies(storage = DefaultCookieStorage())
    .then(httpClient)

// InsecureCookieStorage — global jar, no origin scoping
// Use only for single-origin test scenarios
val client = ClientFilters.Cookies(storage = InsecureCookieStorage())
    .then(httpClient)
```

`ClientFilters.Cookies` defaults to `DefaultCookieStorage()`.

### Cookie Storage Gotchas

- **`BasicCookieStorage` is deprecated**: It has been renamed to `InsecureCookieStorage`. Replace usages to suppress deprecation warnings. Use `DefaultCookieStorage` for production — it prevents cross-origin cookie leakage.
- **`LocalCookie` requires `origin`**: `LocalCookie(cookie, created, origin)` — the `origin: Uri` field is required when constructing `LocalCookie` instances directly.
- **`CookieStorage.retrieve(uri)` takes a URI**: The `retrieve` method now takes the request `Uri` to enable origin-scoped filtering.
- **`DefaultCookieStorage` domain matching**: Cookies without a `Domain` attribute are host-only (exact host match). Cookies with `Domain` match that domain and all subdomains.

## Request Context

Attach typed values to a request within a filter pipeline:

```kotlin
val contexts = RequestContexts()
val userKey = RequestKey.required<User>(contexts)

val auth = Filter { next -> { request ->
    val user = authenticate(request)
    next(userKey(user, request))
} }

val handler: HttpHandler = { request ->
    val user = userKey(request)   // retrieve typed value
    Response(OK).body("Hello ${user.name}")
}

val app = ServerFilters.InitialiseRequestContext(contexts)
    .then(auth)
    .then(handler)
```

## Testing Patterns

http4k's function-based design makes testing straightforward:

```kotlin
// Test handler directly — no server needed
val app: HttpHandler = { Response(OK).body("hello") }
assertThat(app(Request(GET, "/")).bodyString(), equalTo("hello"))

// Test filters in isolation
val filter = Filter { next -> { next(it.header("X-Test", "true")) } }
val testHandler = filter.then { req -> Response(OK).body(req.header("X-Test")!!) }
assertThat(testHandler(Request(GET, "/")).bodyString(), equalTo("true"))

// Test routing
val app = routes("/greet/{name}" bind GET to { Response(OK).body("Hi ${it.path("name")}") })
assertThat(app(Request(GET, "/greet/Alice")).bodyString(), equalTo("Hi Alice"))
assertThat(app(Request(GET, "/missing")).status, equalTo(NOT_FOUND))
assertThat(app(Request(POST, "/greet/Alice")).status, equalTo(METHOD_NOT_ALLOWED))

// Capture events
val events = RecordingEvents()
val app = myApp(events)
app(Request(GET, "/"))
assertThat(events.toList(), hasSize(equalTo(1)))
```

### In-Memory HTTP Client

Use any `HttpHandler` as a fake HTTP client — no network needed:

```kotlin
val fakeBackend: HttpHandler = { request ->
    when (request.uri.path) {
        "/api/users" -> Response(OK).body("""[{"id": 1}]""")
        else -> Response(NOT_FOUND)
    }
}

// Inject fakeBackend wherever your code expects an HttpHandler/client
val myService = UserService(fakeBackend)
```
