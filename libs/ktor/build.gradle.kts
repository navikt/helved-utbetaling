val ktorVersion = "3.6.1"

dependencies {
    api(project(":libs:utils"))
    implementation("io.ktor:ktor-server-core:$ktorVersion")
}
