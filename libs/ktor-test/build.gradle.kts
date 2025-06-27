val libVersion = "3.1.165"
val ktorVersion = "3.2.0"

dependencies {
    api("no.nav.helved:utils:$libVersion")
    api("io.ktor:ktor-client-cio:$ktorVersion")
    api("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-server-netty:$ktorVersion")
    api("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-serialization-jackson:$ktorVersion")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.1")
}
