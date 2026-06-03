---
license: Apache-2.0
module: http4k-platform-docker
---

# http4k-platform-docker Reference

Docker platform support — read Docker Swarm secrets as environment variables.

## Docker Swarm Secrets

```kotlin
// Read secrets from /run/secrets/ as environment variables
val env = Environment.fromDockerSwarmSecrets()

// Custom path and name mapping
val env = Environment.fromDockerSwarmSecrets(
    path = File("/custom/secrets").toPath(),
    mapName = { it.uppercase().replace("-", "_") }
)

// Combine with regular environment
val env = Environment.ENV overrides Environment.fromDockerSwarmSecrets()
```

## Gotchas

- **Name mapping**: Secret file names are converted to environment variable names. Default: uppercase with `-` → `_` (e.g., `db-password` → `DB_PASSWORD`).
- **Trailing whitespace trimmed**: Secret file contents are `trimEnd()`'d.
- **Missing directory**: Returns empty environment if `/run/secrets/` doesn't exist (graceful outside Docker).
