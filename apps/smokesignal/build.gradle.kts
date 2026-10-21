plugins {
    id("com.gradleup.shadow")
    application
    kotlin("plugin.serialization")
}

application {
    mainClass.set("smokesignal.SmokesignalKt")
}

tasks.register("buildFatJar") {
    dependsOn(tasks.shadowJar)
}

val http4kVersion = "6.61.0.0"

dependencies {
    implementation(project(":models"))
    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation(project(":libs:kotlinx"))
    implementation(project(":libs:utils"))

    testImplementation(kotlin("test"))
}
