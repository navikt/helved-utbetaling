val ktorVersion = "3.5.1"

dependencies {
    api(project(":models"))
    api(project(":libs:cache"))
    api(project(":libs:http"))
    api(project(":libs:kotlinx"))
    api(project(":libs:utils"))

    implementation("io.github.pdvrieze.xmlutil:serialization-jvm:1.0.0-rc2")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
}

// libs:ws tests share singleton SOAP/HTTP fake state (verified 2026-04-24:
// concurrent fails). Keep same_thread.
tasks.withType<Test> {
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
}
