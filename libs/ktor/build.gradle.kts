val ktorVersion = "3.3.0"

dependencies {
    api(project(":libs:utils"))

    implementation("io.ktor:ktor-server-core:$ktorVersion")
}
