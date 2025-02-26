plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("urskog.UrskogKt")
}

val ktorVersion = "3.1.0"
val libVersion = "3.1.36"

dependencies {
    implementation("no.nav.helved:auth:$libVersion")
    implementation("no.nav.helved:kafka:$libVersion")
    implementation("no.nav.helved:mq:$libVersion")
    implementation("no.nav.helved:utils:$libVersion")
    implementation("no.nav.helved:ws:$libVersion")

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
