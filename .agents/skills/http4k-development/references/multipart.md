---
license: Apache-2.0
module: http4k-multipart
---

# http4k-multipart Reference

Multipart form handling — file uploads and multi-field forms.

## Core Types

```kotlin
// Data types
data class MultipartFormFile(val filename: String, val contentType: ContentType, val content: InputStream, val length: Long? = null, ...) : Closeable
data class MultipartFormField(val value: String, val headers: Headers = emptyList(), ...)
data class MultipartForm(val fields: Map<String, List<MultipartFormField>>, val files: Map<String, List<MultipartFormFile>>, val errors: List<Failure>, ...) : Closeable

// Body type
class MultipartFormBody(boundary: String) : Body, Closeable
```

## Building Multipart Requests

### With Lenses (Recommended)

```kotlin
val nameField = MultipartFormField.string().required("name")
val ageField = MultipartFormField.string().int().required("age")
val uploadFile = MultipartFormFile.required("upload")

val formLens = Body.multipartForm(Strict, nameField, ageField, uploadFile).toLens()

val request = Request(POST, "/submit").with(
    formLens of MultipartForm().with(
        nameField of "Alice",
        ageField of 30,
        uploadFile of MultipartFormFile("doc.txt", ContentType.TEXT_PLAIN, "content".byteInputStream())
    )
)
```

### With MultipartFormBody (Low-Level)

```kotlin
val file = MultipartFormFile("doc.txt", ContentType.TEXT_PLAIN, "content".byteInputStream())

val form = MultipartFormBody("boundary-123") +
    ("name" to "Alice") +
    ("file" to file)

val request = Request(POST, "/submit")
    .with(Header.CONTENT_TYPE of ContentType.MultipartFormWithBoundary(form.boundary))
    .body(form)
```

## Parsing Multipart Requests

### With Lenses

```kotlin
val formLens = Body.multipartForm(Strict, nameField, ageField, uploadFile).toLens()

val form: MultipartForm = formLens(request)
val name: String = nameField(form)
val file: MultipartFormFile = uploadFile(form)
```

### With MultipartFormBody

```kotlin
val body = MultipartFormBody.from(request)
val fieldValue = body.fieldValue("name")          // first value
val fieldValues = body.fieldValues("name")        // all values
val file = body.file("upload")                    // first file
val files = body.files("upload")                  // all files
```

## Field Lenses

```kotlin
MultipartFormField.string().required("name")
MultipartFormField.string().optional("nickname")
MultipartFormField.string().int().required("age")
MultipartFormField.string().multi.required("tags")
MultipartFormField.string().map(::MyType) { it.value }.required("custom")
```

## File Lenses

```kotlin
MultipartFormFile.required("upload")
MultipartFormFile.optional("upload")
MultipartFormFile.multi.required("uploads")
MultipartFormFile.inputStream().required("data")   // InputStream directly
```

## Validation

```kotlin
// Strict — throws LensFailure immediately on missing/invalid
val strictLens = Body.multipartForm(Strict, nameField, uploadFile).toLens()

// Feedback — collects errors into form.errors
val feedbackLens = Body.multipartForm(Feedback, nameField, uploadFile).toLens()
val form = feedbackLens(request)
if (form.errors.isNotEmpty()) { /* handle errors */ }
```

## Processing Files in Filters

```kotlin
val processedFiles = mutableListOf<String>()
val app = ServerFilters.ProcessFiles { formFile ->
    processedFiles.add(formFile.file.filename)
    formFile.file.filename  // return value becomes the field value
}.then(handler)
```

## Disk-Backed Storage for Large Uploads

```kotlin
val formLens = Body.multipartForm(
    Strict,
    nameField,
    uploadFile,
    diskThreshold = 1024,                          // bytes before writing to disk
    getDiskLocation = { DiskLocation.Temp() }      // auto-deletes on close
).toLens()
```

## Gotchas

- **Close multipart forms**: `MultipartForm` and `MultipartFormBody` implement `Closeable`. Close them to free resources and delete temp files. Use `.use {}` for automatic cleanup.
- **Streaming parts are consumed once**: Each part's `InputStream` is invalidated when the next part is read. Read or buffer immediately.
- **DiskLocation.Temp auto-deletes**: Temp files are cleaned up on `close()`. Use `DiskLocation.Permanent(dir)` to keep files.
- **Boundary must match**: When building manually, the boundary in `Content-Type` header must match the body's boundary.
- **Lens replacement semantics**: Setting a field via lens replaces all values for that key, not appends.
- **Default content type for unknown files**: Files without an explicit content-type default to `application/octet-stream`.
