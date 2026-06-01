---
license: Apache-2.0
module: http4k-testing-hamkrest
---

# http4k-testing-hamkrest Reference

Hamkrest matchers for http4k types. Composable matchers for Response, Request, HttpMessage, Cookie, and Uri.

## Response Matchers

```kotlin
assertThat(response, hasStatus(OK))
assertThat(response, hasStatusDescription("OK"))
assertThat(response, hasSetCookie(Cookie("session", "abc123")))
```

## HttpMessage Matchers (Request and Response)

```kotlin
assertThat(response, hasBody("expected body"))
assertThat(response, hasBody(containsSubstring("partial")))
assertThat(response, hasBody(myBodyLens, equalTo(expectedObj)))
assertThat(response, hasContentType(APPLICATION_JSON))
assertThat(response, hasHeader("X-Custom", "value"))
assertThat(response, hasHeader("X-Custom", containsSubstring("val")))
assertThat(response, hasHeader(myHeaderLens, equalTo(expectedValue)))
```

## Request Matchers

```kotlin
assertThat(request, hasQuery("name", "value"))
assertThat(request, hasQuery(myQueryLens, equalTo(expectedValue)))
assertThat(request, hasForm("field", "value"))
assertThat(request, hasUri(Uri.of("/path")))
assertThat(request, hasUri(Regex("/path/\\d+")))
assertThat(request, hasCookie("name", "value"))
assertThat(request, hasCookie(myCookieLens, equalTo(expectedValue)))
```

## Cookie Matchers

```kotlin
assertThat(cookie, hasCookieName("session"))
assertThat(cookie, hasCookieValue("abc123"))
assertThat(cookie, hasCookieDomain("example.com"))
assertThat(cookie, hasCookiePath("/"))
assertThat(cookie, isSecureCookie())
assertThat(cookie, isHttpOnlyCookie())
assertThat(cookie, hasCookieExpiry(expectedInstant))
assertThat(cookie, hasCookieSameSite(SameSite.Strict))
```

## Composition

```kotlin
assertThat(response, hasStatus(OK)
    .and(hasBody(containsSubstring("hello")))
    .and(hasContentType(APPLICATION_JSON))
)
```

## Gotchas

- **Lens-based matchers**: Use `hasBody(lens, matcher)` to extract and match typed values via lenses.
- **Composable with `.and()`**: Chain matchers for multiple assertions on the same message.
