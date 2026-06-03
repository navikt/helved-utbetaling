---
license: Apache-2.0
module: http4k-format-kondor-json
---

# http4k-format-kondor-json Reference

JSON format module backed by Kondor-json. Requires explicit converter registration for all types — no reflection-based auto-discovery.

## Construction

```kotlin
// Create with converter registrations
val json = KondorJson {
    register(MyType::class to JMyType)    // register Kondor converter
    register(OtherType::class to JOtherType)
}

// With custom JSON styles
val json = KondorJson(
    compactJsonStyle = JsonStyle.compactWithNulls,
    prettyJsonStyle = JsonStyle.prettyWithNulls
) {
    register(MyType::class to JMyType)
}
```

## Lens Integration

```kotlin
// Typed body lens
val lens = Body.auto<MyType>().toLens()
val obj: MyType = lens(request)
val response = Response(OK).with(lens of myObj)

// Raw JSON node lens
val jsonLens = Body.json().toLens()
val node: JsonNode = jsonLens(request)

// WebSocket
val wsLens = WsMessage.auto<MyType>().toLens()
```

## JSON Node Manipulation

Uses Kondor's own `JsonNode` type hierarchy: `JsonNodeObject`, `JsonNodeArray`, `JsonNodeString`, `JsonNodeNumber`, `JsonNodeBoolean`, `JsonNodeNull`.

```kotlin
val node = json.parse("""{"name": "http4k"}""")
json.typeOf(node)                   // JsonType.Object
json.fields(node)                   // [("name", JsonNodeString)]
json.textValueOf(node, "name")      // "http4k"
```

## Custom Type Mapping with BiDiMapping

```kotlin
val json = KondorJson {
    // BiDiMapping adapters convert to Kondor converters
    text(BiDiMapping(::MyId, MyId::value))      // maps to JStringRepresentable
    int(BiDiMapping(::Age, Age::value))          // maps to JIntRepresentable
    boolean(BiDiMapping(::Flag, Flag::value))    // maps to JBooleanRepresentable
}
```

## Gotchas

- **Explicit registration required**: Unlike Jackson/Gson/Moshi, Kondor does not auto-discover types. Every type must have a registered `JsonConverter` or the marshaller will fail at runtime.
- **No reflection**: Kondor uses compile-time converters, not reflection. This makes it faster but requires more setup.
- **JsonStyle controls null handling**: `JsonStyle.compactWithNulls` includes nulls in output; other styles may omit them.
- **Unit type pre-registered**: The `Unit` type is always registered automatically.
