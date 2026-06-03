---
license: Apache-2.0
module: http4k-template-freemarker
---

# http4k-template-freemarker Reference

FreeMarker template engine integration.

## Construction

```kotlin
val templates = FreemarkerTemplates(Configuration(Configuration.getVersion()))

val renderer = templates.CachingClasspath("org.example.views")
val renderer = templates.HotReload("src/main/resources/org/example/views")
```

## Template Syntax

```freemarker
<#-- Template: org/example/views/MyView (no extension in template name) -->
<h1>${name}</h1>
<ul>
  <#list items as item>
    <li>${item.name} - ${item.price}</li>
  </#list>
</ul>
```

## Gotchas

- **No file extension in template name**: FreeMarker resolves templates without an appended extension.
- **Direct property access**: ViewModel properties accessed directly (e.g., `${name}`), not via `model.` prefix.
- **Configuration-first**: Takes a FreeMarker `Configuration` object directly.
- **HotReload**: Sets `templateUpdateDelayMilliseconds = 0` to disable caching.
