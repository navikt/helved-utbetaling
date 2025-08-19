plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("urskog.UrskogKt")
}

val ktorVersion = "3.2.3"
val libVersion = "3.1.174"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:auth"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:mq"))
    implementation(project(":libs:utils"))
    implementation(project(":libs:ws"))

    implementation("no.nav.helved:xml:$libVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.19.0-alpha")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.2")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    testImplementation(kotlin("test")) 
    testImplementation(project(":libs:auth-test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:ktor-test"))
    testImplementation(project(":libs:mq-test"))
    testImplementation("org.apache.kafka:kafka-streams:4.0.0") // StreamsConfig
}
