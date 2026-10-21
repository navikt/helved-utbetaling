plugins {
    id("com.gradleup.shadow")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("branntaarn.BranntaarnKt")
}

tasks.register("buildFatJar") {
    dependsOn(tasks.shadowJar)
}

val http4kVersion = "6.61.0.0"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:kotlinx"))
    implementation(project(":libs:utils"))
    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-ops-resilience4j:$http4kVersion")
    testImplementation(kotlin("test"))
}
