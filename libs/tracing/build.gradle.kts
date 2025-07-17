val libVersion = "3.1.171"

dependencies {
    api(project(":libs:utils"))

    // API (core tracing functionality)
    api("io.opentelemetry:opentelemetry-api:1.52.0")

    // Context Propagation (for managing spans across threads/coroutines)
    api("io.opentelemetry:opentelemetry-context:1.52.0")
}
