---
name: ktor-routing
description: Build Ktor routes, StatusPages handlers, and authentication blocks following helved-utbetaling conventions. Triggers on "Ktor route", "REST endpoint", "API handler", "StatusPages", "/ktor-routing".
license: MIT
compatibility: opencode
metadata:
  audience: ai-assistant
  language: kotlin
  framework: ktor
  domain: nav-payment-system
---

# Ktor Routing

> **Parent context:** See root `AGENTS.md` (lines 147-167) for error handling and routing rules. This skill provides **concrete code examples** that complement those rules. The codebase uses **Ktor only** -- no annotation-based web frameworks.

## When to use me

- Trigger: building a new REST endpoint or `fun Route.X()` extension
- Trigger: wiring a new app's `Application.<appName>()` module
- Trigger: handling errors via `StatusPages` and `ApiError`
- Trigger: adding `authenticate("name") { ... }` JWT blocks
- Trigger: throwing `badRequest()`, `notFound()`, `conflict()`, etc. from a route handler

## Route as extension function

Routes are defined as extension functions on `Route`. Dependencies are passed as constructor-style parameters. No annotations, no DI framework.

```kotlin
// Source: apps/utsjekk/main/utsjekk/routes/IverksettingRoute.kt:16-30
fun Route.iverksetting(
    service: IverksettingService,
    migrator: IverksettingMigrator,
) {
    route("/api/iverksetting") {

        post("/v2/migrate") {
            val fagsystem = call.fagsystem()
            val request = call.receive<MigrationRequest>()
            if(request.meldeperiode == null && request.uidToStønad == null) badRequest("mangler en av: 'meldeperiode' eller 'uidToStønad'")
            if (request.meldeperiode != null && request.uidToStønad != null) badRequest("mutual exclusive: 'meldeperiode' and 'uidToStønad'")
            val utbetalinger = migrator.mapUtbetalinger(fagsystem, request)
            utbetalinger.forEach { migrator.migrate(request, it) }
            call.respond(HttpStatusCode.OK)
        }
    }
}
```

Path parameters use `call.parameters.getOrFail<String>("name")` and are wrapped into value classes via `.let(::SakId)`:

```kotlin
// Source: apps/utsjekk/main/utsjekk/routes/IverksettingRoute.kt:65-73
get("/{sakId}/{behandlingId}/status") {
    val sakId = call.parameters.getOrFail<String>("sakId").let(::SakId)
    val behandlingId = call.parameters.getOrFail<String>("behandlingId").let(::BehandlingId)
    val fagsystem = call.fagsystem()
    val status = service.utledStatus(fagsystem, sakId, behandlingId, null)
        ?: notFound("Fant ikke status utbetaling med sakId $sakId og behandlingId $behandlingId")

    call.respond(HttpStatusCode.OK, status)
}
```

## Application wiring

The app's module function (`fun Application.<appName>()`) takes dependencies as parameters with sensible defaults, manually wires services, and installs Ktor plugins. The `routing { ... }` block at the bottom registers all `Route` extensions.

```kotlin
// Source: apps/utsjekk/main/utsjekk/Utsjekk.kt:73-78
fun Application.utsjekk(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
    jdbcCtx: CoroutineDatasource = Jdbc.initialize(config.jdbc).context(),
    topology: Topology = createTopology(jdbcCtx),
) {
```

```kotlin
// Source: apps/utsjekk/main/utsjekk/Utsjekk.kt:163-174
routing {
    authenticate(TokenProvider.AZURE) {
        iverksetting(iverksettingService, iverksettingMigrator)
        utbetalinger(utbetalingService, utbetalingMigrator)
        simuleringRoutes.aap(this)
        simuleringRoutes.utsjekk(this)
        simuleringRoutes.abetal(this)
        simuleringRoutes.dryrun(this)
    }

    actuator(kafka, metrics)
}
```

`main()` calls `embeddedServer(...).start(wait = true)` referencing the module function:

```kotlin
// Source: apps/utsjekk/main/utsjekk/Utsjekk.kt:60-71
embeddedServer(
    factory = Netty,
    configure = {
        shutdownGracePeriod = 5000L
        shutdownTimeout = 50_000L
        connectors.add(EngineConnectorBuilder().apply {
            port = 8080
        })
    },
    module = Application::utsjekk,
).start(wait = true)
```

## StatusPages + ApiError

`StatusPages` is installed once per app and centralizes exception → HTTP mapping. `ApiError` becomes the response body verbatim; everything else maps to 400 (parse error) or 500.

```kotlin
// Source: apps/utsjekk/main/utsjekk/Utsjekk.kt:97-116
install(StatusPages) {
    exception<Throwable> { call, cause ->
        when (cause) {
            is ApiError -> call.respond(HttpStatusCode.fromValue(cause.statusCode), cause)
            is BadRequestException -> {
                val msg = "Klarte ikke lese json meldingen. Sjekk at formatet på meldingen din er korrekt, f.eks navn på felter, påkrevde felter, e.l."
                appLog.debug(msg)
                secureLog.debug(msg, cause)
                val res = ApiError(statusCode = 400, msg = msg)
                call.respond(HttpStatusCode.BadRequest, res)
            }
            else -> {
                val msg = "Ukjent feil, helved er varslet."
                appLog.error(msg, cause)
                val res = ApiError(statusCode = 500, msg = msg)
                call.respond(HttpStatusCode.InternalServerError, res)
            }
        }
    }
}
```

