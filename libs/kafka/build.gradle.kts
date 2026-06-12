plugins {
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":libs:tracing"))
    implementation("no.nav.helved:xml:3.1.252")

    implementation("io.micrometer:micrometer-registry-prometheus:1.16.2")
    api("org.apache.kafka:kafka-streams:4.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    implementation("ch.qos.logback:logback-classic:1.5.22")

    testImplementation(kotlin("test"))
    testImplementation("org.apache.kafka:kafka-streams-test-utils:4.3.0")
}

// Kafka topology tests share Kafka Streams test driver state (verified
// 2026-04-24: concurrent fails). Keep same_thread.
tasks.withType<Test> {
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
}
