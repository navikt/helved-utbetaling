plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("simulering.SimuleringKt")
}

val ktorVersion = "3.2.0"
val libVersion = "3.1.171"
val kontraktVersion = "1.0_20241213145703_7ff5f9c"

dependencies {
    implementation(project(":models"))
    implementation("no.nav.helved:auth:$libVersion")
    implementation("no.nav.helved:http:$libVersion")
    implementation("no.nav.helved:ktor:$libVersion")
    implementation("no.nav.helved:utils:$libVersion")
    implementation("no.nav.helved:ws:$libVersion")
    implementation("no.nav.helved:xml:$libVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.17.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.1")

    implementation("jakarta.xml.ws:jakarta.xml.ws-api:4.0.2")
    implementation("com.sun.xml.ws:jaxws-rt:4.0.3")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.1")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav.helved:auth-test:$libVersion")
}
