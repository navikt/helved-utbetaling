plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("statistikkern.StatistikkernKt")
}

val ktorVersion = "3.4.1"
val libVersion = "3.1.232"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:utils"))
    implementation(project(":libs:kafka"))

    implementation("no.nav.helved:xml:$libVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.24.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.0")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
    implementation("io.ktor:ktor-server-netty:${ktorVersion}")
    implementation("io.ktor:ktor-server-metrics-micrometer:${ktorVersion}")
    implementation("com.google.cloud:google-cloud-bigquery:2.60.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.2")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("org.apache.kafka:kafka-clients:4.2.0")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:ktor-test"))
    testImplementation("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
    testImplementation("io.ktor:ktor-server-test-host:${ktorVersion}")
    testImplementation("org.testcontainers:gcloud:1.21.4")

}
