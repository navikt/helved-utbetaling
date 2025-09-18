dependencies {
    implementation(project(":libs:kafka"))
    implementation(project(":libs:utils"))

    implementation("io.micrometer:micrometer-registry-prometheus:1.15.3")
    implementation("org.apache.kafka:kafka-streams:4.1.0")
    implementation("org.apache.kafka:kafka-streams-test-utils:4.1.0")
    implementation(kotlin("test"))
}
