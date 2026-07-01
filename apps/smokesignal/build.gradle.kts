plugins {
    id("io.ktor.plugin")
    kotlin("plugin.serialization")
}

application {
    mainClass.set("smokesignal.SmokesignalKt")
}

val ktorVersion = "3.5.1"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:auth"))
    implementation(project(":libs:kotlinx"))
    implementation(project(":libs:utils"))

    testImplementation(kotlin("test"))
}
