---
license: Apache-2.0
module: http4k-api-jsonschema
---

# http4k-api-jsonschema Reference

JSON Schema generation from Kotlin objects or raw JSON nodes. Used internally by the OpenAPI contract renderer but also usable standalone.

## AutoJsonToJsonSchema (From Kotlin Objects)

```kotlin
val creator = AutoJsonToJsonSchema(Jackson)

val schema = creator.toSchema(User(1, "John"))
// schema.node = the JSON Schema node
// schema.definitions = map of referenced type schemas
```

### Custom Configuration

```kotlin
val creator = AutoJsonToJsonSchema(
    json = Jackson,
    fieldRetrieval = FieldRetrieval.compose(
        JacksonJsonPropertyAnnotated,        // respect @JsonProperty renames
        SimpleLookup(metadataRetrievalStrategy =
            JacksonFieldMetadataRetrievalStrategy  // extract @JsonPropertyDescription
                .then(PrimitivesFieldMetadataRetrievalStrategy)  // add format for Int, Long, UUID, etc.
        )
    ),
    modelNamer = SchemaModelNamer.Full,       // use qualified class names
    refLocationPrefix = "components/schemas"  // OpenAPI 3.x location
)
```

## JsonToJsonSchema (From Raw JSON Nodes)

```kotlin
// V3 (OpenAPI 3.x — refs under components/schemas)
val creator = org.http4k.contract.jsonschema.v3.JsonToJsonSchema(Jackson)

// V2 (OpenAPI 2.x — refs under definitions)
val creator = org.http4k.contract.jsonschema.v2.JsonToJsonSchema(Jackson)

val schema = creator.toSchema(
    Jackson { obj("name" to string("John"), "age" to number(30)) },
    overrideDefinitionId = "User"
)
```

## SchemaModelNamer

Controls how definition names are generated:

```kotlin
SchemaModelNamer.Simple     // "User" (simple class name)
SchemaModelNamer.Full       // "com.example.User" (qualified name)
SchemaModelNamer.Canonical  // "com.example.User" (canonical name)
```

## FieldRetrieval

Extracts field values and metadata from objects:

```kotlin
// Default: simple Kotlin reflection
SimpleLookup(metadataRetrievalStrategy = PrimitivesFieldMetadataRetrievalStrategy)

// With Jackson annotation support
FieldRetrieval.compose(
    JacksonJsonPropertyAnnotated,   // handles @JsonProperty renames
    JacksonJsonNamingAnnotated(),   // handles @JsonNaming strategies
    SimpleLookup(metadataRetrievalStrategy = JacksonFieldMetadataRetrievalStrategy)
)
```

## FieldMetadataRetrievalStrategy

Adds schema metadata (format, description) per field:

```kotlin
// Adds format for primitives (int32, int64, double, float, date-time, date, uuid, uri)
PrimitivesFieldMetadataRetrievalStrategy

// Extracts @JsonPropertyDescription as schema description
JacksonFieldMetadataRetrievalStrategy

// Chain strategies
PrimitivesFieldMetadataRetrievalStrategy.then(JacksonFieldMetadataRetrievalStrategy)
```

## MetadataRetrieval (Class-Level)

Adds metadata at the root schema level:

```kotlin
val creator = AutoJsonToJsonSchema(
    json = Jackson,
    metadataRetrieval = MetadataRetrieval.compose(
        SimpleMetadataLookup(mapOf(
            typeOf<User>() to FieldMetadata(mapOf("description" to "A user object"))
        ))
    )
)
```

## Custom Field Metadata

```kotlin
val creator = AutoJsonToJsonSchema(Jackson, fieldRetrieval = { target, name ->
    Field(
        value = "example",
        isNullable = false,
        metadata = FieldMetadata(mapOf(
            "description" to "field description",
            "format" to "email",
            "example" to "user@example.com"
        ))
    )
})
```

## Gotchas

- **V2 vs V3 ref paths**: V2 uses `#/definitions/`, V3 uses `#/components/schemas/`. Choose based on your OpenAPI version.
- **AutoJsonToJsonSchema needs examples**: It inspects actual object instances to determine types. Pass representative example objects.
- **Jackson annotations**: `@JsonProperty`, `@JsonPropertyDescription`, and `@JsonNaming` are supported via dedicated `FieldRetrieval` strategies. Compose them with `FieldRetrieval.compose()`.
- **Nullable fields**: Detected via Kotlin reflection. Nullable properties get `"nullable": true` in the schema.
- **Enums, maps, arrays**: All handled automatically by `AutoJsonToJsonSchema` including nested types.
