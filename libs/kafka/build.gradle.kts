val libVersion = "3.1.187"

dependencies {
    implementation(project(":libs:utils"))
    implementation("no.nav.helved:xml:$libVersion")

    implementation("io.micrometer:micrometer-registry-prometheus:1.15.3")
    implementation("org.apache.kafka:kafka-streams:4.0.0")

    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.0")

    testImplementation(kotlin("test"))
    testImplementation("org.apache.kafka:kafka-streams-test-utils:4.0.0")
}
