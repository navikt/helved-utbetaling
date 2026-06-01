---
license: Apache-2.0
module: http4k-template-htmlflow
---

# http4k-template-htmlflow Reference

HtmlFlow type-safe Kotlin DSL templates. Templates are defined in code, not files.

## Construction

```kotlin
val templates = HtmlFlowTemplates()

val renderer = templates.CachingClasspath("org.example.views")
val renderer = templates.HotReload("src/main/kotlin/org/example/views")

// Hot reload from classpath
val renderer = templates.HotReloadClasspath("org.example.views")
```

## Template Definition (Kotlin Code)

```kotlin
// File: org/example/views/MyView.kt
val myView: HtmlView<MyViewModel> = HtmlFlow.view { view ->
    view.html {
        body {
            dyn { model: MyViewModel ->
                h1 { text(model.name) }
                ul {
                    model.items.forEach { item ->
                        li {
                            text("${item.name} - ${item.price}")
                        }
                    }
                }
            }
        }
    }
}
```

## Direct Renderer from HtmlView

```kotlin
val renderer: TemplateRenderer = myView.renderer()
val html = renderer(MyViewModel("hello", items))
```

## Gotchas

- **No template files**: Templates are Kotlin code using HtmlFlow's DSL. No separate template files.
- **`dyn` block for dynamic content**: Wrap model-dependent content in `dyn { model -> ... }`.
- **Thread-safe by default**: Templates are configured with `threadSafe(true)`.
- **Pre-encoding**: Enabled for cached templates, disabled for hot reload.
- **Package-based resolution**: Template classes are located by converting the ViewModel's package path.
