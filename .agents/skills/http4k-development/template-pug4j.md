---
license: Apache-2.0
module: http4k-template-pug4j
---

# http4k-template-pug4j Reference

Pug4J (Jade) template engine integration.

## Construction

```kotlin
val templates = Pug4jTemplates()

// With custom PugConfiguration
val config = PugConfiguration().apply { prettyPrint = true }
val templates = Pug4jTemplates(config)

val renderer = templates.CachingClasspath("org.example.views")
val renderer = templates.HotReload("src/main/resources/org/example/views")
```

## Template Syntax

```pug
//- Template: org/example/views/MyView.pug
h1= model.name
ul
  for item in model.items
    li
      span= item.name
      |  -
      span= item.price
```

## Gotchas

- **File extension**: Templates use `.pug` extension.
- **Access via `model.xxx`**: The ViewModel is bound as `model`.
- **Indentation-based**: Pug uses indentation for nesting (no closing tags).
- **Pipe for text**: Use `|` prefix for plain text content.
