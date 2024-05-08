
val ktorVersion = "2.3.11"

dependencies {
    api(project(":libs:utils"))
    api(project(":libs:http"))

    api("io.ktor:ktor-client-auth:$ktorVersion")
    api("io.ktor:ktor-server-auth:$ktorVersion")
    api("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0")
}
