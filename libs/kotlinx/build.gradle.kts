// plugins {
//     kotlin("plugin.serialization")
// }

dependencies {
    implementation(project(":libs:utils"))
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
}
