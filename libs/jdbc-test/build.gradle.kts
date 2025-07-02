dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":libs:jdbc"))
    implementation(kotlin("test"))
    api("org.testcontainers:postgresql:1.21.3")
}
