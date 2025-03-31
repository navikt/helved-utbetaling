plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("abetal.AbetalKt")
}

val ktorVersion = "3.1.2"
val libVersion = "3.1.93"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:kafka"))

    implementation("no.nav.helved:xml:$libVersion")
    implementation("no.nav.helved:utils:$libVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.5")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    // TODO: ER det mulig å innkapsulere denne i libs?
    // vanilla producer
    implementation("org.apache.kafka:kafka-clients:3.9.0")

    testImplementation(kotlin("test"))
    testImplementation("org.apache.kafka:kafka-streams:3.9.0") // intercept StreamsBuilder
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation(project(":libs:kafka-test"))
    // testImplementation("no.nav.helved:kafka-test:$libVersion")
}
