plugins {
    id("io.ktor.plugin")
    kotlin("plugin.serialization")
}

application {
    mainClass.set("vedskiva.VedskivaKt")
}

val ktorVersion = "3.5.1"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:auth"))
    implementation(project(":libs:jdbc"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:kotlinx"))
    implementation(project(":libs:ktor"))
    implementation(project(":libs:utils"))
    implementation("no.nav.helved:xml:3.1.252")

    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.2")
    implementation("org.apache.kafka:kafka-clients:4.3.0")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:jdbc-test"))
    testImplementation(project(":libs:ktor-test"))
    testImplementation(project(":libs:auth-test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
