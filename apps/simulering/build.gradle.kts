plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("simulering.SimuleringKt")
}

val ktorVersion = "3.4.2"
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
    implementation(project(":libs:http"))
    implementation(project(":libs:auth"))
    implementation(project(":libs:ktor"))
    implementation(project(":libs:utils"))
    implementation(project(":libs:ws"))
    implementation("no.nav.helved:xml:$libVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.24.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.2")

    implementation("jakarta.xml.ws:jakarta.xml.ws-api:4.0.3")
    implementation("com.sun.xml.ws:jaxws-rt:4.0.3")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.2")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:auth-test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
