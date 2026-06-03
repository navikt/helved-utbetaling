---
license: Apache-2.0
module: http4k-connect-amazon-s3
---

# http4k-connect-amazon-s3 Reference

S3 client — connect actions for Amazon S3 object storage.

## Global S3 Client

```kotlin
val s3 = S3.Http(
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient(),            // optional
    payloadMode = Payload.Mode.Signed   // optional
)

// Bucket operations
s3.listBuckets().successValue()
s3.createBucket(BucketName.of("my-bucket"), Region.of("us-east-1")).successValue()
s3.headBucket(BucketName.of("my-bucket")).successValue()
s3.deleteBucket(BucketName.of("my-bucket")).successValue()
```

## Bucket-Specific Client

```kotlin
val bucket = S3Bucket.Http(
    bucketName = BucketName.of("my-bucket"),
    bucketRegion = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient(),
    forcePathStyle = false   // optional
)

// Convenience operators
bucket[BucketKey.of("file.txt")] = inputStream   // PutObject
val content: InputStream? = bucket[BucketKey.of("file.txt")] // GetObject

// Explicit actions
bucket.putObject(BucketKey.of("data.json"), body, contentType).successValue()
bucket.getObject(BucketKey.of("data.json")).successValue()
bucket.deleteObject(BucketKey.of("data.json")).successValue()
bucket.headObject(BucketKey.of("data.json")).successValue()
bucket.listObjectsV2().successValue()
bucket.copyObject(sourceBucket, sourceKey, destKey).successValue()
```

## Object Tagging

```kotlin
bucket.putObjectTagging(
    BucketKey.of("file.txt"),
    mapOf("env" to "prod", "owner" to "team")
).successValue()
```

## LocalStack / Custom Endpoints

```kotlin
S3Bucket.Http(
    bucketName, region, creds,
    overrideEndpoint = Uri.of("http://localhost:4566")
)
```

## Streaming Uploads

`PutObject` accepts an optional `length: Long?` parameter for content-length-aware streaming:

```kotlin
// Stream with known content length (enables streaming without buffering)
PutObject(
    key = BucketKey.of("large-file.bin"),
    content = inputStream,
    length = 50_000_000L   // optional — enables streaming mode
)

// Via bucket client
bucket.putObject(BucketKey.of("data.bin"), inputStream, length = fileSize).successValue()
```

Providing `length` sets the `content-length` header and enables true streaming (no in-memory buffering).

## Gotchas

- Use `S3Bucket` (not `S3`) for most object operations — it pre-configures the bucket/region
- **Virtual-hosted-style** is default (`bucket.s3.amazonaws.com`) — use `forcePathStyle = true` for LocalStack
- Bucket names with dots require path-style addressing
- `getObject` returns `null` (wrapped in `Success(null)`) for 404 — use `NullableAutoMarshalledAction`
