plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("peisschtappern.PeisschtappernKt")
}

val ktorVersion = "3.2.1"
val libVersion = "3.1.174"

dependencies {
    implementation(project(":libs:auth"))
    implementation(project(":libs:jdbc"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:tracing"))
    implementation(project(":libs:utils"))
    implementation("no.nav.helved:xml:$libVersion")

    implementation("org.apache.kafka:kafka-clients:4.0.0")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.17.1-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.1")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:ktor-test"))
    testImplementation(project(":libs:jdbc-test"))
    testImplementation(project(":libs:auth-test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
