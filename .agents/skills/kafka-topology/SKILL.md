---
name: kafka-topology
description: Build Kafka Streams topologies using the helved-utbetaling custom DSL
license: MIT
compatibility: opencode
metadata:
  audience: ai-assistant
  language: kotlin
  framework: kafka-streams
  domain: nav-payment-system
---

## What I do

I guide you to use the custom Kafka Streams DSL in `libs/kafka/` correctly. This DSL wraps raw Kafka Streams with type-safe builders, custom processors, and opinionated error handling. Do NOT use raw Kafka Streams APIs -- use these abstractions instead.

## When to use me

Load this skill when writing or modifying Kafka topologies, Topics, state stores, or stream processors.

> **Prerequisites:** Root `AGENTS.md` covers general architecture. `apps/AGENTS.md` has the Kafka topic reference table. Load `libs-reference` skill for full library API details.

---

## 1. Topology Builder

Entry point -- the `topology { }` function with `Topology` as the receiver:

```kotlin
val topology = topology("app-name") {
    // Available operations inside this block:
    consume(topic)              // -> ConsumedStream<K,V>
    consume(table)              // -> KTable<K,V>
    globalKTable(table)         // -> GlobalKTable<K,V>
    intercept { builder -> }    // escape hatch to raw StreamsBuilder
}
```

---

## 2. Topic, Table, Store

### Topic

Type-safe topic abstraction with key/value serdes:

```kotlin
object Topics {
    val utbetalinger = Topic<String, Utbetaling>(
        name = "helved.utbetalinger.v1",
        keySerde = Serdes.String(),
        valueSerde = JsonSerde(Utbetaling::class)
    )
}
```

### Table

Wraps a `Topic` with a state store name. Used for KTable materialization:

```kotlin
val sakerTable = Table<SakKey, Set<UtbetalingId>>(
    topic = Topics.saker,
    storeName = "saker-store"
)
```

### Store / StateStore

`Store` defines a state store. `StateStore` is the read-only runtime wrapper:

```kotlin
val store: StateStore<K, V> = streams.getStore(myStore)
store.getOrNull(key)   // K -> V?
store.iterator()       // Iterator<KeyValue<K, V>>
store.filter { k, v -> predicate }
```

---

## 3. Stream Type Chain

The DSL enforces a type-safe progression. Each type exposes only valid operations:

```
consume(topic)  ->  ConsumedStream
                      .map { k, v -> Pair(k2, v2) }    ->  MappedStream
                      .branch(predicate) { ... }        ->  BranchedStream
                      .forEach { k, v -> }              (terminal)
                      .groupByKey()                     ->  GroupedStream

MappedStream
  .join(table) { v, tableV -> }    ->  JoinedStream
  .leftJoin(table) { v, tableV -> } ->  JoinedStream
  .branch(predicate) { ... }       ->  BranchedMappedStream
  .produce(topic)                   (terminal)

BranchedStream / BranchedMappedStream
  .branch(predicate) { ... }       (chain more branches)
  .default { ... }                  (catch-all, required to end branching)

GroupedStream
  .aggregate(init, aggregator)      ->  KTable
  .windowedBy(window)               ->  TimeWindowedStream / SessionWindowedStream
```

### Key rules

- `.branch()` requires a `.default {}` to terminate
- `.produce(topic)` is the terminal operation to write to a topic
- `.forEach {}` is the terminal operation for side effects (DB writes, logging)
- `.rekey { newKey }` changes the stream key (triggers repartition)
- `.repartition(numPartitions)` explicitly repartitions

---

## 4. Serde Helpers

Top-level functions in `Serde.kt`:

```kotlin
string()          // Serdes<String, String>
bytes()           // Serdes<ByteArray, ByteArray>
json<V>()         // Serdes<String, V> using Jackson
jsonList<V>()     // Serdes<String, List<V>>
xml<V>()          // Serdes<String, V> using Jackson XML
jaxb<V>()         // Serdes<String, V> using JAXB
```

---

## 5. Error Handling

### In processors: `Result.catch { }`

Wraps business logic in a `Result<V, StatusReply>`. Catches `ApiError` and `Throwable`:

```kotlin
consume(topic)
    .map { key, value ->
        Result.catch {
            // Business logic that may throw
            processPayment(value)
        }
    }
    .branch(Result::isErr) { stream ->
        // Route errors to status topic
        stream.map { key, err -> Pair(key, err.unwrap()) }
            .produce(Topics.status)
    }
    .default { stream ->
        // Happy path continues
        stream.map { key, ok -> Pair(key, ok.unwrap()) }
            .produce(Topics.output)
    }
```

### Infrastructure-level error handlers

Configured on the Kafka Streams instance (not in topology code):

- `DeserializationAgainHandler` / `DeserializationNextHandler` -- deserialization failures
- `ProductionAgainHandler` / `ProductionNextHandler` -- production failures
- `UncaughtHandler` -- shuts down the Kafka client

---

## 6. Processors

| Processor | Purpose |
|-----------|---------|
| `Processor<Kin,Vin,Kout,Vout>` | Simple stateless transform |
| `StateProcessor<K,V,U,R>` | Processor with named state store access |
| `StateScheduleProcessor<K,V>` | Wall-clock scheduled punctuator on KTable state |
| `SuppressProcessor` | Buffers windowed records, emits after inactivity gap |
| `DedupProcessor` | Deduplicates by key+value hash within retention period |
| `EnrichMetadataProcessor` | Enriches record with `Metadata` (topic, partition, offset, timestamps, headers) |

---

## 7. Named Processors

Every processor must have a unique name. The `Named` value class registers names in a global `Names` singleton that fails on duplicates:

