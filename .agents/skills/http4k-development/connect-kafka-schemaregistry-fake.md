---
license: Apache-2.0
module: http4k-connect-kafka-schemaregistry-fake
---

# http4k-connect-kafka-schemaregistry-fake Reference

In-memory fake Schema Registry server for testing.

## Setup

```kotlin
val fakeRegistry = FakeSchemaRegistry()
val client = fakeRegistry.client()
```

## Custom Configuration

```kotlin
val fakeRegistry = FakeSchemaRegistry(
    schemas = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val registry = FakeSchemaRegistry().client()

val id = registry.registerSchema(
    subject = Subject.of("my-topic-value"),
    schema = """{"type": "string"}""",
    schemaType = SchemaType.AVRO
).successValue().id

val schema = registry.getSchema(SchemaId.of(id)).successValue()
```

## Chaos Testing

```kotlin
fakeRegistry.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeRegistry.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Schema compatibility is not enforced in the fake — all schemas are accepted
- Schema IDs are auto-incrementing integers in the fake
