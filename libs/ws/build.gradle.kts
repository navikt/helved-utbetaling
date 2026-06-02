plugins {
    kotlin("plugin.serialization")
}

val ktorVersion = "3.5.0"

dependencies {
    api(project(":models"))
    api(project(":libs:cache"))
    api(project(":libs:http"))
    api(project(":libs:utils"))

    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    // Legacy: kept for apps/simulering (deprecated), remove when it's deleted
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.20.1")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
}

// libs:ws tests share singleton SOAP/HTTP fake state (verified 2026-04-24:
// concurrent fails). Keep same_thread.
tasks.withType<Test> {
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
}
