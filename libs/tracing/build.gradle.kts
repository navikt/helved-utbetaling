
dependencies {
    api(project(":libs:utils"))

    // API (core tracing functionality)
    api("io.opentelemetry:opentelemetry-api:1.56.0")

    // Context Propagation (for managing spans across threads/coroutines)
    api("io.opentelemetry:opentelemetry-context:1.56.0")
}