```kotlin
// Names are typically derived from topic + operation
// The DSL handles this automatically for most operations
// Custom processors need explicit naming
```

In tests, clear the `Names` singleton between test runs (typically in `@AfterEach`).

---

## 8. Real Examples

### Simple: Audit logger (peisschtappern pattern)

Consumes all topics as raw bytes, enriches with metadata, persists to DB:

```kotlin
topology("peisschtappern") {
    consume(Topics.oppdrag)  // bytes()
        .process(EnrichMetadataProcessor())
        .forEach { key, value ->
            // Persist to DB (uses runBlocking since Kafka Streams is not coroutine-based)
            dao.insert(key, value)
        }
}
```

### Moderate: Status sync + aggregation (utsjekk pattern)

```kotlin
topology("utsjekk") {
    val sakerTable = globalKTable(Tables.saker, retention = 24.hours)

    consume(Topics.utbetalinger)
        .groupByKey()
        .aggregate(
            initializer = { emptySet() },
            aggregator = { key, value, acc -> acc + value.uid }
        )

    consume(Topics.status)
        .forEach { key, status ->
            // Write to DB (not coroutine-based)
            runBlocking {
                withContext(jdbcCtx) {
                    StatusDao.upsert(key, status)
                }
            }
        }
}
```

### Complex: Payment aggregation (abetal pattern)

Multiple sub-topologies per fagsystem, each following the same flow:

```kotlin
topology("abetal") {
    val sakerTable = globalKTable(Tables.saker)
    val pendingTable = globalKTable(Tables.pendingUtbetalinger)

    // One sub-topology per fagsystem (dp, aap, ts, tp, historisk)
    consume(Topics.dpExternal)
        .repartition(3)
        .merge(consume(Topics.dpInternal))
        .map { key, value -> Pair(SakKey(value), value) }
        .leftJoin(sakerTable) { value, existingSaker ->
            Result.catch { aggregate(value, existingSaker) }
        }
        .branch(Result::isErr) { stream ->
            stream.map { k, v -> Pair(k, v.unwrap()) }
                .produce(Topics.status)
        }
        .default { stream ->
            stream.map { k, v -> Pair(k, v.unwrap()) }
                .produce(Topics.oppdrag)
            // Also produce pending + status
        }

    // Kvittering handling: join oppdrag with pending to produce final utbetalinger
    consume(Topics.oppdrag)
        .filter { _, oppdrag -> oppdrag.hasKvittering() }
        .flatMap { _, oppdrag -> oppdrag.uids.map { uid -> Pair(uid, oppdrag) } }
        .leftJoin(pendingTable) { oppdrag, pending ->
            if (pending != null) pending.withKvittering(oppdrag)
            else null  // retry later
        }
        .branch({ _, v -> v == null }) { stream ->
            stream.produce(Topics.retryOppdrag)  // not found yet, retry
        }
        .default { stream ->
            stream.produce(Topics.utbetalinger)  // final payment
        }
}
```

Key patterns in abetal:
- `.repartition(3)` for consistent processing across partitions
- `.merge()` combines external + internal topics
- `.leftJoin()` with GlobalKTable for stateful processing
- `.branch()` / `.default {}` for routing errors vs happy path
- `Result.catch {}` wraps all business logic
- `.flatMap()` to fan out from oppdrag to individual UIDs

---

## 9. Testing with StreamsMock

```kotlin
object TestRuntime {
    val kafka = StreamsMock()
}

@Test
fun `produces oppdrag from payment request`() {
    TestRuntime.kafka.connect(createTopology())

    val input = TestRuntime.kafka.testTopic(Topics.dpExternal)
    val output = TestRuntime.kafka.testTopic(Topics.oppdrag)

    input.produce("key1", PaymentRequest(...))

    output.assertThat()
        .hasTotal(1)
        .has("key1", expectedOppdrag)
}

@Test
fun `routes errors to status topic`() {
    TestRuntime.kafka.connect(createTopology())

    val input = TestRuntime.kafka.testTopic(Topics.dpExternal)
    val status = TestRuntime.kafka.testTopic(Topics.status)

    input.produce("key1", invalidRequest)

    status.assertThat()
        .hasTotal(1)
        .has("key1", StatusReply(Status.FEILET, ...))
}
```

### TopicAssertion API

```kotlin
topic.assertThat()
    .hasTotal(n)                    // exact count
    .has(key, expectedValue)        // key-value match
    .hasNot(key, unexpectedValue)   // negative match
    .hasTombstone(key)              // null value for key
    .hasHeader("name", "value")     // header check
    .isEmpty()                      // no records
```

### State store access in tests

```kotlin
val store = TestRuntime.kafka.getStore(Tables.saker)
val saker = store.getOrNull(sakKey)
assertNotNull(saker)
```

### Wall-clock punctuators in tests

```kotlin
TestRuntime.kafka.advanceWallClockTime(Duration.ofMinutes(5))
// Triggers any StateScheduleProcessor punctuators
```

---

## 10. Important Constraints

1. **Kafka Streams is NOT coroutine-based.** Use `runBlocking` for DB access inside `forEach`. Do NOT use `suspend` functions in stream processors.
2. **Do NOT use raw Kafka Streams APIs.** Always use the DSL wrappers (`Topic`, `Table`, `consume()`, etc.).
3. **Every `.branch()` chain must end with `.default {}`.**
4. **Processor names must be unique.** The `Named` singleton enforces this at topology construction time.
5. **State stores are eventually consistent.** GlobalKTables replicate across all instances but have replication lag.
6. **snickerboa is the exception** -- it uses vanilla `KafkaProducer`/`KafkaConsumer` via `KafkaFactory` for request-reply correlation, not the topology DSL.
