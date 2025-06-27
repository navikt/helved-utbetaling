plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("peisschtappern.PeisschtappernKt")
}

val ktorVersion = "3.2.0"
val libVersion = "3.1.165"

dependencies {
    implementation(project(":libs:kafka"))
    implementation("no.nav.helved:xml:$libVersion")

    implementation("no.nav.helved:auth:$libVersion")
    implementation("no.nav.helved:jdbc:$libVersion")
    implementation("no.nav.helved:utils:$libVersion")

    implementation("org.apache.kafka:kafka-clients:4.0.0")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.16.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.1")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:ktor-test"))
    testImplementation(project(":libs:jdbc-test"))
    testImplementation("no.nav.helved:auth-test:$libVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
