---
name: http4k-development
description: This skill should be used when the user needs guidance on any of the 200+ http4k modules - it contains patterns and API usage examples for all http4k technologies.
---

# http4k

http4k is a lightweight Kotlin HTTP toolkit built on the "Server as a Function" principle, where `HttpHandler` is `(Request) -> Response`. Everything — servers, clients, routing, testing — composes through this function type.

## Dependency Detection

Before loading references, detect which http4k modules the user's project depends on:

1. Read `build.gradle.kts`, `build.gradle`, or `pom.xml` in the project root (and subproject build files)
2. Look for dependencies matching `org.http4k:http4k-*`
3. Only load reference files from `references/` that match detected modules
4. Always load `references/core.md` — it covers the foundation used by all other modules

If no build file is found, ask the user which modules they use.

## Loading References

For each detected dependency `org.http4k:http4k-{module}`, load `references/{module}.md` if it exists. For example, `http4k-server-undertow` maps to `references/server-undertow.md`.

## http4k-connect API Call Style

http4k-connect modules use **extension functions** on client interfaces, not the `invoke()` operator directly. Actions are called as camelCase methods on the client:

```kotlin
// CORRECT — extension function style
val buckets = s3.listBuckets().successValue()
s3Bucket.putObject(key, body).successValue()
dynamo.createTable(table, keySchema).successValue()
sqs.sendMessage(queueUrl, "Hello").successValue()

// WRONG — do NOT use the invoke operator with action classes
val buckets = s3(ListBuckets()).successValue()        // ✗
sqs(SendMessage(queueUrl, "Hello")).successValue()     // ✗
```

All connect actions return `Result<T, RemoteFailure>` monads.
