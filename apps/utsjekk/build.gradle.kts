plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("oppdrag.AppKt")
}

val ktorVersion = "2.3.9"

dependencies {
    implementation(project(":libs:auth"))
    implementation(project(":libs:postgres"))

    implementation("no.nav.utsjekk.kontrakter:oppdrag:1.0_20240408113510_4a2db84")

    // server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.4")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    // json
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")

    testImplementation(kotlin("test"))
    testImplementation("org.testcontainers:postgresql:1.19.7")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation(project(":libs:auth-test"))
}
