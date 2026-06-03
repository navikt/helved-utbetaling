---
license: Apache-2.0
module: http4k-connect-amazon-apprunner-fake
---

# http4k-connect-amazon-apprunner-fake Reference

In-memory fake App Runner server for testing service management flows.

## Setup

```kotlin
val fakeAppRunner = FakeAppRunner()
val client = fakeAppRunner.client()
```

## Custom Configuration

```kotlin
val fakeAppRunner = FakeAppRunner(
    services = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val appRunner = FakeAppRunner().client()

val service = appRunner.createService(
    serviceName = ServiceName.of("test-svc"),
    sourceConfiguration = SourceConfiguration(
        imageRepository = ImageRepository("123.dkr.ecr.us-east-1.amazonaws.com/app:latest", ImageRepositoryType.ECR)
    )
).successValue().Service

appRunner.listServices().successValue()
appRunner.deleteService(ServiceArn.of(service.ServiceArn)).successValue()
```

## Chaos Testing

```kotlin
fakeAppRunner.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeAppRunner.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Services are immediately set to `RUNNING` state in the fake (no async delay)
