plugins {
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":libs:kotlinx"))
    implementation(project(":libs:tracing"))
    implementation("no.nav.helved:xml:3.1.252")

    implementation("io.micrometer:micrometer-registry-prometheus")
    api("org.apache.kafka:kafka-streams")
    implementation("ch.qos.logback:logback-classic")

    testImplementation(kotlin("test"))
    testImplementation("org.apache.kafka:kafka-streams-test-utils")
}

// Kafka topology tests share Kafka Streams test driver state (verified
// 2026-04-24: concurrent fails). Keep same_thread.
tasks.withType<Test> {
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
}
