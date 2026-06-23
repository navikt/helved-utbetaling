plugins {
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":libs:kotlinx"))
    implementation("no.nav.helved:xml:3.1.252")
    testImplementation(kotlin("test"))
    testImplementation("io.swagger.parser.v3:swagger-parser:2.1.25")
}
