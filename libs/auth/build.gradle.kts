plugins {
    kotlin("plugin.serialization")
}

val ktorVersion = "3.5.1"

dependencies {
    api(project(":libs:cache"))
    api(project(":libs:http"))
    api(project(":libs:utils"))

    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
    api("io.ktor:ktor-server-auth:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:auth-test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
}
