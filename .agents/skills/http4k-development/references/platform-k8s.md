---
license: Apache-2.0
module: http4k-platform-k8s
---

# http4k-platform-k8s Reference

Kubernetes platform support — dual-port server with health checks for K8s deployments.

## K8s Server

```kotlin
// Main app on port 8000, health checks on port 8001
val server = myApp.asK8sServer(::Undertow)
server.start()

// Custom ports
val server = myApp.asK8sServer(::Undertow, port = 9000, healthPort = 9001)

// From environment variables (SERVICE_PORT, HEALTH_PORT)
val server = myApp.asK8sServer(::Undertow, env = Environment.ENV)

// Custom health app
val server = myApp.asK8sServer(::Undertow, healthApp = Health(
    checks = listOf(databaseCheck, cacheCheck)
))
```

## Health Checks

```kotlin
// Default health app provides /liveness and /readiness
Health()

// With readiness checks
Health(checks = listOf(
    ReadinessCheck("database") { /* check db connection */ Completed("ok") },
    ReadinessCheck("cache") { /* check cache */ Completed("ok") }
))
```

- `GET /liveness` — always returns `200 OK`
- `GET /readiness` — runs all checks, returns `200 OK` if all pass, `503 SERVICE_UNAVAILABLE` if any fail

## Gotchas

- **Dual-port**: Main app and health checks run on separate ports. K8s probes should target the health port.
- **Environment-driven**: `SERVICE_PORT` and `HEALTH_PORT` environment variables configure ports when using `env` parameter.
- **Liveness is always OK**: The liveness endpoint always returns 200. Use readiness checks for dependency health.
