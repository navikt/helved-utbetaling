---
license: Apache-2.0
module: http4k-connect-amazon-route53
---

# http4k-connect-amazon-route53 Reference

Route53 client — connect actions for Amazon Route 53 DNS management.

## Client

```kotlin
val route53 = Route53.Http(
    region = Region.of("us-east-1"),   // Route53 is global but region required
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Hosted Zone Management

```kotlin
// Create hosted zone
val zone = route53.createHostedZone(
    name = DNSName.of("example.com."),
    callerReference = CallerReference.of("unique-ref-${System.currentTimeMillis()}")
).successValue().HostedZone

// Get hosted zone
route53.getHostedZone(id = HostedZoneId.of(zone.Id)).successValue()

// Delete hosted zone
route53.deleteHostedZone(id = HostedZoneId.of(zone.Id)).successValue()
```

## DNS Record Management

```kotlin
// List records
val records = route53.listResourceRecordSets(
    hostedZoneId = HostedZoneId.of(zone.Id)
).successValue().ResourceRecordSets

// Create/update/delete records
route53.changeResourceRecordSets(
    hostedZoneId = HostedZoneId.of(zone.Id),
    changeBatch = ChangeBatch(
        changes = listOf(
            Change(
                action = ChangeAction.CREATE,
                resourceRecordSet = ResourceRecordSet(
                    name = DNSName.of("api.example.com."),
                    type = RRType.A,
                    ttl = 300,
                    resourceRecords = listOf(ResourceRecord("1.2.3.4"))
                )
            )
        )
    )
).successValue()
```

## Gotchas

- Uses XML/REST protocol
- DNS names must end with `.` (trailing dot) — e.g., `example.com.`
- `callerReference` must be unique per `createHostedZone` call
- `changeResourceRecordSets` is atomic — all changes succeed or all fail
- Route53 is a global service but requires a region in the client config (use `us-east-1`)
- Zone IDs include a prefix: `/hostedzone/Z1234567` — strip prefix when needed
