---
license: Apache-2.0
module: http4k-api-openapi
---

# http4k-api-openapi Reference

Contract-driven API definition with automatic OpenAPI specification generation. Routes declare their parameters, request/response bodies, and security — http4k validates inputs and generates the spec.

## Contract Builder

```kotlin
val api = "/api" bind contract {
    renderer = OpenApi3(ApiInfo("My API", "1.0.0", "Description"), Jackson)
    security = ApiKeySecurity(Query.required("api_key"), { it == "secret" })
    descriptionPath = "/docs"
    includeDescriptionRoute = true
    preFlightExtraction = PreFlightExtraction.All
    tags += Tag("users", "User management")

    routes += "/users" meta {
        summary = "List users"
        description = "Returns all users"
        operationId = "listUsers"
        tags += Tag("users")
        queries += Query.int().optional("page")
        returning(OK, Body.auto<List<User>>().toLens() to listOf(User(1, "John")))
    } bindContract GET to { { Response(OK) } }

    routes += "/users" / Path.int().of("userId") meta {
        summary = "Get user"
        operationId = "getUser"
        returning(OK, Body.auto<User>().toLens() to User(1, "John"))
        returning(NOT_FOUND)
    } bindContract GET to { userId -> { Response(OK) } }
}
```

## Route Definition

```kotlin
// No path parameters
"/endpoint" bindContract GET to { { Response(OK) } }

// Path parameters (up to 10)
"/users" / Path.int().of("userId") bindContract GET to { userId -> { Response(OK) } }
"/users" / Path.of("name") / "posts" / Path.int().of("postId")
    bindContract GET to { name, postId -> { Response(OK) } }

// With metadata
"/endpoint" meta { summary = "My endpoint" } bindContract POST to { { Response(OK) } }

// Invoke route directly for testing (outside contract context)
val route = "/test" bindContract GET to { { Response(OK) } }
val response = route(Request(GET, "/test"))
```

## RouteMeta DSL

```kotlin
"/endpoint" meta {
    summary = "Short summary"
    description = "Detailed description"
    operationId = "uniqueOperationId"
    tags += Tag("groupName", "description")
    markAsDeprecated()

    // Query, header, cookie parameters
    queries += Query.int().required("page")
    queries += Query.string().optional("filter")
    queries += Query.boolean().multi.required("flags")
    queries += Query.enum<Status>().optional("status")
    headers += Header.string().required("X-Request-Id")
    cookies += Cookies.required("session")

    // Request body
    receiving(Body.auto<CreateUser>().toLens() to CreateUser("Jane", "jane@example.com"))

    // Response examples
    returning(OK, Body.auto<User>().toLens() to User(1, "John"))
    returning(NOT_FOUND)
    returning(FORBIDDEN to Response(FORBIDDEN))

    // Content negotiation
    produces += APPLICATION_JSON
    produces += APPLICATION_XML
    consumes += APPLICATION_JSON

    // Form bodies
    receiving(Body.webForm(
        FormField.boolean().required("active"),
        FormField.int().multi.optional("ids")
    ).toLens())

    // Multipart
    receiving(Body.multipartForm(Strict,
        MultipartFormField.multi.required("name"),
        MultipartFormFile.required("file")
    ).toLens())

    // Per-route security override
    security = BasicAuthSecurity("realm", credentials)

    // Pre-flight extraction override
    preFlightExtraction = PreFlightExtraction.IgnoreBody

    // Callbacks (webhooks)
    callback("onEvent") {
        "/webhook" meta { receiving(Body.auto<Event>().toLens()) } bindCallback POST
    }
} bindContract POST to { { Response(OK) } }
```

## Renderers

```kotlin
// OpenAPI 3.0 (default)
val renderer = OpenApi3(
    apiInfo = ApiInfo("My API", "1.0.0", "Description"),
    json = Jackson
)

// OpenAPI 3.0 with servers
val renderer = OpenApi3(
    apiInfo = ApiInfo("My API", "1.0.0"),
    json = Jackson,
    servers = listOf(
        ApiServer(Uri.of("https://api.example.com"), "Production"),
        ApiServer(Uri.of("https://staging.example.com"), "Staging")
    )
)

// OpenAPI 3.1
val renderer = OpenApi3(
    apiInfo = ApiInfo("My API", "1.0.0"),
    json = Jackson,
    version = OpenApiVersion._3_1_0
)

// OpenAPI 2.0 (Swagger)
val renderer = OpenApi2(
    apiInfo = ApiInfo("My API", "1.0.0"),
    json = Jackson,
    baseUri = Uri.of("https://api.example.com")
)
```

## Security

```kotlin
// API Key (query, header, or cookie)
ApiKeySecurity(Query.required("api_key"), { it == "secret" })
ApiKeySecurity(Header.required("X-Api-Key"), { validateKey(it) })
ApiKeySecurity(Cookies.required("auth"), { validateCookie(it) })

// Basic Auth
BasicAuthSecurity("realm", Credentials("user", "pass"))
BasicAuthSecurity("realm", { creds -> creds.user == "admin" })

// Bearer Auth
BearerAuthSecurity("fixed-token")
BearerAuthSecurity({ token -> validateToken(token) })

// OAuth2 variants
AuthCodeOAuthSecurity(
    authorizationUrl = Uri.of("https://auth.example.com/authorize"),
    tokenUrl = Uri.of("https://auth.example.com/token"),
    scopes = listOf(OAuthScope("read", "Read access")),
    filter = ServerFilters.BearerAuth(validateToken)
)
ImplicitOAuthSecurity(Uri.of("https://auth.example.com/authorize"), scopes, filter)
ClientCredentialsOAuthSecurity(Uri.of("https://auth.example.com/token"), scopes, filter)
UserCredentialsOAuthSecurity(Uri.of("https://auth.example.com/token"), scopes, filter)

// OpenID Connect
OpenIdConnectSecurity(Uri.of("https://auth.example.com/.well-known/openid-configuration"), filter)

// Composite security
val both = security1.and(security2)   // all must pass
val either = security1.or(security2)  // any can pass
```

## PreFlightExtraction

Controls parameter validation before the handler is called:

```kotlin
// Validate all parameters including body (default)
preFlightExtraction = PreFlightExtraction.All

// Validate parameters but skip body (avoid double-read)
preFlightExtraction = PreFlightExtraction.IgnoreBody

// Skip all validation (handler manages its own errors)
preFlightExtraction = PreFlightExtraction.None
```

## Webhooks

```kotlin
"/api" bind contract {
    renderer = OpenApi3(ApiInfo("My API", "1.0.0"), Jackson)

    webhook("orderCreated") {
        "/notify" meta {
            receiving(Body.auto<OrderEvent>().toLens() to OrderEvent("created"))
        } bindWebhook POST
    }

    routes += // ...
}
```

## Gotchas

- **Spec served at descriptionPath**: Set `descriptionPath = "/docs"` and `includeDescriptionRoute = true` to serve the generated spec. Default path is the contract root.
- **Handler nesting**: Path-parameter routes return `{ params -> { request -> response } }` — the outer function receives extracted path params, the inner receives the request.
- **Security inheritance**: Contract-level security applies to all routes unless overridden per-route via `meta { security = ... }`.
- **Auto-schema from examples**: When using `Body.auto<T>().toLens()` with `OpenApi3`, the renderer generates JSON Schema from the example object via reflection.
- **PreFlightExtraction.All is default**: All declared parameters and body are validated before reaching the handler. Use `IgnoreBody` if you need to read the body stream yourself.
