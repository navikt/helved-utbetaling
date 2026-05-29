plugins {
    id("com.gradleup.shadow")
    application
}

application {
    mainClass.set("simulering.SimuleringKt")
}

tasks.register("buildFatJar") {
    dependsOn(tasks.shadowJar)
}

val http4kVersion = "6.48.0.0"
val libVersion = "3.1.250"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:utils"))
    implementation("no.nav.helved:xml:$libVersion")

    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-format-jackson:$http4kVersion")
    implementation("org.http4k:http4k-ops-micrometer:$http4kVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.24.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    implementation("io.micrometer:micrometer-registry-prometheus:1.16.2")

    implementation("jakarta.xml.ws:jakarta.xml.ws-api:4.0.3")
    implementation("com.sun.xml.ws:jaxws-rt:4.0.3")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:auth-test"))
}
