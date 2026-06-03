---
license: Apache-2.0
module: http4k-tools-traffic-capture
---

# http4k-tools-traffic-capture Reference

Record, replay, and serve cached HTTP traffic.

## Sink (Record Traffic)

```kotlin
// Record to in-memory map (keyed by request)
val sink = Sink.MemoryMap()

// Record to in-memory stream (sequential)
val stream = ReadWriteStream.Memory()
val sink = Sink.MemoryStream(stream)

// Record to disk as a tree (one file pair per unique request)
val sink = Sink.DiskTree(Path.of("./traffic"))

// Record to disk as a stream (sequential numbered folders)
val sink = Sink.DiskStream(Path.of("./traffic"))
```

## Source (Serve Cached Responses)

```kotlin
val source = Source.MemoryMap(cache)
val source = Source.DiskTree(Path.of("./traffic"))
```

## Replay (Iterate Recorded Traffic)

```kotlin
val replay = Replay.MemoryStream(stream)
val replay = Replay.DiskStream(Path.of("./traffic"))

replay.requests().forEach { println(it) }
replay.responses().forEach { println(it) }
```

## Filters

```kotlin
// Record all traffic to a sink
val app = TrafficFilters.RecordTo(sink).then(realApp)

// Serve cached responses (falls back to next handler if not found)
val app = TrafficFilters.ServeCachedFrom(source).then(fallback)

// Replay stored traffic in order
val app = TrafficFilters.ReplayFrom(replay)
```

## ReadWriteStream

```kotlin
val stream = ReadWriteStream.Memory()
// Acts as both Sink and Replay
RecordTo(stream).then(realApp)
stream.requests().toList()   // all recorded requests
stream.responses().toList()  // all recorded responses
```

## Filtering What Gets Stored

```kotlin
Sink.DiskTree(dir, shouldStore = { req, resp -> resp.status.successful })
Sink.MemoryMap(shouldStore = { req, _ -> req.method == GET })
```

## Gotchas

- `ReplayFrom` returns `BAD_REQUEST` if request doesn't match or sequence is exhausted
- Default match function uses `toString()` comparison
- Copy request/response bodies before recording to avoid stream exhaustion
