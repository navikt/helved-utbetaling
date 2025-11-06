plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("peisschtappern.PeisschtappernKt")
}

val ktorVersion = "3.3.2"
val libVersion = "3.1.209"

dependencies {
    implementation(project(":libs:auth"))
    implementation(project(":libs:jdbc"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:tracing"))
    implementation(project(":libs:utils"))
    implementation(project(":models"))
    implementation("no.nav.helved:xml:$libVersion")

    implementation("org.apache.kafka:kafka-clients:4.1.0")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.19.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.5")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.1")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:ktor-test"))
    testImplementation(project(":libs:jdbc-test"))
    testImplementation(project(":libs:auth-test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}

