---
license: Apache-2.0
module: http4k-format-core
---

# http4k-format-core Reference

Base abstractions for all format modules. Provides `AutoMarshalling`, `AutoMarshallingJson<NODE>`, `Json<NODE>`, and `AutoMappingConfiguration`.

## Type Hierarchy

```kotlin
AutoMarshalling                         // base: String/InputStream ↔ typed objects
├── AutoMarshallingJson<NODE>           // adds Json<NODE> for JSON node manipulation
│   ├── ConfigurableJackson             // Jackson
│   ├── ConfigurableGson                // Gson
│   ├── ConfigurableMoshi               // Moshi
│   ├── ConfigurableKotlinxSerialization // kotlinx.serialization
│   └── KondorJson                      // Kondor
├── AutoMarshallingXml                  // adds XML-specific methods
│   ├── ConfigurableJacksonXml          // Jackson XML
│   └── Xml                             // org.json XML
└── (direct AutoMarshalling)
    ├── ConfigurableKlaxon              // Klaxon
    ├── ConfigurableJacksonYaml         // Jackson YAML
    ├── ConfigurableMoshiYaml           // Moshi YAML
    ├── ConfigurableJacksonCsv          // Jackson CSV
    └── ConfigurableToon                // Toon
```

## AutoMarshalling

Base class providing typed serialization/deserialization and lens integration.

```kotlin
// Core operations
marshaller.asA<MyType>(jsonString)          // parse String → T
marshaller.asA<MyType>(inputStream)         // parse InputStream → T
marshaller.asFormatString(myObject)         // serialize T → String
marshaller.convert<InputType, OutputType>(input) // roundtrip via format string

// Lens integration — all AutoMarshalling implementations provide these
Body.auto<MyType>().toLens()                // BiDiBodyLensSpec<MyType>
WsMessage.auto<MyType>().toLens()           // BiDiWsMessageLensSpec<MyType>

// Usage with lenses
val lens = Body.auto<MyType>().toLens()
val obj: MyType = lens(request)             // extract from request
val response = Response(OK).with(lens of myObj) // inject into response
```

## Json\<NODE\>

Interface for raw JSON node creation, inspection, and formatting. Implemented by all JSON format modules.

```kotlin
// Parsing and formatting
val node: NODE = json.parse("""{"key": "value"}""")
val compact: String = json.compact(node)
json.prettify(jsonString)
json.compactify(jsonString)

// Node construction
json.string("hello")
json.number(42)
json.boolean(true)
json.nullNode()
json.array(listOf(node1, node2))
json.obj("key" to node1, "other" to node2)

// Primitive conversions
"hello".asJsonValue()
42.asJsonValue()
true.asJsonValue()
listOf(node1, node2).asJsonArray()
listOf("k" to node1).asJsonObject()

// Node inspection
json.typeOf(node)           // JsonType: Object, Array, String, Integer, Number, Boolean, Null
json.fields(node)           // Iterable<Pair<String, NODE>>
json.elements(node)         // Iterable<NODE>
json.text(node)             // String
json.bool(node)             // Boolean
json.integer(node)          // Long
json.decimal(node)          // BigDecimal
json.textValueOf(node, "key") // String?

// JSON body lens (for raw NODE, not typed objects)
val jsonLens = Body.json().toLens()

// DSL scope
json { obj("name" to string("http4k")) }
```

## AutoMappingConfiguration

Generic interface for registering custom type mappings. All format modules provide `.asConfigurable()`.

```kotlin
val custom = Jackson.custom {
    // Register custom type mappings
    text(BiDiMapping(::MyId, MyId::value))
    int(BiDiMapping(::Age, Age::value))
    boolean(BiDiMapping(::Flag, Flag::value))
    long(BiDiMapping(::Timestamp, Timestamp::millis))
    double(BiDiMapping(::Score, Score::value))
    bigDecimal(BiDiMapping(::Price, Price::amount))
    bigInteger(BiDiMapping(::Counter, Counter::value))

    // Shorthand with lambdas
    text({ MyId(it) }, { it.value })
}
```

### withStandardMappings()

Registers all standard http4k type mappings in one call. Applied by default in all pre-built singletons.

```kotlin
// What it registers:
// Java Time: Instant, LocalDate, LocalTime, LocalDateTime, ZonedDateTime,
//            OffsetTime, OffsetDateTime, YearMonth, Duration, Period, ZoneId, ZoneOffset
// Standard:  Uri, URL, UUID, Regex, Locale
// HTTP:      Status, EventCategory, TraceId, SamplingDecision
// Other:     Throwable (message as string)
```

### values4k Integration

```kotlin
// Map values4k Value types directly
text(MyStringValue)       // for Value<String>
int(MyIntValue)           // for Value<Int>
boolean(MyBoolValue)      // for Value<Boolean>
```

## AutoMarshallingEvents

Adapter for structured event logging using any format module.

```kotlin
val events: Events = AutoMarshallingEvents(Jackson)
events(MyEvent("something happened"))  // prints JSON to stdout

// Custom output
val events = AutoMarshallingEvents(Jackson) { line -> logger.info(line) }
```

## Gotchas

- **`withStandardMappings()` is already applied**: The pre-built singletons (`Jackson`, `Gson`, etc.) already include standard mappings. Only call it when building a custom configurable instance from scratch.
- **`Body.auto<T>()` vs `Body.json()`**: `auto<T>()` deserializes to a typed object; `json()` gives you raw JSON nodes. Use `auto` for domain types, `json` for dynamic JSON manipulation.
- **`prohibitStrings()` for security**: Call on `AutoMappingConfiguration` to prevent deserialization of unbounded string fields — useful for APIs that should only accept structured types.
- **Lens failures are typed**: Missing values throw `LensFailure` with `Missing` metadata; malformed values throw with `Invalid`. Catch `LensFailure` and inspect `.failures` for details.
