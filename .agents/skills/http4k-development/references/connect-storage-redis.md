---
license: Apache-2.0
module: http4k-connect-storage-redis
---

# http4k-connect-storage-redis Reference

Redis-backed `Storage<T>` with TTL support, using Lettuce client.

## Usage

```kotlin
// Simple (1 hour TTL for all items)
val storage = Storage.Redis<MyData>(
    uri = Uri.of("redis://localhost:6379"),
    autoMarshalling = Moshi  // optional
)

// Dynamic per-value TTL
val storage = Storage.Redis<MyData>(
    uri = Uri.of("redis://localhost:6379"),
    ttl = { data -> if (data.important) Duration.ofDays(7) else Duration.ofHours(1) }
)

// From existing Lettuce connection
val storage = Storage.Redis<MyData>(
    redis = redisCommands,
    ttl = Duration.ofHours(1)
)
```

## Operations

```kotlin
storage["key"] = MyData("value")   // SET with TTL
val item: MyData? = storage["key"] // GET (null if expired or not found)
storage.remove("key")              // DEL (returns true if existed)
storage.keySet("prefix/")         // KEYS prefix/* (pattern matching)
storage.removeAll("prefix/")      // DEL all matching KEYS prefix/*
```

## Dynamic TTL Variant

```kotlin
val storage = Storage.RedisWithDynamicTtl(
    redis = redisCommands,
    ttl = { data: MyData -> computeTtl(data) }
)
```

## Gotchas

- **TTL throws** `RuntimeException` if the Redis `SET` response is not `"OK"`
- `keySet`/`removeAll` use Redis `KEYS pattern*` — avoid in production with large keyspaces (use `SCAN` instead for large datasets)
- Default TTL is 1 hour if not specified
- Uses Lettuce (`io.lettuce.core`) for the Redis connection
- Items silently expire — `get` returns `null` for expired keys
