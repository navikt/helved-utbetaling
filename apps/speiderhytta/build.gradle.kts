plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("speiderhytta.SpeiderhyttaKt")
}

val ktorVersion = "3.4.1"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:http"))
    implementation(project(":libs:jdbc"))
    implementation(project(":libs:ktor"))
    implementation(project(":libs:utils"))

    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")

    implementation("io.micrometer:micrometer-registry-prometheus:1.16.2")

    // YAML parsing for slo.yml
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.2")

    // JWT signing for GitHub App auth (RS256)
    implementation("com.auth0:java-jwt:4.5.0")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.24.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:jdbc-test"))
    testImplementation(project(":libs:ktor-test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

// speiderhytta tests share a single Postgres schema and the DAO tests rely on
// sequential row inserts/queries (verified 2026-04-24: concurrent mode causes
// row-count assertion failures). Keep same_thread.
tasks.withType<Test> {
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
}
