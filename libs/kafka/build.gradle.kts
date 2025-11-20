val libVersion = "3.1.215"

dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":libs:tracing"))
    implementation("no.nav.helved:xml:$libVersion")

    implementation("io.micrometer:micrometer-registry-prometheus:1.16.0")
    api("org.apache.kafka:kafka-streams:4.1.1")

    implementation("ch.qos.logback:logback-classic:1.5.21")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.1")

    testImplementation(kotlin("test"))
    testImplementation("org.apache.kafka:kafka-streams-test-utils:4.1.1")
}
