---
license: Apache-2.0
module: http4k-connect-amazon-firehose
---

# http4k-connect-amazon-firehose Reference

Firehose client — connect actions for Amazon Kinesis Data Firehose.

## Client

```kotlin
val firehose = Firehose.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Delivery Stream Management

```kotlin
firehose.createDeliveryStream(
    deliveryStreamName = DeliveryStreamName.of("my-stream"),
    deliveryStreamType = DeliveryStreamType.DirectPut
).successValue()

firehose.listDeliveryStreams().successValue()
firehose.deleteDeliveryStream(DeliveryStreamName.of("my-stream")).successValue()
```

## Put Records

```kotlin
// Single record
firehose.putRecord(
    deliveryStreamName = DeliveryStreamName.of("my-stream"),
    record = Record(data = "event data\n".toByteArray())
).successValue()

// Batch records
firehose.putRecordBatch(
    deliveryStreamName = DeliveryStreamName.of("my-stream"),
    records = listOf(
        Record("event 1\n".toByteArray()),
        Record("event 2\n".toByteArray())
    )
).successValue()
```

## Gotchas

- Uses JSON protocol with `X-Amz-Target` header
- Record data is Base64-encoded in the API — the client handles encoding
- `putRecordBatch` accepts up to 500 records or 4MB per call
- Add newlines between records when delivering to S3 in text formats (Firehose doesn't add delimiters)
- `putRecordBatch` returns failed records — check `FailedPutCount` in the response
- Stream must be in `ACTIVE` state before putting records
