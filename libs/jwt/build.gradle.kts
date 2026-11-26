plugins {
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":libs:utils"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    testImplementation(kotlin("test"))
    testImplementation(project(":libs:auth-test"))
}
