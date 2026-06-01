---
license: Apache-2.0
module: http4k-testing-powerassert
---

# http4k-testing-powerassert Reference

Boolean assertion functions for use with Kotlin Power Assert. Provides simple `has*` functions that return `Boolean` for rich compiler-generated error messages.

## Usage with Power Assert

```kotlin
// Power assert generates detailed failure messages automatically
assert(response.hasStatus(OK))
assert(response.hasBody("expected"))
assert(request.hasQuery("name", "value"))
```

## Response Functions

```kotlin
response.hasStatus(OK)                    // Boolean
response.hasStatusDescription("OK")       // Boolean
response.hasSetCookie(Cookie("s", "v"))   // Boolean
response.hasSetCookie("name")             // Boolean
```

## HttpMessage Functions

```kotlin
response.hasBody("expected")              // Boolean
response.hasContentType(APPLICATION_JSON) // Boolean
response.hasHeader("X-Custom", "value")   // Boolean
```

## Request Functions

```kotlin
request.hasQuery("name", "value")         // Boolean
request.hasForm("field", "value")         // Boolean
request.hasUri(Uri.of("/path"))           // Boolean
request.hasCookie("name", "value")        // Boolean
```

## Gotchas

- **Requires kotlin-power-assert plugin**: These functions return `Boolean` — without the power-assert compiler plugin, failures just show `AssertionError` with no detail.
- **Use `assert()` not `assertTrue()`**: Power assert transforms `assert()` calls for rich error output.
- **Inline functions**: All functions are `inline` for power-assert compatibility.
