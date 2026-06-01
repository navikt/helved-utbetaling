---
license: Apache-2.0
module: http4k-format-klaxon
---

# http4k-format-klaxon Reference

JSON format module backed by Beust Klaxon. Provides auto-marshalling for Kotlin types with Klaxon's converter system.

## Construction

```kotlin
// Default singleton (includes withStandardMappings())
val json = Klaxon

// Custom configuration
object MyKlaxon : ConfigurableKlaxon(
    KKlaxon()
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

// WebSocket
val wsLens = WsMessage.auto<MyType>().toLens()
```

## Gotchas

- **No JSON node manipulation**: Klaxon extends `AutoMarshalling` directly, not `AutoMarshallingJson`. There is no `Body.json()` lens or node creation/inspection API.
- **Top-level lists not supported**: Klaxon cannot directly deserialize a JSON array at the top level — only JSON objects. Wrap lists in a container object.
- **Custom converters via Klaxon API**: Custom type mappings are registered as Klaxon `Converter` instances under the hood via `asConfigurable()`.
- **`KKlaxon` not `Klaxon`**: The underlying Klaxon library class is imported as `KKlaxon` (aliased) to avoid collision with the http4k `Klaxon` singleton.
