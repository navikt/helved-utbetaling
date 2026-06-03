---
license: Apache-2.0
module: http4k-format-toon
---

# http4k-format-toon Reference

Format module for the Toon format. Wraps Moshi internally for object mapping and converts to/from Toon's text representation.

## Construction

```kotlin
// Default singleton (includes withStandardMappings())
val toon = Toon

// Custom configuration
val custom = ConfigurableToon(
    ToonBuilder(Moshi.Builder())
        .asConfigurable()
        .withStandardMappings()
        .text(BiDiMapping(::MyId, MyId::value))
        .done(),
    EncodeOptions.DEFAULT,
    DecodeOptions.DEFAULT
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

// Convenience extensions
val response = Response(OK).toon(myObj)
val obj: MyType = request.toon<MyType>()
```

## Gotchas

- **Content type is `text/toon`**: Default content type is a custom `text/toon` media type.
- **Two-step conversion**: Objects are converted via Moshi to JSON internally, then from JSON to Toon format. Moshi adapter configuration applies.
- **No JSON node manipulation**: Extends `AutoMarshalling` directly — no `Body.json()` or node API.
- **EncodeOptions/DecodeOptions**: Control Toon format-specific encoding and decoding behavior.
