---
license: Apache-2.0
module: http4k-connect-amazon-dynamodb-fake
---

# http4k-connect-amazon-dynamodb-fake Reference

In-memory fake DynamoDB server for testing.

## Setup

```kotlin
val fakeDynamo = FakeDynamoDb()
val client = fakeDynamo.client()
// Or:
val client = DynamoDb.Http(Region.of("us-east-1"), CredentialsProvider.Environment(testEnv), fakeDynamo)
```

## Custom Table Storage

```kotlin
val fakeDynamo = FakeDynamoDb(
    tables = Storage.InMemory()
)
```

## Test Contracts

```kotlin
class FakeDynamoDbTest : DynamoDbContract {
    override val dynamo = FakeDynamoDb().client()
}
```

## Chaos Testing

```kotlin
fakeDynamo.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeDynamo.behave()
```

## Gotchas

- Supports complex operations including transactions, conditional writes, and batch operations
- Condition expressions and projections are evaluated in-memory
- Extends `ChaoticHttpHandler`
- S3 bucket sources for `ImportTable` require a separate FakeS3
- `PutItem` with a `ConditionExpression` evaluates the condition against an empty item when the target item does not yet exist. This means `attribute_not_exists(pk)` works correctly as a "put-only-if-new" guard.
