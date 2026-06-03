---
license: Apache-2.0
module: http4k-bridge-servlet
---

# http4k-bridge-servlet Reference

Bridge http4k handlers into javax.servlet containers.

## Servlet Adapter

```kotlin
// As a servlet
class MyServlet : HttpHandlerServlet(myApp)

// Or convert directly
val servlet = myApp.asServlet()

// Manual adapter usage
val adapter = Http4kServletAdapter(myApp)
adapter.handle(servletRequest, servletResponse)
```

## Conversion Functions

```kotlin
// Servlet request → http4k Request
val request: Request = servletRequest.asHttp4kRequest()

// http4k Response → Servlet response
response.transferTo(servletResponse)
```

## Gotchas

- **javax.servlet**: This bridge uses `javax.servlet` (Java EE 8 and earlier). For Jakarta EE 9+, use `http4k-bridge-jakarta`.
- **Streaming**: Request and response bodies are streamed — not buffered in memory.
- **RequestSource**: Extracts `remoteAddr`, `remotePort`, and `scheme` from the servlet request.
