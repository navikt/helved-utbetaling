---
license: Apache-2.0
module: http4k-connect-amazon-s3-fake
---

# http4k-connect-amazon-s3-fake Reference

In-memory fake S3 server for testing.

## Setup

```kotlin
val fakeS3 = FakeS3()

val s3 = fakeS3.s3Client()
val bucket = fakeS3.s3BucketClient(BucketName.of("test-bucket"), Region.of("us-east-1"))
```

## Custom Storage

```kotlin
val fakeS3 = FakeS3(
    buckets = Storage.InMemory(),
    bucketContent = Storage.InMemory()
)
```

## As a Running Server (WithRunningFake)

```kotlin
class MyS3Test : WithRunningFake({ FakeS3() }) {
    val s3 = S3.Http(CredentialsProvider.Environment(testEnv), http)
}
```

## Chaos Testing

```kotlin
fakeS3.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeS3.behave()
```

## Gotchas

- Supports both path-style and virtual-hosted-style bucket addressing
- Pre-creates no buckets — call `CreateBucket` before `PutObject`
- Extends `ChaoticHttpHandler`
