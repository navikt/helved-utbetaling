---
license: Apache-2.0
module: http4k-testing-kotest
---

# http4k-testing-kotest Reference

Kotest matchers for http4k types. Provides infix-style assertions with `shouldHave*` extensions.

## Response Matchers

```kotlin
response.shouldHaveStatus(OK)
response.shouldNotHaveStatus(NOT_FOUND)
response shouldHaveSetCookie Cookie("session", "abc123")
```

## HttpMessage Matchers

```kotlin
response shouldHaveBody "expected body"
response shouldHaveBody haveBody(containsSubstring("partial"))
response shouldHaveContentType APPLICATION_JSON
response shouldHaveHeader "X-Custom" to "value"
response.shouldHaveHeader("X-Custom", containSubstring("val"))
```

## Request Matchers

```kotlin
request shouldHaveQuery "name" to "value"
request shouldHaveForm "field" to "value"
request shouldHaveUri Uri.of("/path")
request shouldHaveCookie Cookie("name", "value")
```

## Matcher Functions

```kotlin
// Use with shouldBe for custom composition
response shouldBe haveStatus(OK)
request shouldBe haveQuery("name", "value")
response shouldBe haveBody("content")
response shouldBe haveContentType(APPLICATION_JSON)
```

## Gotchas

- **Infix style**: Kotest matchers use `shouldHave*` infix functions for readable assertions.
- **Same coverage as hamkrest**: Covers Response, Request, HttpMessage, Cookie, and Uri — same patterns, different assertion library.
