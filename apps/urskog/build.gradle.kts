plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("urskog.UrskogKt")
}

val ktorVersion = "3.1.3"
val libVersion = "3.1.133"

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
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.0")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    testImplementation(kotlin("test")) 
    testImplementation(project(":libs:kafka-test"))
    testImplementation("no.nav.helved:auth-test:$libVersion")
    testImplementation("no.nav.helved:mq-test:$libVersion")
    testImplementation("org.apache.kafka:kafka-streams:4.0.0") // StreamsConfig
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
