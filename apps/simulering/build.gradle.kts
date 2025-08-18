plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("simulering.SimuleringKt")
}

val ktorVersion = "3.2.3"
val libVersion = "3.1.174"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:http"))
    implementation(project(":libs:auth"))
    implementation(project(":libs:ktor"))
    implementation(project(":libs:utils"))
    implementation(project(":libs:ws"))
    implementation("no.nav.helved:xml:$libVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.18.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.2")

    implementation("jakarta.xml.ws:jakarta.xml.ws-api:4.0.2")
    implementation("com.sun.xml.ws:jaxws-rt:4.0.3")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.2")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:auth-test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
