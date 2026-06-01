---
license: Apache-2.0
module: http4k-format-jackson
---

# http4k-format-jackson Reference

JSON format module backed by Jackson. The most feature-rich format module — supports auto-marshalling, JSON node manipulation, views, CloudEvents, and data4k integration.

## Construction

```kotlin
// Default singleton (includes withStandardMappings())
val json = Jackson

// Custom configuration
val custom = ConfigurableJackson(
    KotlinModule.Builder().build()
        .asConfigurable(ObjectMapper())
        .withStandardMappings()
        .text(BiDiMapping(::MyId, MyId::value))
        .done()
)

// Shorthand custom factory
val custom = Jackson.custom {
    text(BiDiMapping(::MyId, MyId::value))
}
```

## Lens Integration

```kotlin
// Typed body lens (most common)
val lens = Body.auto<MyType>().toLens()
val obj: MyType = lens(request)
val response = Response(OK).with(lens of myObj)

// Raw JSON node lens
val jsonLens = Body.json().toLens()
val node: JsonNode = jsonLens(request)

// WebSocket
val wsLens = WsMessage.auto<MyType>().toLens()

// Convenience extensions
val response = Response(OK).json(myObj)       // set body with Jackson
val obj: MyType = request.json<MyType>()      // extract with Jackson
```

## JSON Node Manipulation

Jackson uses `com.fasterxml.jackson.databind.JsonNode` as its node type.

```kotlin
// Parse and inspect
val node = Jackson.parse("""{"name": "http4k", "version": 4}""")
Jackson.typeOf(node)                // JsonType.Object
Jackson.fields(node)                // [("name", TextNode), ("version", IntNode)]
Jackson.textValueOf(node, "name")   // "http4k"

// Construct nodes
Jackson {
    obj(
        "name" to string("http4k"),
        "tags" to array(listOf(string("kotlin"), string("http"))),
        "stable" to boolean(true)
    )
}

// Format
node.asPrettyJsonString()
node.asCompactJsonString()
Jackson.prettify(jsonString)
Jackson.compactify(jsonString)
```

## Jackson Views

Serialize/deserialize using Jackson `@JsonView` annotations:

```kotlin
val viewLens = Body.autoView<MyType, PublicView>().toLens()
val obj = viewLens(request)                    // deserialize with view
val response = Response(OK).with(viewLens of myObj) // serialize with view
```

## Default Configuration

The `Jackson` singleton applies:
- `FAIL_ON_NULL_FOR_PRIMITIVES = true`
- `FAIL_ON_UNKNOWN_PROPERTIES = false`
- `FAIL_ON_IGNORED_PROPERTIES = false`
- `USE_BIG_DECIMAL_FOR_FLOATS = true`
- `USE_BIG_INTEGER_FOR_INTS = true`

## Gotchas

- **BigDecimal/BigInteger by default**: Jackson is configured with `USE_BIG_DECIMAL_FOR_FLOATS` and `USE_BIG_INTEGER_FOR_INTS` for precision. Numbers are not lossy.
- **Unknown properties ignored**: `FAIL_ON_UNKNOWN_PROPERTIES = false` by default. Use a strict marshaller if you need to reject unknown fields.
- **Null primitives fail**: `FAIL_ON_NULL_FOR_PRIMITIVES = true` — a JSON `null` for a non-nullable Kotlin primitive throws an error.
- **CloudEvents support**: Jackson includes `cloudevents-json-jackson` — use `CloudEventBuilder.withData(t)` for CloudEvents serialization.
