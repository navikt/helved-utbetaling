---
license: Apache-2.0
module: http4k-format-moshi
---

# http4k-format-moshi Reference

JSON format module backed by Square Moshi. Provides auto-marshalling and JSON node manipulation with `MoshiNode`.

## Construction

```kotlin
// Default singleton (includes withStandardMappings())
val json = Moshi

// Custom configuration
val custom = Moshi.custom {
    text(BiDiMapping(::MyId, MyId::value))
}

// With strictness mode
val strict = ConfigurableMoshi(
    Moshi.Builder()
        .asConfigurable()
        .withStandardMappings()
        .done(),
    strictness = StrictnessMode.FailOnUnknown
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
val node: MoshiNode = jsonLens(request)

// WebSocket
val wsLens = WsMessage.auto<MyType>().toLens()

// Convenience extensions
val response = Response(OK).json(myObj)
val obj: MyType = request.json<MyType>()
```

## JSON Node Manipulation

Moshi uses a custom `MoshiNode` sealed interface as its node type.

```kotlin
// MoshiNode types: MoshiObject, MoshiArray, MoshiString, MoshiInteger,
//                  MoshiLong, MoshiDecimal, MoshiBoolean, MoshiNull

// Parse and inspect
val node = Moshi.parse("""{"name": "http4k"}""")
Moshi.typeOf(node)                   // JsonType.Object
Moshi.fields(node)                   // [("name", MoshiString)]
Moshi.textValueOf(node, "name")      // "http4k"

// Construct nodes
Moshi {
    obj(
        "name" to string("http4k"),
        "tags" to array(listOf(string("kotlin"))),
        "count" to number(42)
    )
}
```

## Strictness Modes

```kotlin
StrictnessMode.Lenient          // ignore unknown fields (default)
StrictnessMode.FailOnUnknown    // reject unknown fields
```

## Custom Adapters

When writing a custom `JsonAdapter` that should self-register as a factory (no annotation processing), extend `TypedJsonAdapterFactory`:

```kotlin
object MyTypeAdapter : TypedJsonAdapterFactory<MyType>(MyType::class.java) {
    override fun toJson(writer: JsonWriter, value: MyType?) { ... }
    override fun fromJson(reader: JsonReader): MyType { ... }
}

// Register with standard .add() — no addTyped() needed
val moshi = Moshi.Builder()
    .add(MyTypeAdapter)
    .asConfigurable()
    .withStandardMappings()
    .done()
```

`TypedJsonAdapterFactory<T>` implements both `JsonAdapter<T>` and `JsonAdapter.Factory`, so it routes only to instances of `T`.

## Gotchas

- **MoshiNode, not JsonElement**: Moshi uses its own `MoshiNode` sealed interface for JSON nodes, not Moshi's internal types.
- **Smart number handling**: `MoshiDecimal` automatically converts to `Int` or `Long` when the value fits safely, preserving precision.
- **Adapter registration order matters**: Moshi checks adapters in registration order. Custom adapters registered via `asConfigurable()` are checked before built-in adapters.
- **StrictnessMode.Lenient by default**: Unknown JSON fields are silently ignored. Use `FailOnUnknown` for strict deserialization.
