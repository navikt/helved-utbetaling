---
license: Apache-2.0
module: http4k-testing-strikt
---

# http4k-testing-strikt Reference

Strikt assertion extensions for http4k types. Fluent property-chain style assertions.

## Response Assertions

```kotlin
expectThat(response) {
    status.isEqualTo(OK)
}
```

## HttpMessage Assertions

```kotlin
expectThat(response) {
    body.isEqualTo(Body("expected"))
    bodyString.isEqualTo("expected")
    contentType.isEqualTo(APPLICATION_JSON)
    header("X-Custom").isEqualTo("value")
    headerValues("X-Multi").containsExactly("a", "b")
}
```

## Request Assertions

```kotlin
expectThat(request) {
    uri.get { path }.isEqualTo("/api/users")
    method.isEqualTo(GET)
    query("name").isEqualTo("value")
    form("field").isEqualTo("value")
    cookie("session").isNotNull()
}
```

## Lens-Based Assertions

```kotlin
expectThat(request) {
    query(myQueryLens).isEqualTo(expectedValue)
    header(myHeaderLens).isEqualTo(expectedValue)
}
```

## Gotchas

- **Property-chain style**: Use `get { property }` for nested assertions on extracted values.
- **Extension properties**: `status`, `body`, `bodyString`, `contentType` are extension properties on `Assertion.Builder`.
