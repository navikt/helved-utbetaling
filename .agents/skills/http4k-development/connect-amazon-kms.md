---
license: Apache-2.0
module: http4k-connect-amazon-kms
---

# http4k-connect-amazon-kms Reference

KMS client — connect actions for AWS Key Management Service.

## Client

```kotlin
val kms = KMS.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Key Management

```kotlin
// Create a key
val keyId = kms.createKey(
    description = "My signing key",
    keyUsage = SIGN_VERIFY,
    keySpec = RSA_2048
).successValue().KeyMetadata.KeyId

kms.describeKey(keyId).successValue()
kms.listKeys().successValue()
kms.scheduleKeyDeletion(keyId, pendingWindowInDays = 7).successValue()
```

## Encryption / Decryption

```kotlin
val ciphertext = kms.encrypt(
    keyId = keyId,
    plaintext = "secret data".toByteArray(),
    encryptionAlgorithm = SYMMETRIC_DEFAULT
).successValue().CiphertextBlob

val plaintext = kms.decrypt(
    ciphertextBlob = ciphertext,
    keyId = keyId
).successValue().Plaintext
```

## Data Key Generation

```kotlin
// Returns both plaintext and encrypted key
val dataKey = kms.generateDataKey(
    keyId = keyId,
    keySpec = AES_256
).successValue()

val plaintextKey = dataKey.Plaintext       // encrypt data locally
val encryptedKey = dataKey.CiphertextBlob  // store alongside encrypted data

// Without plaintext (for envelope encryption setup)
kms.generateDataKeyWithoutPlaintext(keyId, AES_256).successValue()
```

## Signing / Verification

```kotlin
val signature = kms.sign(
    keyId = keyId,
    message = "message to sign".toByteArray(),
    signingAlgorithm = RSASSA_PKCS1_V1_5_SHA_256,
    messageType = RAW
).successValue().Signature

val valid = kms.verify(
    keyId = keyId,
    message = "message to sign".toByteArray(),
    signature = signature,
    signingAlgorithm = RSASSA_PKCS1_V1_5_SHA_256
).successValue().SignatureValid
```

## Gotchas

- Uses JSON protocol with internal service name `"TrentService"` (KMS internals)
- Key IDs can be: key ID UUID, key ARN, alias name (`alias/my-key`), or alias ARN
- `scheduleKeyDeletion` has a minimum 7-day pending window
- Data key pattern (envelope encryption): generate data key, encrypt data locally, store encrypted data key
