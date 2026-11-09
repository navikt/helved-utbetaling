dependencies {
    implementation(project(":libs:kafka"))
    implementation(project(":libs:utils"))

    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.apache.kafka:kafka-streams")
    implementation("org.apache.kafka:kafka-streams-test-utils")
    implementation(kotlin("test"))
}
