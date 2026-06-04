plugins {
    id("com.gradleup.shadow")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("simulering.SimuleringKt")
}

tasks.register("buildFatJar") {
    dependsOn(tasks.shadowJar)
}

val http4kVersion = "6.48.0.0"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:utils"))

    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-format-kotlinx-serialization:$http4kVersion")
    implementation("org.http4k:http4k-ops-micrometer:$http4kVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.24.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    implementation("io.micrometer:micrometer-registry-prometheus:1.16.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("io.github.pdvrieze.xmlutil:serialization-jvm:1.0.0-rc2")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:auth-test"))
}
