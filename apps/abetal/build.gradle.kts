plugins {
    id("com.gradleup.shadow")
    application
    kotlin("plugin.serialization")
}

application {
    mainClass.set("abetal.AbetalKt")
}

tasks.register("buildFatJar") {
    dependsOn(tasks.shadowJar)
}

val http4kVersion = "6.61.0.0"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:kotlinx"))
    implementation(project(":libs:jwt"))
    implementation(project(":libs:utils"))
    implementation("no.nav.helved:xml:3.1.252")

    implementation("org.apache.kafka:kafka-streams:4.3.1")
    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-ops-micrometer:$http4kVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.apache.kafka:kafka-clients:4.3.1")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:auth-test"))
}

// abetal tests share a single TestRuntime (singleton StreamsMock + topics) and
// each test asserts topics are empty in @AfterEach. Class-concurrency hangs the
// build (verified 2026-04-24). Keep same_thread.
tasks.withType<Test> {
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
}
