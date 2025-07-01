val libVersion = "3.1.165"

dependencies {
    api("no.nav.helved:utils:$libVersion")

    // API (core tracing functionality)
    api("io.opentelemetry:opentelemetry-api:1.51.0")

    // SDK (needed for creating spans if no external collector is set)
    // api("io.opentelemetry:opentelemetry-sdk:1.46.0")

    // OTLP Exporter (to send traces to an OpenTelemetry Collector)
    // api("io.opentelemetry:opentelemetry-exporter-otlp:1.46.0")

    // Context Propagation (for managing spans across threads/coroutines)
    api("io.opentelemetry:opentelemetry-context:1.51.0")
}
