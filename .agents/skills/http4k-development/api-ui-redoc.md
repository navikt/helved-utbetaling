---
license: Apache-2.0
module: http4k-api-ui-redoc
---

# http4k-api-ui-redoc Reference

Serves Redoc from locally-bundled webjars. Returns a `RoutingHttpHandler` that serves the API documentation UI.

## Basic Usage

```kotlin
val app = routes(
    "/api" bind contract {
        renderer = OpenApi3(ApiInfo("My API", "1.0.0"), Jackson)
        descriptionPath = "/docs"
        routes += // ...
    },
    "/redoc" bind redocWebjar {
        url = "/api/docs"
    }
)
```

## Configuration

```kotlin
redocWebjar {
    url = "/api/docs"            // OpenAPI spec URL (required to set)
    pageTitle = "API Reference"  // browser tab title

    // Custom Redoc options (rendered as HTML attributes on <redoc> element)
    options["hide-download-button"] = ""
    options["expand-responses"] = "200,201"
    options["path-in-middle-panel"] = ""
}
```

## Gotchas

- **Root redirects**: Requesting the root path redirects to `index.html`.
- **Default URL**: Points to petstore if not set. Always set `url` to your spec endpoint.
- **Webjar version**: Serves Redoc from classpath — no CDN needed, works offline.
- **Options as HTML attributes**: The `options` map entries become attributes on the `<redoc>` HTML element, following Redoc's attribute-based configuration.
- **Simpler than Swagger UI**: Redoc has fewer config options — just `url`, `pageTitle`, and free-form `options`.
