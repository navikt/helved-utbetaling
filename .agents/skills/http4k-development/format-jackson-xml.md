---
license: Apache-2.0
module: http4k-format-jackson-xml
---

# http4k-format-jackson-xml Reference

XML format module backed by Jackson's `jackson-dataformat-xml`. Provides auto-marshalling between XML and Kotlin types.

## Construction

```kotlin
// Default singleton (includes withStandardMappings())
val xml = JacksonXml

// Custom configuration
val custom = ConfigurableJacksonXml(
    KotlinModule.Builder().build()
        .asConfigurableXml()
        .withStandardMappings()
        .text(BiDiMapping(::MyId, MyId::value))
        .done()
)
```

## Lens Integration

```kotlin
// Typed body lens
val lens = Body.auto<MyType>().toLens()
val obj: MyType = lens(request)
val response = Response(OK).with(lens of myObj)

// Custom content type
val lens = JacksonXml.autoBody<MyType>(ContentType("application/soap+xml")).toLens()

// WebSocket
val wsLens = WsMessage.auto<MyType>().toLens()

// Convenience extensions
val response = Response(OK).xml(myObj)
val obj: MyType = request.xml<MyType>()
```

## XML Annotations

Use Jackson XML annotations to control serialization:

```kotlin
@JsonPropertyOrder("name", "age")     // control element order
data class Person(
    @JacksonXmlProperty(isAttribute = true)  // serialize as attribute
    val id: String,
    val name: String,
    val age: Int
)
```

## Gotchas

- **No wrapper elements by default**: `JacksonXmlModule.setDefaultUseWrapper(false)` is applied — list elements are not wrapped in an extra container element.
- **Content type is `APPLICATION_XML`**: The default content type is `application/xml`. Use `autoBody<T>(contentType)` for custom XML content types (e.g., SOAP).
- **Field ordering**: XML element order matters for some consumers. Use `@JsonPropertyOrder` to control output order.
- **Null lists**: Jackson XML has known issues with null list fields — prefer empty lists over nullable list properties.
