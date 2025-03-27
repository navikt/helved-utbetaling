val libVersion = "3.1.92"

dependencies {
    implementation(project(":libs:kafka"))
    implementation("no.nav.helved:utils:$libVersion")

    implementation("io.micrometer:micrometer-registry-prometheus:1.14.5")
    implementation("org.apache.kafka:kafka-streams:3.9.0")
    implementation("org.apache.kafka:kafka-streams-test-utils:3.9.0")
    implementation(kotlin("test"))
}
