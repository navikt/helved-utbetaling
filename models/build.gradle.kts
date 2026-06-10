val libVersion = "3.1.250"

plugins {
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":libs:utils"))
    implementation("no.nav.helved:xml:$libVersion")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    testImplementation(kotlin("test"))
    testImplementation("io.swagger.parser.v3:swagger-parser:2.1.25")
}
