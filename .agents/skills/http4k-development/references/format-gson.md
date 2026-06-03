---
license: Apache-2.0
module: http4k-format-gson
---

# http4k-format-gson Reference

JSON format module backed by Google Gson. Provides auto-marshalling and JSON node manipulation with `JsonElement`.

## Construction

```kotlin
// Default singleton (includes withStandardMappings(), serializeNulls enabled)
val json = Gson

// Custom configuration via extending ConfigurableGson
object MyGson : ConfigurableGson(
    GsonBuilder()
        .serializeNulls()
        .asConfigurable()
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

// Raw JSON node lens
val jsonLens = Body.json().toLens()
val node: JsonElement = jsonLens(request)

// WebSocket
val wsLens = WsMessage.auto<MyType>().toLens()

// Convenience extensions
val response = Response(OK).json(myObj)
val obj: MyType = request.json<MyType>()
```

## JSON Node Manipulation

Gson uses `com.google.gson.JsonElement` as its node type.

```kotlin
// Parse and inspect
val node = Gson.parse("""{"name": "http4k"}""")
Gson.typeOf(node)                   // JsonType.Object
Gson.fields(node)                   // [("name", JsonPrimitive)]
Gson.textValueOf(node, "name")      // "http4k"

// Construct nodes
Gson {
    obj(
        "name" to string("http4k"),
        "tags" to array(listOf(string("kotlin"))),
        "active" to boolean(true)
    )
}

// Format
node.asPrettyJsonString()
node.asCompactJsonString()
```

## Gotchas

- **Nulls serialized by default**: The `Gson` singleton uses `serializeNulls()` — nullable fields appear as `null` in JSON output rather than being omitted.
- **Configuration via GsonBuilder**: Custom instances are created by extending `ConfigurableGson` with a configured `GsonBuilder`, not by passing an `ObjectMapper`.
- **Same lens API as Jackson**: `Body.auto<T>()`, `Body.json()`, and `WsMessage.auto<T>()` work identically across all JSON format modules.
