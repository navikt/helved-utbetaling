---
license: Apache-2.0
module: http4k-testing-chaos
---

# http4k-testing-chaos Reference

Chaos engineering for http4k. Inject failures, latency, and other chaotic behaviours into handlers at runtime.

## ChaosEngine

```kotlin
val engine = ChaosEngine(ChaosBehaviours.ReturnStatus(SERVICE_UNAVAILABLE))
val app = engine.then(myHandler)

// Toggle chaos at runtime
engine.enable()
engine.disable()

// Switch behaviour
engine.enable(ChaosBehaviours.Latency(100.millis, 500.millis))
```

## Behaviours

```kotlin
ChaosBehaviours.ReturnStatus(INTERNAL_SERVER_ERROR)   // return fixed status
ChaosBehaviours.ReturnResponse(Response(OK).body("forced")) // return a specific full response
ChaosBehaviours.Latency(100.millis, 500.millis)       // random delay
ChaosBehaviours.ThrowException(RuntimeException("!")) // throw exception
ChaosBehaviours.NoBody()                              // strip response body
ChaosBehaviours.SnipBody(maxLen)                      // truncate body
ChaosBehaviours.SnipRequestBody(maxLen)                // truncate request body
ChaosBehaviours.None()                                 // no-op
ChaosBehaviours.EatMemory()                            // OOM (destructive)
ChaosBehaviours.StackOverflow()                        // stack overflow (destructive)
ChaosBehaviours.KillProcess()                          // exit JVM (destructive)
ChaosBehaviours.BlockThread()                          // deadlock (destructive)
```

`ReturnResponse` is also supported in the chaos JSON format used by the Remote Control API:

```json
{"type": "response", "status": 503, "headers": {"Retry-After": "60"}, "body": "unavailable"}
```

The `headers` and `body` fields are optional.

## Stages and Triggers

```kotlin
// Behaviour applied when trigger matches
val stage = ChaosBehaviours.ReturnStatus(NOT_FOUND)
    .appliedWhen(ChaosTriggers.Always())

// Chain stages: first for N requests, then switch
val staged = ChaosBehaviours.Latency(100.millis, 500.millis)
    .appliedWhen(ChaosTriggers.Always())
    .then(ChaosBehaviours.ReturnStatus(SERVICE_UNAVAILABLE)
        .appliedWhen(ChaosTriggers.Always()))

// Repeat stages
ChaosStages.Repeat { staged }

// Variable stage (mutable at runtime)
val variable = ChaosStages.Variable(stage)
```

## Remote Control API

```kotlin
// ChaosEngine exposes HTTP endpoints for remote control
val app = engine.then(myHandler)

app(Request(POST, "/chaos/activate"))    // enable chaos
app(Request(GET, "/chaos/status"))       // check status
app(Request(POST, "/chaos/deactivate"))  // disable chaos
```

## Gotchas

- **Engine is a Filter**: `ChaosEngine` implements `Filter` — compose it with `.then(handler)`.
- **Destructive behaviours**: `EatMemory`, `StackOverflow`, `KillProcess`, `BlockThread` are genuinely destructive. Use with caution.
- **Default is disabled**: ChaosEngine starts disabled. Call `enable()` to activate.
