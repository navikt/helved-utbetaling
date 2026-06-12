plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("statistikkern.StatistikkernKt")
}

val ktorVersion = "3.4.2"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:utils"))
    implementation(project(":libs:kafka"))

    implementation("no.nav.helved:xml:3.1.252")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.24.0-alpha")
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.0")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
    implementation("io.ktor:ktor-server-netty:${ktorVersion}")
    implementation("io.ktor:ktor-server-metrics-micrometer:${ktorVersion}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("com.google.cloud:google-cloud-bigquery:2.66.0")
    implementation("org.apache.kafka:kafka-clients:4.3.0")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:ktor-test"))
    testImplementation("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
    testImplementation("io.ktor:ktor-server-test-host:${ktorVersion}")
    testImplementation("org.testcontainers:gcloud:1.21.4")

}