`ApiError` is a `RuntimeException` data class with `statusCode`, `msg`, `doc`, and `system`. Default `doc` points to `DocumentedErrors.BASE`; default `system` is `System.HELVED`.

```kotlin
// Source: models/main/models/ApiError.kt:105-110
data class ApiError(
    val statusCode: Int,
    val msg: String,
    val doc: String? = DocumentedErrors.BASE,
    val system: System? = System.HELVED,
) : RuntimeException(msg) {
```

## Helper functions

All helpers return `Nothing` so they can be used inside expressions (Elvis, if-else) without breaking type inference. They throw `ApiError` with the appropriate HTTP status code; `StatusPages` does the rest.

```kotlin
// Source: models/main/models/ApiError.kt:124-145
fun badRequest(msg: String, doc: String? = null): Nothing = throw ApiError(400, msg, doc ?: DocumentedErrors.BASE)
fun badRequest(error: DocumentedErrors.Async): Nothing = throw ApiError(400, error.msg, error.doc)

fun forbidden(msg: String, doc: String? = null): Nothing = throw ApiError(403, msg, doc ?: DocumentedErrors.BASE)

fun notFound(msg: String, doc: String? = null): Nothing = throw ApiError(404, msg, doc ?: DocumentedErrors.BASE)
fun notFound(error: DocumentedErrors.Async): Nothing = throw ApiError(404, error.msg, error.doc)

fun conflict(msg: String, doc: String? = null): Nothing = throw ApiError(409, msg, doc ?: DocumentedErrors.BASE)
fun conflict(error: DocumentedErrors.Async): Nothing = throw ApiError(409, error.msg, error.doc)

fun unprocessable(msg: String, doc: String? = null): Nothing = throw ApiError(422, msg, doc ?: DocumentedErrors.BASE)
fun locked(msg: String, doc: String? = null): Nothing = throw ApiError(423, msg, doc ?: DocumentedErrors.BASE)
fun internalServerError(msg: String, doc: String? = null): Nothing =
    throw ApiError(500, msg, doc ?: DocumentedErrors.BASE)

fun notImplemented(msg: String, doc: String? = null): Nothing = throw ApiError(501, msg, doc ?: DocumentedErrors.BASE)
fun badGateway(msg: String, doc: String? = null): Nothing = throw ApiError(502, msg, doc ?: DocumentedErrors.BASE)

fun unavailable(msg: String, doc: String? = null): Nothing = throw ApiError(503, msg, doc ?: DocumentedErrors.BASE)

fun unauthorized(msg: String): Nothing = throw ApiError(
```

Use them inline with Elvis on nullable lookups, in validation guards, or in `when` branches:

```kotlin
// Source: apps/utsjekk/main/utsjekk/routes/IverksettingRoute.kt:69-70
val status = service.utledStatus(fagsystem, sakId, behandlingId, null)
    ?: notFound("Fant ikke status utbetaling med sakId $sakId og behandlingId $behandlingId")
```

For domain errors with documentation links, prefer the `DocumentedErrors.Async` overload over a free-text message.

## Authentication

JWT auth providers are installed in `Authentication`, then routes are wrapped in `authenticate("provider-name") { ... }`. Unauthenticated routes (e.g. actuator) sit outside the block.

```kotlin
// Source: apps/utsjekk/main/utsjekk/Utsjekk.kt:92-96
install(Authentication) {
    jwt(TokenProvider.AZURE) {
        configure(config.azure)
    }
}
```

```kotlin
// Source: apps/utsjekk/main/utsjekk/Utsjekk.kt:163-174
routing {
    authenticate(TokenProvider.AZURE) {
        iverksetting(iverksettingService, iverksettingMigrator)
        utbetalinger(utbetalingService, utbetalingMigrator)
        simuleringRoutes.aap(this)
        simuleringRoutes.utsjekk(this)
        simuleringRoutes.abetal(this)
        simuleringRoutes.dryrun(this)
    }

    actuator(kafka, metrics)
}
```

JWT claims are read off the `JWTPrincipal` inside handlers. Throw `forbidden()` / `unauthorized()` when claims are missing:

```kotlin
// Source: apps/utsjekk/main/utsjekk/Utsjekk.kt:184-190
fun ApplicationCall.client(): Client =
    principal<JWTPrincipal>()
        ?.getClaim("azp_name", String::class)
        ?.split(":")
        ?.last()
        ?.let(::Client)
        ?: forbidden("missing JWT claim")
```

## Cross-references

- For Ktor library API (`HttpClientFactory`, `CallLog`, `TokenValidator`, `KtorRuntime`), load the **`libs-reference`** skill.
- For Ktor test setup (`KtorRuntime`, `runTest`, JWT generation), load the **`testing`** skill.
- Root `AGENTS.md` lines 147-167 define error handling and DI rules — do not duplicate.
- For Norwegian domain terms (Iverksetting, Utbetaling, Sak, Fagsystem), see `apps/AGENTS.md`.
