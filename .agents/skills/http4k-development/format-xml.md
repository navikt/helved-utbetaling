---
license: Apache-2.0
module: http4k-format-xml
---

# http4k-format-xml Reference

XML format module using `org.json.XML` for parsing and `javax.xml` for DOM manipulation. Works with W3C `Document` objects and provides XXE-safe parsing by default.

## Construction

```kotlin
// Singleton — no configuration needed
val xml = Xml
```

## Lens Integration

```kotlin
// Document body lens
val xmlLens = Body.xml().toLens()
val doc: Document = xmlLens(request)
val response = Response(OK).with(xmlLens of doc)

// String-to-Document lens on any string spec
val queryXml = Query.string().xml().required("data")

// BiDiMapping
val mapping = Xml.asBiDiMapping()  // BiDiMapping<String, Document>
```

## XML ↔ JSON Conversion

```kotlin
// Parse XML to Gson JsonElement (for JSON-based processing)
val json: JsonElement = xmlString.asXmlToJsonElement()
```

## Document Manipulation

```kotlin
// Parse XML string to W3C Document
val doc: Document = xmlString.asXmlDocument()

// With custom parser configuration
val doc = xmlString.asXmlDocument { dbf ->
    dbf.setFeature("http://example.com/feature", true)
}

// Serialize Document back to XML string
val xmlString: String = doc.asXmlString()
```

## Security Configuration

The default XML parser disables dangerous features to prevent XXE attacks:

```kotlin
// defaultXmlParsingConfig applies:
// - Disables external general entities
// - Disables external parameter entities
// - Disables external DTD loading
```

## Gotchas

- **Not an AutoMarshalling implementation**: `Xml` does not provide `Body.auto<T>()`. It works with W3C `Document` objects, not typed Kotlin classes. For typed XML marshalling, use `http4k-format-jackson-xml` instead.
- **XXE protection by default**: The default parser configuration blocks external entity resolution. Override with a custom `XmlParsingConfig` only if you trust the XML source.
- **Uses Gson internally**: XML-to-JSON conversion relies on Gson for the `JsonElement` representation.
- **Content type is `APPLICATION_XML`**: Default content type is `application/xml`.
- **No automatic number conversion**: Decimal strings in XML remain as strings — they are not automatically converted to numbers during parsing.
