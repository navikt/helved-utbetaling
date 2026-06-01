---
license: Apache-2.0
module: http4k-template-thymeleaf
---

# http4k-template-thymeleaf Reference

Thymeleaf template engine integration.

## Construction

```kotlin
val templates = ThymeleafTemplates()

// With custom TemplateEngine configuration
val templates = ThymeleafTemplates { engine ->
    engine.addDialect(myCustomDialect)
    engine
}

val renderer = templates.CachingClasspath("org.example.views")
val renderer = templates.HotReload("src/main/resources/org/example/views")
```

## Template Syntax

```html
<!-- Template: org/example/views/MyView.html -->
<h1 th:text="${model.name}">placeholder</h1>
<ul>
  <li th:each="item : ${model.items}">
    <span th:text="${item.name}">name</span> - <span th:text="${item.price}">0</span>
  </li>
</ul>
```

## Fragment Support

```kotlin
// ViewModel returns "TemplateName::fragmentName"
data class MyFragment(val text: String) : ViewModel {
    override fun template() = "MyView::content-fragment"
}
```

## Gotchas

- **File extension**: Templates use `.html` extension.
- **Access via `${model.xxx}`**: The ViewModel is bound as `model` in the Thymeleaf context.
- **HotReload disables cache**: Sets `templateCacheMaxSize = 0`.
- **Fragment syntax**: Use `template()::fragment` to render a specific fragment from a template.
