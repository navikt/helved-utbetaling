
dependencies {
    api(project(":libs:utils"))

    // API (core tracing functionality)
    api("io.opentelemetry:opentelemetry-api")

    // Context Propagation (for managing spans across threads/coroutines)
    api("io.opentelemetry:opentelemetry-context")
}
