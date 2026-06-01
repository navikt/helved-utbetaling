---
license: Apache-2.0
module: http4k-connect-amazon-instancemetadata
---

# http4k-connect-amazon-instancemetadata Reference

InstanceMetadataService client — connect actions for the EC2 Instance Metadata Service (IMDS).

## Client

```kotlin
val imds = InstanceMetadataService.Http(
    http = JavaHttpClient()   // optional — connects to 169.254.169.254
)
```

## Instance Information

```kotlin
val instanceId = imds.getInstanceId().successValue()
val instanceType = imds.getInstanceType().successValue()
val amiId = imds.getAmiId().successValue()
```

## Security Credentials (IAM Role)

```kotlin
val credentials = imds.getSecurityCredentials(
    roleName = RoleName.of("my-instance-role")
).successValue()
// Returns: AccessKeyId, SecretAccessKey, Token, Expiration
```

## Instance Identity Document

```kotlin
val identity = imds.getInstanceIdentityDocument().successValue()
// Returns: instanceId, accountId, region, availabilityZone, imageId, etc.
```

## Gotchas

- Uses IMDSv2 by default — requires token-based access (PUT request for session token first)
- Connects to link-local address `169.254.169.254` — only available from EC2/ECS
- `getSecurityCredentials` requires knowing the IAM role name attached to the instance
- Identity document is used for instance verification (signed by AWS)
- IMDSv1 (direct GET without token) may be disabled on hardened instances
- Not available in Lambda — use environment variables or `CredentialsProvider.Environment()` instead
