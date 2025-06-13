plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("utsjekk.UtsjekkKt")
}

val ktorVersion = "3.2.0"
val libVersion = "3.1.131"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:kafka"))

    implementation("no.nav.helved:auth:$libVersion")
    implementation("no.nav.helved:jdbc:$libVersion")
    implementation("no.nav.helved:job:$libVersion")
    implementation("no.nav.helved:xml:$libVersion")
    implementation("no.nav.helved:task:$libVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.16.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    implementation("org.apache.kafka:kafka-clients:4.0.0")

    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:jdbc-test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("no.nav.helved:auth-test:$libVersion")
}

