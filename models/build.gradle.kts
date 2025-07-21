val libVersion = "3.1.174"

dependencies {
    implementation(project(":libs:utils"))
    implementation("no.nav.helved:xml:$libVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.1")
    testImplementation(kotlin("test"))
}
