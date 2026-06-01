---
license: Apache-2.0
module: http4k-format-dataframe
---

# http4k-format-dataframe Reference

Tabular data integration using Kotlin DataFrame. Parses CSV and JSON HTTP bodies into DataFrame instances.

## Construction

DataFrameFormat is a sealed interface with CSV and JSON implementations:

```kotlin
// CSV format with options
val csvFormat = DataFrameFormat.CSV(
    delimiter = ',',
    header = listOf("name", "age", "email"),
    colTypes = mapOf("age" to ColType.Int),
    skipLines = 0,
    readLines = null  // read all
)

// JSON format with options
val jsonFormat = DataFrameFormat.JSON(
    header = listOf("name", "age"),
    keyValuePaths = emptyList(),
    typeClashTactic = TypeClashTactic.ARRAY_AND_VALUE_COLUMNS
)

// Defaults (no options)
val csv = DataFrameFormat.CSV()
val json = DataFrameFormat.JSON()
```

## Lens Integration

```kotlin
// Create lens from format
val csvLens = Body.dataFrame(DataFrameFormat.CSV()).toLens()
val df: AnyFrame = csvLens(request)

// Convenience extensions
val df = request.dataFrameCsv()     // CSV with auto-cast
val df = request.dataFrameJson()    // JSON with auto-cast
val df = request.dataFrame(DataFrameFormat.CSV(delimiter = '\t'))  // custom format
```

## Typed DataFrames

Cast a generic DataFrame to a typed one:

```kotlin
val df = request.dataFrameCsv().cast<Person>()
val names: List<String> = df.name.toList()
```

## CSV Configuration Options

| Option | Type | Description |
|---|---|---|
| `delimiter` | `Char` | Column delimiter (default `,`) |
| `header` | `List<String>` | Column names (default: from first row) |
| `colTypes` | `Map<String, ColType>` | Force column types |
| `columnWidths` | `List<IntRange>` | Fixed-width column definitions |
| `skipLines` | `Int` | Lines to skip before header |
| `readLines` | `Int?` | Max lines to read (`null` = all) |
| `parserOptions` | `ParserOptions?` | Locale, null strings, etc. |
| `charset` | `Charset` | Character encoding (default `UTF_8`) |
| `compression` | `Compression<*>` | Input compression format |

## Gotchas

- **No serialization**: DataFrame format is read-only — it parses HTTP bodies into DataFrames but does not serialize DataFrames back to HTTP responses.
- **Not an AutoMarshalling implementation**: DataFrame does not extend `AutoMarshalling` and does not participate in the `Body.auto<T>()` pattern. Use `Body.dataFrame()` instead.
- **Auto-cast with convenience methods**: `dataFrameCsv()` and `dataFrameJson()` automatically call `.cast<T>()` — use `.dataFrame(format)` for untyped access.
- **CSV headers from first row**: By default, the first CSV row is treated as column headers. Pass explicit `header` to override.
