plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("vedskiva.VedskivaKt")
}

val ktorVersion = "3.4.0"
val libVersion = "3.1.219"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:auth"))
    implementation(project(":libs:jdbc"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:ktor"))
    implementation(project(":libs:utils"))
    implementation("no.nav.helved:xml:$libVersion")

    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")

    implementation("io.micrometer:micrometer-registry-prometheus:1.16.2")
    implementation("org.apache.kafka:kafka-clients:4.1.1")
    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.24.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.1")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:jdbc-test"))
    testImplementation(project(":libs:ktor-test"))
    testImplementation(project(":libs:auth-test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
