plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("urskog.UrskogKt")
}

val ktorVersion = "3.1.1"
val libVersion = "3.1.47"

dependencies {
    implementation(project(":models"))
    implementation("no.nav.helved:auth:$libVersion")
    implementation("no.nav.helved:kafka:3.1.51")
    implementation("no.nav.helved:mq:$libVersion")
    implementation("no.nav.helved:utils:$libVersion")
    implementation("no.nav.helved:ws:$libVersion")

    // vanilla producer
    implementation("org.apache.kafka:kafka-clients:3.9.0")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.3")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav.helved:kafka-test:3.1.51")
    testImplementation("no.nav.helved:mq-test:$libVersion")
    testImplementation(kotlin("test")) 
}
