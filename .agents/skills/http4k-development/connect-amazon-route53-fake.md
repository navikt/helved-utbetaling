---
license: Apache-2.0
module: http4k-connect-amazon-route53-fake
---

# http4k-connect-amazon-route53-fake Reference

In-memory fake Route 53 server for testing DNS management flows.

## Setup

```kotlin
val fakeRoute53 = FakeRoute53()
val client = fakeRoute53.client()
```

## Custom Configuration

```kotlin
val fakeRoute53 = FakeRoute53(
    hostedZones = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val route53 = FakeRoute53().client()

val zone = route53.createHostedZone(DNSName.of("test.com."), CallerReference.of("ref-1")).successValue().HostedZone
val zoneId = HostedZoneId.of(zone.Id)

route53.changeResourceRecordSets(
    zoneId,
    ChangeBatch(listOf(Change(ChangeAction.CREATE, ResourceRecordSet(
        DNSName.of("sub.test.com."), RRType.A, 300, listOf(ResourceRecord("10.0.0.1"))
    ))))
).successValue()
```

## Chaos Testing

```kotlin
fakeRoute53.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeRoute53.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- DNS propagation is instantaneous in the fake
- Record sets are stored and queryable via `listResourceRecordSets`
