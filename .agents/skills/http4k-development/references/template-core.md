---
license: Apache-2.0
module: http4k-template-core
---

# http4k-template-core Reference

Core template abstraction. All template engines implement `Templates` to provide `CachingClasspath`, `Caching`, and `HotReload` renderers.

## ViewModel

```kotlin
interface ViewModel {
    fun template(): String  // defaults to class FQCN with / separators
}

// Template path derived from package: org.example.MyView → org/example/MyView
data class MyView(val name: String, val items: List<Item>) : ViewModel
```

## Templates Interface

```kotlin
interface Templates {
    fun CachingClasspath(baseClasspathPackage: String = ""): TemplateRenderer
    fun Caching(baseTemplateDir: String = "./"): TemplateRenderer
    fun HotReload(baseTemplateDir: String = "./"): TemplateRenderer
}

typealias TemplateRenderer = (ViewModel) -> String
```

## Rendering to Response

```kotlin
val renderer = templates.CachingClasspath()

// Manual
val html = renderer(MyView("hello", items))
val response = Response(OK).body(html).with(CONTENT_TYPE of TEXT_HTML)

// Via lens
val viewLens = Body.viewModel(renderer, TEXT_HTML).toLens()
val response = Response(OK).with(viewLens of MyView("hello", items))

// Convenience
val response = renderer.renderToResponse(MyView("hello", items))
```

## Composing Renderers

```kotlin
// Fall back to second renderer if first throws ViewNotFound
val combined = primaryRenderer.then(fallbackRenderer)
```

## Gotchas

- **Template path from class name**: `ViewModel.template()` returns the class FQCN with `/` separators. Override to customize.
- **CachingClasspath vs HotReload**: `CachingClasspath` caches compiled templates (production). `HotReload` recompiles on every render (development).
- **ViewNotFound**: Thrown when the template file for a ViewModel cannot be found. Accepts an optional `cause` parameter to preserve the underlying exception (e.g., template compilation error). Compose renderers with `.then()` for fallbacks.
