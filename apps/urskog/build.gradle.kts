plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("urskog.UrskogKt")
}

val ktorVersion = "3.1.2"
val libVersion = "3.1.93"

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
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.5")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    testImplementation(project(":libs:kafka-test"))
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav.helved:auth-test:$libVersion")
    testImplementation("no.nav.helved:mq-test:$libVersion")
    testImplementation(kotlin("test")) 
}
