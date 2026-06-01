---
license: Apache-2.0
module: http4k-connect-amazon-dynamodb
---

# http4k-connect-amazon-dynamodb Reference

DynamoDB client — connect actions for Amazon DynamoDB.

## Client

```kotlin
val dynamo = DynamoDb.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient(),            // optional
    overrideEndpoint = null             // optional — use for LocalStack
)
```

## Table Operations

```kotlin
dynamo.createTable(
    TableName.of("users"),
    listOf(AttributeDefinition(AttributeName.of("id"), S)),
    listOf(KeySchema(AttributeName.of("id"), HASH)),
    BillingMode.PAY_PER_REQUEST
).successValue()

dynamo.describeTable(TableName.of("users")).successValue()
dynamo.listTables().successValue()
dynamo.deleteTable(TableName.of("users")).successValue()
```

## Item Operations

```kotlin
// Put item
dynamo.putItem(
    TableName.of("users"),
    item = mapOf(
        "id" to AttributeValue.Str("user-123"),
        "name" to AttributeValue.Str("Alice"),
        "age" to AttributeValue.Num("30")
    )
).successValue()

// Get item
dynamo.getItem(
    TableName.of("users"),
    key = mapOf("id" to AttributeValue.Str("user-123"))
).successValue()?.item

// Delete item
dynamo.deleteItem(TableName.of("users"), key).successValue()

// Update item
dynamo.updateItem(
    TableName.of("users"), key,
    updateExpression = "SET #n = :name",
    expressionAttributeNames = mapOf("#n" to "name"),
    expressionAttributeValues = mapOf(":name" to AttributeValue.Str("Bob"))
).successValue()
```

## Query & Scan

```kotlin
dynamo.query(
    TableName.of("users"),
    keyConditionExpression = "id = :id",
    expressionAttributeValues = mapOf(":id" to AttributeValue.Str("user-123"))
).successValue()

dynamo.scan(TableName.of("users")).successValue()
```

## Batch Operations

```kotlin
dynamo.batchGetItem(mapOf(
    TableName.of("users") to KeysAndAttributes(keys = listOf(mapOf("id" to AttributeValue.Str("user-1"))))
)).successValue()

dynamo.batchWriteItem(mapOf(
    TableName.of("users") to listOf(
        WriteRequest(PutRequest(item = mapOf("id" to AttributeValue.Str("user-2"), ...)))
    )
)).successValue()
```

## Transactions

```kotlin
dynamo.transactWriteItems(listOf(
    TransactWriteItem(Put(TableName.of("users"), item)),
    TransactWriteItem(Delete(TableName.of("orders"), key))
)).successValue()
```

## Gotchas

- Uses `application/x-amz-json-1.0` protocol with `X-Amz-Target` header
- Expression attribute names (`#name`) required for reserved words
- Expression attribute values (`:val`) required for all filter/condition values
- `AttributeValue` has typed constructors: `Str`, `Num`, `Bool`, `Null`, `List`, `Map`, `Bin`, etc.
- PAY_PER_REQUEST billing is simplest for development
