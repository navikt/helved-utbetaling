dependencies {
    implementation(project(":libs:kafka"))
    implementation(project(":libs:utils"))

    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.apache.kafka:kafka-streams:4.3.1")
    implementation("org.apache.kafka:kafka-streams-test-utils:4.3.1")
    implementation(kotlin("test"))
}
