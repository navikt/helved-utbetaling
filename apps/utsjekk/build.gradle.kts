plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("utsjekk.UtsjekkKt")
}

val ktorVersion = "3.5.0"
val libVersion = "3.1.250"
val nettyVersion = "4.2.13.Final"
val jackson3Version = "3.1.1"

dependencies {
    // Trivy 2026-05-18: Ktor 3.4.2 still resolves Netty 4.2.9.Final, and
    // logstash-logback-encoder 9.0 pulls Jackson 3.0.1. Override those
    // transitive versions here until the upstreams move past the fixed ranges.
    implementation(platform("io.netty:netty-bom:$nettyVersion"))
    implementation("tools.jackson.core:jackson-core:$jackson3Version")
    implementation("tools.jackson.core:jackson-databind:$jackson3Version")

    implementation(project(":models"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:ktor"))
    implementation(project(":libs:auth"))
    implementation(project(":libs:jdbc"))
    implementation("no.nav.helved:xml:$libVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.24.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("org.apache.kafka:kafka-clients:4.2.0")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-double-receive:${ktorVersion}")
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:ktor-test"))
    testImplementation(project(":libs:jdbc-test"))
    testImplementation(project(":libs:auth-test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

