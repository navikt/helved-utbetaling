dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":libs:jdbc"))
    implementation(kotlin("test"))
    api("org.testcontainers:testcontainers-postgresql:2.0.3")
}
