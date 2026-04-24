plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Provides the ShadowJar type referenced by the helved.fatjar precompiled
    // script plugin. Version must match what the io.ktor.plugin transitively
    // applies in app modules.
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.1.0")
}
