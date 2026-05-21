val ktorVersion = "3.4.2"

dependencies {
    api(project(":libs:utils"))

    implementation("io.ktor:ktor-server-core:$ktorVersion")
}
