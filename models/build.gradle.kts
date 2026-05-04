val libVersion = "3.1.250"

dependencies {
    implementation(project(":libs:utils"))
    implementation("no.nav.helved:xml:$libVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.1")
    testImplementation(kotlin("test"))
    testImplementation("io.swagger.parser.v3:swagger-parser:2.1.25")
}
