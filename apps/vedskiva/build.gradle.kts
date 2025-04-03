plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("vedskiva.VedskivaKt")
}

val ktorVersion = "3.1.2"
val libVersion = "3.1.93"

dependencies {
    implementation(project(":libs:kafka"))
    implementation(project(":models"))

    implementation("no.nav.helved:utils:$libVersion")
    implementation("no.nav.helved:xml:$libVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.5")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
