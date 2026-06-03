---
license: Apache-2.0
module: http4k-connect-amazon-kms-fake
---

# http4k-connect-amazon-kms-fake Reference

In-memory fake KMS server with real cryptographic operations via BouncyCastle.

## Setup

```kotlin
val fakeKms = FakeKMS()
val client = fakeKms.client()
```

## Custom Key Storage

```kotlin
val fakeKms = FakeKMS(
    keys = Storage.InMemory<StoredCMK>()
)
```

## Deterministic Testing

Pass an injectable `SecureRandom` to make key generation reproducible across test runs:

```kotlin
val deterministicRandom = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(42) }
val fakeKms = FakeKMS(Storage.InMemory(), deterministicRandom)

// Key IDs and key material are deterministic — same seed → same keys
val keyArn = fakeKms.client().createKey().successValue().KeyMetadata.Arn

// Recreating with the same seed produces the same key ARN
val sameKms = FakeKMS(Storage.InMemory(), SecureRandom.getInstance("SHA1PRNG").apply { setSeed(42) })
val sameArn = sameKms.client().createKey().successValue().KeyMetadata.Arn
// keyArn == sameArn
```

## Test Pattern

```kotlin
val kms = FakeKMS().client()

// Create a key
val keyId = kms.createKey().successValue().KeyMetadata.KeyId

// Encrypt and decrypt
val ciphertext = kms.encrypt(keyId, plaintext).successValue().CiphertextBlob
val decrypted = kms.decrypt(ciphertext, keyId).successValue().Plaintext
assertThat(decrypted, equalTo(plaintext))
```

## Chaos Testing

```kotlin
fakeKms.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeKms.behave()
```

## Gotchas

- **Uses real cryptography** (BouncyCastle) — key operations behave like real KMS
- Requires `org.bouncycastle:bcpkix-jdk18on` on the classpath
- Extends `ChaoticHttpHandler`
- Keys are stored in-memory and lost when the fake is discarded
- Signing/verification uses real RSA/EC algorithms
- **Key IDs are hex-encoded random bytes**, not UUIDs — do not hardcode key ID format in tests; always use the returned `KeyMetadata.KeyId` or `KeyMetadata.Arn`
