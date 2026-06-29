val ktorVersion = "3.5.1"

dependencies {
    api(project(":libs:utils"))
    api("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-client-cio:$ktorVersion")
    api("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    api("io.ktor:ktor-client-logging:$ktorVersion")
    testImplementation(kotlin("test"))
}
