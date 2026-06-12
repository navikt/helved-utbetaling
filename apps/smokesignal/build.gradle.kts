plugins {
    id("io.ktor.plugin")
    kotlin("plugin.serialization")
}

application {
    mainClass.set("smokesignal.SmokesignalKt")
}

val ktorVersion = "3.4.2"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:auth"))
    implementation(project(":libs:utils"))

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.24.0-alpha")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    testImplementation(kotlin("test"))
}
