plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("oppdrag.AppKt")
}

val ktorVersion = "2.3.12"
val libVersion = "0.1.90"

dependencies {
    implementation("no.nav.helved:auth:$libVersion")
    implementation("no.nav.helved:jdbc:$libVersion")
    implementation("no.nav.helved:job:$libVersion")

    implementation("no.nav.utsjekk.kontrakter:oppdrag:1.0_20240821103442_e26671e")
    implementation("no.nav.utsjekk.kontrakter:iverksett:1.0_20240820142241_1f3f212")
    implementation("no.nav.utsjekk.kontrakter:felles:1.0_20240820142241_1f3f212")

    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.10")
    implementation("io.getunleash:unleash-client-java:9.2.4")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.13.3")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("no.nav.helved:auth-test:$libVersion")
    testImplementation("no.nav.helved:jdbc-test:$libVersion")
}
