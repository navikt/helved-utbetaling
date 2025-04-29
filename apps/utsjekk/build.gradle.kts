plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("utsjekk.AppKt")
}

val ktorVersion = "3.1.2"
val libVersion = "3.1.93"
val kontraktVersion = "1.0_20250331111546_5f5a1ac"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:kafka"))

    implementation("no.nav.helved:auth:$libVersion")
    implementation("no.nav.helved:jdbc:$libVersion")
    implementation("no.nav.helved:job:$libVersion")
    implementation("no.nav.helved:xml:$libVersion")
    implementation("no.nav.helved:task:$libVersion")

    implementation("no.nav.utsjekk.kontrakter:oppdrag:$kontraktVersion")
    implementation("no.nav.utsjekk.kontrakter:iverksett:$kontraktVersion")
    implementation("no.nav.utsjekk.kontrakter:felles:$kontraktVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.15.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    implementation("org.apache.kafka:kafka-clients:4.0.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.20")

    implementation("io.ktor:ktor-server-double-receive:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("no.nav.helved:auth-test:$libVersion")
    testImplementation("no.nav.helved:jdbc-test:$libVersion")
}
