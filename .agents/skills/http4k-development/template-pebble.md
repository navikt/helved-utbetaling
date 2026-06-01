---
license: Apache-2.0
module: http4k-template-pebble
---

# http4k-template-pebble Reference

Pebble template engine integration.

## Construction

```kotlin
val templates = PebbleTemplates()

// With custom PebbleEngine configuration
val templates = PebbleTemplates { builder ->
    builder.addExtension(myExtension)
}

val renderer = templates.CachingClasspath("org.example.views")
val renderer = templates.HotReload("src/main/resources/org/example/views")
```

## Template Syntax

```pebble
{# Template: org/example/views/MyView.peb #}
<h1>{{ model.name }}</h1>
<ul>
  {% for item in model.items %}
    <li>{{ item.name }} - {{ item.price }}</li>
  {% endfor %}
</ul>
```

## Gotchas

- **File extension**: Templates use `.peb` extension (appended automatically).
- **Access via `model.xxx`**: The ViewModel is bound as `model`.
- **Builder pattern**: Configuration via `PebbleEngine.Builder` function.
- **Cache control**: `Caching` enables the Pebble cache; `HotReload` disables it with `cacheActive(false)`.
