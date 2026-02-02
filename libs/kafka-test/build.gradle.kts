dependencies {
    implementation(project(":libs:kafka"))
    implementation(project(":libs:utils"))

    implementation("io.micrometer:micrometer-registry-prometheus:1.16.2")
    implementation("org.apache.kafka:kafka-streams:4.1.1")
    implementation("org.apache.kafka:kafka-streams-test-utils:4.1.1")
    implementation(kotlin("test"))
}
