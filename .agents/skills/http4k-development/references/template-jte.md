---
license: Apache-2.0
module: http4k-template-jte
---

# http4k-template-jte Reference

JTE (Java Template Engine) integration with type-safe Kotlin templates.

## Construction

```kotlin
val templates = JTETemplates()

// With custom content type
val templates = JTETemplates(ContentType.Plain)  // default is Html

val renderer = templates.CachingClasspath("org.example.views")
val renderer = templates.HotReload("src/main/resources/org/example/views")
```

## Template Syntax

```kte
@import org.example.views.MyView
@param model: MyView
<h1>${model.name}</h1>
<ul>
  @for(item in model.items)
    <li>${item.name} - ${item.price}</li>
  @endfor
</ul>
```

## Gotchas

- **File extension**: Templates use `.kte` extension (appended automatically) for Kotlin, `.jte` for Java.
- **Type-safe**: Templates declare `@param` with types — compile-time checked.
- **ViewModel passed directly**: The ViewModel object is the template parameter, not wrapped in a `model` variable.
- **HotReload**: Creates a new `TemplateEngine` per render for true hot reload.
