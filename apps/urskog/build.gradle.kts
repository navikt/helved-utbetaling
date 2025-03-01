plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("urskog.UrskogKt")
}

val ktorVersion = "3.1.1"
val libVersion = "3.1.42"

dependencies {
    implementation(project(":models"))
    implementation("no.nav.helved:auth:$libVersion")
    implementation("no.nav.helved:kafka:$libVersion")
    implementation("no.nav.helved:mq:$libVersion")
    implementation("no.nav.helved:utils:$libVersion")
    implementation("no.nav.helved:ws:$libVersion")

    // vanilla producer
    implementation("org.apache.kafka:kafka-clients:3.9.0")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.3")
    // implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav.helved:kafka-test:$libVersion")
    testImplementation("no.nav.helved:mq-test:$libVersion")
    testImplementation(kotlin("test")) 
}
