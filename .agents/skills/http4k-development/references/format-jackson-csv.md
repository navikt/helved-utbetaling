---
license: Apache-2.0
module: http4k-format-jackson-csv
---

# http4k-format-jackson-csv Reference

CSV format module backed by Jackson's `jackson-dataformat-csv`. Works with lists of typed objects and CSV schemas.

## Construction

```kotlin
// Default singleton (includes withStandardMappings())
val csv = JacksonCsv

// Custom configuration — same pattern as other Jackson modules
```

## Schema

CSV requires a schema defining columns. Generate automatically from a data class:

```kotlin
val schema = JacksonCsv.defaultSchema<MyRecord>()  // CsvSchema with headers from class fields
```

## Lens Integration

CSV lenses work with `List<T>` since CSV is inherently tabular:

```kotlin
// Typed body lens — note List<T>, not T
val lens = Body.auto<MyRecord>(schema).toLens()
val records: List<MyRecord> = lens(request)
val response = Response(OK).with(lens of listOf(record1, record2))

// Convenience extensions
val response = Response(OK).csv(listOf(record1, record2))
val records: List<MyRecord> = request.csv<MyRecord>()

// BiDiMapping
val mapping = JacksonCsv.asBiDiMapping<MyRecord>(schema)
```

## Read/Write Functions

For direct conversion without HTTP:

```kotlin
// Write objects to CSV string
val csvString: String = JacksonCsv.writeCsv(listOf(record1, record2), schema)

// Read CSV string to objects
val records: List<MyRecord> = JacksonCsv.readCsv<MyRecord>(csvString, schema)

// Get reusable reader/writer functions
val writer: (List<MyRecord>) -> String = JacksonCsv.writerFor<MyRecord>(schema)
val reader: (String) -> List<MyRecord> = JacksonCsv.readerFor<MyRecord>(schema)
```

## Column Ordering

Control CSV column order with `@JsonPropertyOrder`:

```kotlin
@JsonPropertyOrder("name", "age", "email")
data class Person(val name: String, val age: Int, val email: String)
```

## Gotchas

- **Content type is `TEXT_CSV`**: Default content type is `text/csv`.
- **Always List\<T\>**: CSV body lenses serialize/deserialize `List<T>`, not single objects.
- **Schema includes headers**: `defaultSchema<T>()` generates a schema with column headers. The first CSV row will be headers.
- **Empty lists produce headers only**: Writing an empty list outputs just the header row.
- **Column order matters**: Use `@JsonPropertyOrder` to ensure consistent column ordering across serialization/deserialization.
- **No JSON node manipulation**: Extends `AutoMarshalling` directly — CSV-only operations.
