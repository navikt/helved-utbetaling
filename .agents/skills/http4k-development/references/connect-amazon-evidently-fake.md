---
license: Apache-2.0
module: http4k-connect-amazon-evidently-fake
---

# http4k-connect-amazon-evidently-fake Reference

In-memory fake Evidently server for testing.

## Setup

```kotlin
val fakeEvidently = FakeEvidently()
val client = fakeEvidently.client()
```

## Chaos Testing

```kotlin
fakeEvidently.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeEvidently.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Placeholder module — check current action coverage in the http4k-connect source
