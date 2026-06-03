---
license: Apache-2.0
module: http4k-format-moshi-yaml
---

# http4k-format-moshi-yaml Reference

YAML format module backed by Moshi (for object mapping) and SnakeYAML (for YAML parsing/formatting). Two-step conversion: YAML ↔ JSON ↔ typed objects.

## Construction

```kotlin
// Default singleton (includes withStandardMappings())
val yaml = MoshiYaml

// Custom configuration
val custom = MoshiYaml.custom {
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
```

## YAML Configuration

The default configuration uses:
- **Block flow style**: YAML output uses block style (not inline/flow)
- **Indent 2**: Two-space indentation
- **Plain scalar style**: Unquoted values where possible
- **MinimalResolver**: Only recognizes `true`/`True`/`TRUE`/`false`/`False`/`FALSE` as booleans — prevents `on`/`off`/`yes`/`no` from being interpreted as booleans

## Gotchas

- **Content type is `APPLICATION_YAML`**: Default content type is `application/yaml`.
- **No JSON node manipulation**: `MoshiYaml` extends `AutoMarshalling` directly — use it for typed object conversion only.
- **MinimalResolver prevents YAML boolean gotchas**: Unlike standard SnakeYAML, `on`/`off`/`yes`/`no` are treated as strings, not booleans. Only explicit `true`/`false` (case-insensitive) are boolean.
- **Null preservation**: Uses a `NullSafeMapAdapter` that serializes null map values (unlike the standard Moshi map adapter which omits them).
