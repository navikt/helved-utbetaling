---
license: Apache-2.0
module: http4k-config
---

# http4k-config Reference

Type-safe configuration from environment variables, property files, YAML, and JVM properties.

## Core Types

```kotlin
// Environment — immutable key-value store
val env = Environment.ENV                           // System.getenv()
val env = Environment.JVM_PROPERTIES                // -D flags
val env = Environment.EMPTY
val env = Environment.from("KEY" to "value", "KEY2" to "value2")
val env = Environment.fromResource("local.properties")
val env = Environment.fromYaml(File("config.yaml"))

// EnvironmentKey — type-safe lens for environment values
val port = EnvironmentKey.int().required("PORT")
val host = EnvironmentKey.optional("HOST")
val debug = EnvironmentKey.boolean().defaulted("DEBUG", false)
```

## Override Chain

Environments compose with `overrides`. Left side wins:

```kotlin
val env = Environment.ENV overrides
    Environment.fromResource("local.properties") overrides
    Environment.defaults(
        EnvironmentKey.int().required("PORT") of 8080
    )
```

## EnvironmentKey Types

```kotlin
EnvironmentKey.required("NAME")                     // String, throws on missing
EnvironmentKey.optional("NAME")                     // String?, null on missing
EnvironmentKey.defaulted("NAME", "default")         // String with default
EnvironmentKey.int().required("PORT")               // Int
EnvironmentKey.long().required("TIMEOUT_MS")        // Long
EnvironmentKey.boolean().required("DEBUG")           // Boolean

// Value types
EnvironmentKey.port().required("PORT")              // Port (validated <= 65535)
EnvironmentKey.host().required("HOST")              // Host (validated non-empty)
EnvironmentKey.authority().required("AUTHORITY")     // Authority (host + optional port)
EnvironmentKey.secret().required("PASSWORD")        // Secret (single-use, auto-cleared)
EnvironmentKey.timeout().required("TIMEOUT")        // Timeout (validated non-negative)

// Enum
EnvironmentKey.enum<MyEnum>().required("MODE")

// Multi-value (comma-separated by default)
EnvironmentKey.int().multi.required("PORTS")        // List<Int>

// Delegated property
val MY_PORT by EnvironmentKey.int().of().required()
val value = MY_PORT(env)
```

## Injecting Values

```kotlin
val port = EnvironmentKey.int().required("PORT")
val env = Environment.EMPTY.with(port of 8080)
```

## Composite Keys

```kotlin
data class DbConfig(val host: String, val port: Int, val name: String?)

val dbConfig = EnvironmentKey.composite {
    DbConfig(
        required("DB_HOST")(it),
        int().required("DB_PORT")(it),
        optional("DB_NAME")(it)
    )
}

val config: DbConfig = dbConfig(env)
```

## Kubernetes Helpers

```kotlin
// Reads <SERVICE>_SERVICE_PORT env var set by K8s
val uri = EnvironmentKey.k8s.serviceUriFor("myservice")(env)
// With MYSERVICE_SERVICE_PORT=8000 → Uri.of("http://myservice:8000/")
// Ports 80/443 are omitted from the authority
```

## Secret

Single-use wrapper for sensitive values. Bytes are zeroed after first read.

```kotlin
val secret = EnvironmentKey.secret().required("PASSWORD")
val env = Environment.defaults(secret of Secret("hunter2"))

secret(env).use { password ->
    // password is "hunter2"
}
// Second .use() throws IllegalStateException
```

**Gotcha**: `secret.toString()` returns `"Secret(hashcode = ...)"`, NOT the value. Use `.use {}` to access.

## YAML Configuration

```kotlin
val env = Environment.fromYaml(File("config.yaml"))
// Nested keys are flattened: {child: {string: "world"}} → child.string=world
env["child.string"]  // "world"
```

## Gotchas

- **Key names are case-insensitive**: `FOO`, `foo`, `Foo` all resolve to the same key. Underscores and dots normalize to hyphens: `FIRST_NAME` → `first-name`.
- **Multi-value separator**: Default is comma. Values are trimmed. Use `MapEnvironment.from(props, separator = ";")` for custom separators.
- **Secret is single-use**: Calling `.use {}` twice throws `IllegalStateException("Cannot read a secret more than once")`.
- **Port validation**: `Port(65536)` throws `IllegalArgumentException`.
- **Timeout validation**: Negative durations throw `IllegalArgumentException`.
