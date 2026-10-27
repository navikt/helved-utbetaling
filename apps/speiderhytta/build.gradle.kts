plugins {
    id("com.gradleup.shadow")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("speiderhytta.SpeiderhyttaKt")
}

tasks.register("buildFatJar") {
    dependsOn(tasks.shadowJar)
}

val http4kVersion = "6.61.0.0"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:jdbc"))
    implementation(project(":libs:kotlinx"))
    implementation(project(":libs:utils"))

    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-format-kotlinx-serialization:$http4kVersion")
    implementation("org.http4k:http4k-ops-micrometer:$http4kVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.2")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:jdbc-test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

// speiderhytta tests share a single Postgres schema and the DAO tests rely on
// sequential row inserts/queries (verified 2026-04-24: concurrent mode causes
// row-count assertion failures). Keep same_thread.
tasks.withType<Test> {
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
}
