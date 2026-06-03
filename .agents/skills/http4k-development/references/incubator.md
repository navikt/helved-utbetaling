---
license: Apache-2.0
module: http4k-incubator
---

# http4k-incubator Reference

Experimental features — typed HTTP messages, resource serving, and HTMX test support.

## Typed HTTP Messages

Type-safe request/response wrappers using property delegation:

```kotlin
data class MyRequest(val request: Request) : TypedRequest(request) {
    val id by required(Path.int().of("id"))
    val name by required(Query.string())
    val token by optional(Header.string())
    var body by body(Body.auto<MyData>())
}

data class MyResponse(val response: Response) : TypedResponse(response) {
    var body by body(Body.auto<MyData>())
}
```

Usage:

```kotlin
val req = MyRequest(Request(GET, "/items/42").query("name", "foo"))
println(req.id)    // 42
println(req.name)  // "foo"
println(req.token) // null (optional)
```

Mutation is tracked through a Java Proxy — reassign properties to build modified messages:

```kotlin
val resp = MyResponse(Response(OK))
resp.body = MyData("hello")
val modified: Response = resp.response
```

## Resource Serving (Experimental)

```kotlin
// Serve from classpath
val handler: HttpHandler = ResourceLoaders.Classpath("/public")

// Serve from directory
val handler: HttpHandler = ResourceLoaders.Directory(Path.of("./static"))

// With directory listings
val handler: HttpHandler = ResourceLoaders.ListingDirectory(Path.of("./static"))
```

Resources handle `If-Modified-Since` and `ETag` caching automatically.

## HTMX Web Driver (Testing)

```kotlin
val driver = Http4kWebDriver(myApp).withHtmx()
driver.get("/")
val elem = driver.findElement(By.id("content"))
```

## Gotchas

- `TypedHttpMessage` uses Java Proxy for mutable delegation
- All mutations go through the proxy — the underlying `request`/`response` field reflects changes
- Classpath resource loader defaults to `index.html` for directory paths
- These APIs may change between releases
