---
license: Apache-2.0
module: http4k-format-kotlinx-serialization
---

# http4k-format-kotlinx-serialization Reference

JSON format module backed by Kotlin's official `kotlinx.serialization` library. Provides auto-marshalling and JSON node manipulation with `JsonElement`.

## Construction

```kotlin
// Default singleton (includes withStandardMappings())
val json = KotlinxSerialization

// Custom configuration
val custom = ConfigurableKotlinxSerialization({
    ignoreUnknownKeys = true
    prettyPrint = false
    // any kotlinx.serialization JsonBuilder configuration
})
```

## Lens Integration

```kotlin
// Typed body lens
val lens = Body.auto<MyType>().toLens()
val obj: MyType = lens(request)
val response = Response(OK).with(lens of myObj)

// Raw JSON node lens
val jsonLens = Body.json().toLens()
val node: JsonElement = jsonLens(request)

// WebSocket
val wsLens = WsMessage.auto<MyType>().toLens()
```

## JSON Node Manipulation

Uses `kotlinx.serialization.json.JsonElement` as its node type.

```kotlin
// Parse and inspect
val node = KotlinxSerialization.parse("""{"name": "http4k"}""")
KotlinxSerialization.typeOf(node)         // JsonType.Object
KotlinxSerialization.fields(node)         // [("name", JsonPrimitive)]
KotlinxSerialization.textValueOf(node, "name") // "http4k"

// Construct and format
KotlinxSerialization {
    obj("name" to string("http4k"), "active" to boolean(true))
}
```

## Gotchas

- **`ignoreUnknownKeys = true` by default**: Unknown JSON fields are silently ignored in the default singleton.
- **BigInteger not supported**: `bigInteger()` mappings throw `UnsupportedOperationException`. Use `long()` or `text()` for large integers.
- **BigDecimal as string**: BigDecimal values are serialized as string content in `JsonPrimitive`, which may cause precision representation differences compared to Jackson/Gson.
- **`@Serializable` annotation required**: Unlike reflection-based libraries (Jackson, Gson), kotlinx.serialization requires `@Serializable` on data classes for auto-marshalling.
