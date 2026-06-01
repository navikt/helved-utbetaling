---
license: Apache-2.0
module: http4k-connect-storage-s3
---

# http4k-connect-storage-s3 Reference

S3-backed `Storage<T>` — stores JSON-serialized values as S3 objects.

## Usage

```kotlin
val s3Bucket: S3Bucket = S3Bucket.Http(
    bucketName = BucketName.of("my-bucket"),
    bucketRegion = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment()
)

val storage = Storage.S3<MyData>(
    s3 = s3Bucket,
    autoMarshalling = Moshi   // optional, default Moshi
)

storage["key"] = MyData("value")   // PutObject (streams JSON)
val item: MyData? = storage["key"] // GetObject
storage.remove("key")              // DeleteObject
storage.keySet("prefix/")         // ListObjectsV2 + filter by prefix
storage.removeAll("prefix/")      // ListObjectsV2 + DeleteObject for each match
```

## Using with FakeS3 (Tests)

```kotlin
val fakeS3 = FakeS3()
val s3Bucket = fakeS3.s3BucketClient(BucketName.of("test-bucket"), Region.of("us-east-1"))
val storage = Storage.S3<MyData>(s3Bucket)
```

## Gotchas

- **No in-memory fallback** — always requires a real or fake S3 endpoint
- Reads/writes as `InputStream` (different from JDBC/Redis which use strings)
- `removeAll` makes two API calls per item: list then delete (no bulk delete)
- `keySet` lists all objects then filters by prefix client-side
- Key becomes the S3 object key (`BucketKey.of(key)`)
- Requires `http4k-connect-amazon-s3` as a dependency
