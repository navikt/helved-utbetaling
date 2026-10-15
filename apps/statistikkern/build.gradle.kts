plugins {
    id("com.gradleup.shadow")
    application
}

application {
    mainClass.set("statistikkern.StatistikkernKt")
}

tasks.register("buildFatJar") {
    dependsOn(tasks.shadowJar)
}

val http4kVersion = "6.61.0.0"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:utils"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:kotlinx"))
    implementation("no.nav.helved:xml:3.1.252")

    implementation("io.micrometer:micrometer-registry-prometheus:1.16.0")
    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-ops-micrometer:$http4kVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("com.google.cloud:google-cloud-bigquery:2.73.0")
    implementation("org.apache.kafka:kafka-clients:4.3.0")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kafka-test"))
    testImplementation("org.testcontainers:gcloud:1.21.4")
}
