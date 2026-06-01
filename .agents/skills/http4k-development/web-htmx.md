---
license: Apache-2.0
module: http4k-web-htmx
---

# http4k-web-htmx Reference

HTMX integration for http4k — headers, request detection, and webjar serving.

## Request Detection

```kotlin
request.isHtmx()                    // HX-Request header present
request.isHtmxBoosted()             // HX-Boosted header present
request.isHtmxHistoryRestoreRequest() // HX-History-Restore-Request header present
```

## HTMX Request Headers (Lenses)

```kotlin
Header.HX_REQUEST(request)          // "true" if HTMX request
Header.HX_BOOSTED(request)          // boosted request
Header.HX_CURRENT_URL(request)      // current URL
Header.HX_HISTORY_RESTORE_REQUEST(request)
Header.HX_PROMPT(request)           // user prompt value
Header.HX_TARGET(request)           // target element ID
Header.HX_TRIGGER(request)          // triggered element ID
Header.HX_TRIGGER_NAME(request)     // triggered element name
```

## HTMX Response Headers (Lenses)

```kotlin
val response = Response(OK)
    .with(Header.HX_LOCATION of "/new-url")
    .with(Header.HX_PUSH_URL of "/push")
    .with(Header.HX_REDIRECT of "/redirect")
    .with(Header.HX_REFRESH of true)
    .with(Header.HX_REPLACE_URL of "/replace")
    .with(Header.HX_RESWAP of HxSwap.outerHTML)
    .with(Header.HX_RETARGET of CssSelector.of("#target"))
    .with(Header.HX_RESELECT of CssSelector.of(".items"))
    .with(Header.HX_TRIGGER_AFTER_SETTLE of "event")
    .with(Header.HX_TRIGGER_AFTER_SWAP of "event")
```

## Swap Modes

```kotlin
HxSwap.innerHTML
HxSwap.outerHTML
HxSwap.beforebegin
HxSwap.afterbegin
HxSwap.beforeend
HxSwap.afterend
HxSwap.delete
HxSwap.none
```

## Stop Polling

```kotlin
Response(Status.STOP_POLLING)   // Status(286, "Stop Polling")
```

## Webjars (HTMX + Hyperscript)

```kotlin
val routes = routes(
    htmxWebjars(),
    // ... other routes
)
```

Serves HTMX and Hyperscript from classpath webjars with redirects.

## Value Types

```kotlin
CssSelector.of("#my-element")   // CSS selector wrapper
Id.of("my-element")             // element ID wrapper
```
