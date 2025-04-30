plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("urskog.UrskogKt")
}

val ktorVersion = "3.1.2"
val libVersion = "3.1.97"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:kafka"))

    implementation("no.nav.helved:utils:$libVersion")
    implementation("no.nav.helved:auth:$libVersion")
    implementation("no.nav.helved:mq:$libVersion")
    implementation("no.nav.helved:ws:$libVersion")
    implementation("no.nav.helved:xml:$libVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.15.0-alpha")
    implementation("io.micrometer:micrometer-registry-otlp:1.14.5")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    testImplementation(kotlin("test")) 
    testImplementation(project(":libs:kafka-test"))
    testImplementation("no.nav.helved:auth-test:$libVersion")
    testImplementation("no.nav.helved:mq-test:$libVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
