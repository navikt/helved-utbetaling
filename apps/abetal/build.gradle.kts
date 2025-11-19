plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("abetal.AbetalKt")
}

val ktorVersion = "3.3.2"
val libVersion = "3.1.215"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:utils"))

    implementation("no.nav.helved:xml:$libVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.21.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("org.apache.kafka:kafka-streams:4.1.1")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.1")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    // TODO: ER det mulig å innkapsulere denne i libs?
    // vanilla producer
    implementation("org.apache.kafka:kafka-clients:4.1.1")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:ktor-test"))
}
