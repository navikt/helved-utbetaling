---
license: Apache-2.0
module: http4k-api-ui-swagger
---

# http4k-api-ui-swagger Reference

Serves Swagger UI from locally-bundled webjars. Returns a `RoutingHttpHandler` that serves the interactive API documentation UI.

## Basic Usage

```kotlin
val app = routes(
    "/api" bind contract {
        renderer = OpenApi3(ApiInfo("My API", "1.0.0"), Jackson)
        descriptionPath = "/docs"
        routes += // ...
    },
    "/swagger" bind swaggerUiWebjar {
        url = "/api/docs"
    }
)
```

## Configuration

```kotlin
swaggerUiWebjar {
    url = "/api/docs"                  // OpenAPI spec URL (required to set)
    pageTitle = "My API Docs"          // browser tab title

    // Display options
    displayOperationId = true          // show operation IDs
    displayRequestDuration = true      // show request timing
    requestSnippetsEnabled = true      // show code snippets
    tryItOutEnabled = true             // enable "Try it out" by default
    deepLinking = true                 // enable deep linking to operations

    // Auth options
    oauth2RedirectUrl = "/oauth2-redirect"
    withCredentials = true             // include credentials in requests
    persistAuthorization = true        // persist auth in browser storage

    // Advanced
    domId = "swagger-ui"               // HTML element ID
    queryConfigEnabled = true          // allow config via URL query params
    layout = "BaseLayout"              // UI layout plugin
    presets = listOf("SwaggerUIBundle.presets.apis")
}
```

## Gotchas

- **Root redirects**: Requesting the root path redirects to `index.html`.
- **Default URL**: Points to petstore if not set. Always set `url` to your spec endpoint.
- **Webjar version**: Serves Swagger UI from classpath — no CDN needed, works offline.
- **Null options omitted**: Boolean options set to `null` are omitted from the config (Swagger UI defaults apply).
