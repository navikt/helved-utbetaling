---
license: Apache-2.0
module: http4k-template-handlebars
---

# http4k-template-handlebars Reference

Handlebars template engine integration.

## Construction

```kotlin
val templates = HandlebarsTemplates()

// With custom Handlebars configuration
val templates = HandlebarsTemplates { handlebars ->
    handlebars.registerHelper("uppercase") { context: String, _ -> context.uppercase() }
    handlebars
}

// With custom template suffix (default is .hbs)
val templates = HandlebarsTemplates(templateSuffix = ".html")

// With both configuration and suffix
val templates = HandlebarsTemplates(
    configure = { it.registerHelper("upper") { ctx: String, _ -> ctx.uppercase() }; it },
    templateSuffix = ".mustache"
)

val renderer = templates.CachingClasspath("org.example.views")
val renderer = templates.HotReload("src/main/resources/org/example/views")
```

## Template Syntax

```handlebars
{{!-- Template: org/example/views/MyView.hbs --}}
<h1>{{name}}</h1>
<ul>
  {{#each items}}
    <li>{{name}} - {{price}}</li>
  {{/each}}
</ul>
```

## Multiple Template Directories (HotReload)

```kotlin
val renderer = templates.HotReload("src/main/resources/views", "src/main/resources/shared")
```

## Gotchas

- **File extension**: Templates use `.hbs` extension by default. Override with `templateSuffix` constructor parameter.
- **Direct property access**: ViewModel properties are accessed directly (e.g., `{{name}}`).
- **Composite loaders**: `HotReload` with multiple directories tries each in order (first match wins).
