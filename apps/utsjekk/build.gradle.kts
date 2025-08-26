plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("utsjekk.UtsjekkKt")
}

val ktorVersion = "3.2.3"
val libVersion = "3.1.184"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:auth"))
    implementation(project(":libs:jdbc"))
    implementation("no.nav.helved:xml:$libVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.19.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    implementation("org.apache.kafka:kafka-clients:4.0.0")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:ktor-test"))
    testImplementation(project(":libs:jdbc-test"))
    testImplementation(project(":libs:auth-test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

