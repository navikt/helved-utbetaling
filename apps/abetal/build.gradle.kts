plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("abetal.AbetalKt")
}

val ktorVersion = "3.1.1"
val libVersion = "3.1.89"

dependencies {
    implementation(project(":models"))

    implementation("no.nav.helved:utils:$libVersion")
    implementation("no.nav.helved:kafka:$libVersion")
    implementation("no.nav.helved:xml:$libVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    // TODO: ER det mulig å innkapsulere denne i libs?
    // vanilla producer
    implementation("org.apache.kafka:kafka-clients:3.9.0")

    testImplementation(kotlin("test"))
    testImplementation("org.apache.kafka:kafka-streams:3.9.0") // intercept StreamsBuilder
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav.helved:kafka-test:$libVersion")
}
