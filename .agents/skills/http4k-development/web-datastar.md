---
license: Apache-2.0
module: http4k-web-datastar
---

# http4k-web-datastar Reference

Datastar (hypermedia framework) integration — SSE-based DOM patching and signal updates.

## Response Helpers

```kotlin
// Patch HTML elements into the DOM (defaults: mode=outer, useViewTransition=false)
Response(OK).datastarElements(
    Element.of("<div id='content'>Hello</div>"),
    mode = MorphMode.replace,            // only included in event when not `outer`
    selector = Selector.of("#target"),
    useViewTransition = true             // only included in event when `true`
)

// Update reactive signals/store
Response(OK).datastarSignals(
    Signal.of("""{"count": 42}"""),
    onlyIfMissing = false
)
```

## Events

```kotlin
sealed class DatastarEvent {
    data class PatchElements(
        val elements: List<Element>,
        val mode: MorphMode,
        val selector: Selector?,
        val useViewTransition: Boolean,
        val id: SseEventId?
    ) : DatastarEvent()

    data class PatchSignals(
        val signals: List<Signal>,
        val onlyIfMissing: Boolean?,
        val id: SseEventId?
    ) : DatastarEvent()
}
```

## Morph Modes

```kotlin
MorphMode.outer    // replace element including tag
MorphMode.inner    // replace element contents only
MorphMode.replace  // replace element
MorphMode.prepend  // insert before first child
MorphMode.append   // insert after last child
MorphMode.before   // insert before element
MorphMode.after    // insert after element
MorphMode.remove   // remove element
```

## Reading Events from Response

```kotlin
// Parse events from SSE response body
val events: List<DatastarEvent> = response.datastarEvents()

// Lens for body parsing
val lens = Body.datastarEvents()
val events = lens(response)
```

## Headers & Detection

```kotlin
Header.DATASTAR_REQUEST(request)          // true if Datastar request
Header.DATASTAR_CONTENT_TYPE(response)    // content type header
Query.DATASTAR_MODEL(request)             // signal model for GET requests
```

## Gotchas

- **Default fields omitted**: `PatchElements` only includes `mode` and `useViewTransition` in the SSE event data when their values differ from defaults (`outer` and `false` respectively).
- **Multi-line elements**: Elements preserve newlines. Multi-line HTML is split into separate SSE `data` lines per line of content.
- SSE messages separated by double newlines
- Use GET with `DATASTAR_MODEL` query param or POST with body for signals
