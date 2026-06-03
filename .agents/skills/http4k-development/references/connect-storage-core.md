---
license: Apache-2.0
module: http4k-connect-storage-core
---

# http4k-connect-storage-core Reference

Generic key-value storage abstraction with in-memory and disk implementations.

## Interface

```kotlin
interface Storage<T : Any> {
    operator fun get(key: String): T?
    operator fun set(key: String, data: T)
    fun remove(key: String): Boolean
    fun keySet(keyPrefix: String = ""): Set<String>
    fun removeAll(keyPrefix: String = ""): Boolean
    companion object
}
```

## InMemory

```kotlin
val storage = Storage.InMemory<MyData>()

storage["key"] = MyData("value")
val item: MyData? = storage["key"]
storage.remove("key")
val keys: Set<String> = storage.keySet("prefix/")
storage.removeAll("prefix/")   // returns false if nothing matched
```

Thread-safe (`ConcurrentHashMap`).

## Disk

```kotlin
val storage = Storage.Disk<MyData>(
    dir = File("./data"),
    autoMarshalling = Moshi  // optional, default Moshi
)
```

Stores each value as a JSON file. Key becomes the filename.

## Contract Tests

```kotlin
abstract class StorageContract {
    abstract val storage: Storage<AnEntity>

    @Test fun `can store and retrieve`() { ... }
    @Test fun `remove returns false if not found`() { ... }
    @Test fun `keySet filters by prefix`() { ... }
}

class InMemoryStorageTest : StorageContract() {
    override val storage = Storage.InMemory<AnEntity>()
}
```

## Gotchas

- `removeAll` returns `false` if no items match the prefix (not an error)
- `get` returns `null` if key not found
- Both `InMemory` and `Disk` are generic — specify type parameter at construction
- `Disk` uses AutoMarshalling for JSON serialization (defaults to Moshi)
