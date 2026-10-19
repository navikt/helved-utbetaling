plugins {
    id("com.gradleup.shadow")
    application
    kotlin("plugin.serialization")
}

application {
    mainClass.set("peisschtappern.PeisschtappernKt")
}

tasks.register("buildFatJar") {
    dependsOn(tasks.shadowJar)
}

val http4kVersion = "6.61.0.0"
val ktorVersion = "3.6.0"
val libVersion = "3.1.252"

dependencies {
    implementation(project(":libs:jwt"))
    implementation(project(":libs:jdbc"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:kotlinx"))
    implementation(project(":libs:tracing"))
    implementation(project(":libs:utils"))
    implementation(project(":models"))
    implementation("no.nav.helved:xml:$libVersion")
    implementation("io.github.pdvrieze.xmlutil:serialization-jvm:0.91.3")
    implementation("com.google.cloud:google-cloud-logging:3.39.0")

    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-ops-micrometer:$http4kVersion")

    implementation("org.apache.kafka:kafka-clients:4.3.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.2")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:jdbc-test"))
    testImplementation(project(":libs:auth-test"))
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
}

// peisschtappern tests share a single TestRuntime (Kafka topology that consumes
// every topic into shared DAOs). Tests rely on sequential execution to assert
// state on the singleton DAO (verified 2026-04-24: concurrent fails). Keep same_thread.
tasks.withType<Test> {
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
}
