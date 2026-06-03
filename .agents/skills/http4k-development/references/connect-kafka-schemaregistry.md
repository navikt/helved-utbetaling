---
license: Apache-2.0
module: http4k-connect-kafka-schemaregistry
---

# http4k-connect-kafka-schemaregistry Reference

SchemaRegistry client — connect actions for Confluent Schema Registry.

## Client

```kotlin
val schemaRegistry = SchemaRegistry.Http(
    credentials = Credentials("api-key", "api-secret"),
    baseUri = Uri.of("https://psrc-xxxxx.us-east-1.aws.confluent.cloud"),
    http = JavaHttpClient()   // optional
)
```

## Schema Operations

```kotlin
// Register a schema
val schemaId = schemaRegistry.registerSchema(
    subject = Subject.of("my-topic-value"),
    schema = """{"type": "record", "name": "MyEvent", "fields": [{"name": "id", "type": "string"}]}""",
    schemaType = SchemaType.AVRO
).successValue().id

// Get schema by ID
schemaRegistry.getSchema(schemaId = SchemaId.of(schemaId)).successValue()

// Get schema for subject
schemaRegistry.getLatestSchemaForSubject(subject = Subject.of("my-topic-value")).successValue()

// List subjects
schemaRegistry.listSubjects().successValue()
```

## Compatibility Check

```kotlin
schemaRegistry.checkCompatibility(
    subject = Subject.of("my-topic-value"),
    schema = """{"type": "record", ...}"""
).successValue()
```

## Gotchas

- Subject naming convention: `{topic-name}-key` and `{topic-name}-value`
- Schema IDs are global across all subjects — store them for serialization
- `BACKWARD` compatibility (default): new schema can read old data
- Schema evolution: adding optional fields is backward compatible; removing fields is not
- `Credentials` takes Schema Registry API key/secret (separate from Kafka API key)
