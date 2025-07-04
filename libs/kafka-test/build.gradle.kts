dependencies {
    implementation(project(":libs:kafka"))
    implementation(project(":libs:utils"))

    implementation("io.micrometer:micrometer-registry-prometheus:1.15.1")
    implementation("org.apache.kafka:kafka-streams:4.0.0")
    implementation("org.apache.kafka:kafka-streams-test-utils:4.0.0")
    implementation(kotlin("test"))
}
