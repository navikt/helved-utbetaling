val ktorVersion = "3.5.1"

dependencies {
    api(project(":libs:utils"))
    implementation("io.ktor:ktor-server-core:$ktorVersion")
}
