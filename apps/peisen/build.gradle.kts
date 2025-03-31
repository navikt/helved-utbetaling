plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("peisen.PeisenKt")
}

val ktorVersion = "3.1.2"
val libVersion = "3.1.93"

dependencies {
    implementation(project(":libs:kafka"))

    implementation("no.nav.helved:auth:$libVersion")
    implementation("no.nav.helved:jdbc:$libVersion")
    implementation("no.nav.helved:utils:$libVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.5")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation("org.apache.kafka:kafka-streams:3.9.0") // intercept StreamsBuilder
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
