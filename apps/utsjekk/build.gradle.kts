plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("oppdrag.AppKt")
}

val ktorVersion = "2.3.11"
val libVersion = "0.1.21"

dependencies {
    implementation("no.nav.helved:auth:$libVersion")
    implementation("no.nav.helved:job:$libVersion")
    implementation("no.nav.helved:postgres:$libVersion")
    implementation("no.nav.helved:task:$libVersion")

    implementation("no.nav.utsjekk.kontrakter:oppdrag:1.0_20240606152736_ac08381")

    // server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.13.0")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    // json
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")

    testImplementation(kotlin("test"))
    testImplementation("org.testcontainers:postgresql:1.19.8")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("no.nav.helved:auth-test:$libVersion")
}
