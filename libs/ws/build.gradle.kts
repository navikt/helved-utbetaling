val ktorVersion = "3.3.0"
val libVersion = "3.1.171"

dependencies {
    api(project(":libs:cache"))
    api(project(":libs:http"))
    api(project(":libs:utils"))

    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.19.2")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
}
