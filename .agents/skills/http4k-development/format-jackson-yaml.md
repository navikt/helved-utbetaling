---
license: Apache-2.0
module: http4k-format-jackson-yaml
---

# http4k-format-jackson-yaml Reference

YAML format module backed by Jackson's `jackson-dataformat-yaml`. Serializes/deserializes Kotlin types to/from YAML.

## Construction

```kotlin
// Default singleton (includes withStandardMappings())
val yaml = JacksonYaml

// Custom configuration
val custom = JacksonYaml.custom {
    text(BiDiMapping(::MyId, MyId::value))
}
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
val response = Response(OK).yaml(myObj)
val obj: MyType = request.yaml<MyType>()

// BiDiMapping for lists
val mapping = JacksonYaml.asBiDiMapping<List<MyType>>()
```

## Gotchas

- **No document start marker**: The `---` YAML document start marker is disabled by default (`WRITE_DOC_START_MARKER` off) for cleaner output.
- **Content type is `TEXT_YAML`**: Default content type is `text/yaml`.
- **No JSON node manipulation**: `JacksonYaml` extends `AutoMarshalling` directly (not `AutoMarshallingJson`) — there is no YAML node type. Use it for typed object conversion only.
- **Polymorphic types supported**: Sealed classes with `@JsonTypeInfo` work correctly for YAML serialization.
