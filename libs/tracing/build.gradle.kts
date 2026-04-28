
dependencies {
    api(project(":libs:utils"))

    // API (core tracing functionality)
    api("io.opentelemetry:opentelemetry-api:1.59.0")

    // Context Propagation (for managing spans across threads/coroutines)
    api("io.opentelemetry:opentelemetry-context:1.59.0")

    implementation("io.opentelemetry:opentelemetry-sdk:1.59.0")

    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.59.0")

    testImplementation(kotlin("test"))
}
