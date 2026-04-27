val ktorVersion = "3.4.2"

dependencies {
    api(project(":models"))
    api(project(":libs:cache"))
    api(project(":libs:http"))
    api(project(":libs:utils"))

    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.20.1")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
}

// libs:ws tests share singleton SOAP/HTTP fake state (verified 2026-04-24:
// concurrent fails). Keep same_thread.
tasks.withType<Test> {
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
}
