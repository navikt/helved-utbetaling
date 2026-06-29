plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("snickerboa.SnickerboaKt")
}

val ktorVersion = "3.5.1"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:kotlinx"))
    implementation(project(":libs:utils"))

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.29.0-alpha")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:${ktorVersion}")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.0")
    implementation("org.apache.kafka:kafka-clients:4.3.0")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation(project(":libs:ktor-test"))
    testImplementation("io.ktor:ktor-server-test-host:${ktorVersion}")
}
