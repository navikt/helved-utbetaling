plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("abetal.AppKt")
}

val ktorVersion = "3.0.3"
val libVersion = "3.0.52"
val kontraktVersion = "1.0_20241213145703_7ff5f9c"

dependencies {
    implementation("no.nav.helved:utils:$libVersion")
    implementation("no.nav.helved:kafka:$libVersion")
    implementation("org.apache.kafka:kafka-clients:3.9.0")
    implementation("no.nav.utsjekk.kontrakter:oppdrag:$kontraktVersion")
    implementation("no.nav.utsjekk.kontrakter:iverksett:$kontraktVersion")
    implementation("no.nav.utsjekk.kontrakter:felles:$kontraktVersion")

    implementation("io.ktor:ktor-server-double-receive:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.2")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    testImplementation(kotlin("test"))
    testImplementation("no.nav.helved:kafka-test:$libVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
}
