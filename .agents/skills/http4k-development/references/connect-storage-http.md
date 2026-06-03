---
license: Apache-2.0
module: http4k-connect-storage-http
---

# http4k-connect-storage-http Reference

HTTP-backed `Storage<T>` implementation — delegates all operations to a remote HTTP server.

## Usage

```kotlin
val storage = Storage.Http<MyData>(
    http = JavaHttpClient(),
    bodyLens = Body.auto<MyData>().toLens()  // optional, defaults to auto
)

storage["key"] = MyData("value")      // POST /api/storage/{key}
val item = storage["key"]              // GET  /api/storage/{key}
storage.remove("key")                  // DELETE /api/storage/{key}
storage.keySet("prefix/")             // GET  /api/storage?keyPrefix=prefix/
storage.removeAll("prefix/")          // DELETE /api/storage?keyPrefix=prefix/
```

## Server Endpoints (expected API)

| Method | Path | Query | Purpose |
|--------|------|-------|---------|
| GET | `/api/storage/{key}` | — | Fetch single value |
| POST | `/api/storage/{key}` | — | Store value |
| DELETE | `/api/storage/{key}` | — | Remove value (returns `202 Accepted`) |
| GET | `/api/storage` | `keyPrefix=...` | List keys |
| DELETE | `/api/storage` | `keyPrefix=...` | Remove all with prefix (returns `202 Accepted`) |

## As a Server (expose any Storage over HTTP)

```kotlin
fun storageRoutes(storage: Storage<MyData>, lens: BiDiBodyLens<MyData>) = routes(
    "/api/storage/{key}" bind GET to { req ->
        val key = Path.of("key")(req)
        storage[key]?.let { Response(OK).with(lens of it) } ?: Response(NOT_FOUND)
    },
    // ...
)
```

## Gotchas

- Uses `BiDiBodyLens` for marshalling — match lens type to what the server expects
- `remove` returns `true` only if server responds `202 ACCEPTED`
- `keySet` parses newline-separated response body
- Wrap with `SetBaseUriFrom` filter for configurable base URL
