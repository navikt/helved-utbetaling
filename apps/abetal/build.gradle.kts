plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("abetal.AbetalKt")
}

val ktorVersion = "3.2.0"
val libVersion = "3.1.159"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:kafka"))

    implementation("no.nav.helved:xml:$libVersion")
    implementation("no.nav.helved:utils:$libVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.16.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    implementation("org.apache.kafka:kafka-streams:4.0.0")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    // TODO: ER det mulig å innkapsulere denne i libs?
    // vanilla producer
    implementation("org.apache.kafka:kafka-clients:4.0.0")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:ktor-test"))
}
