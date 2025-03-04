val libVersion = "3.1.47"

dependencies {
    implementation("no.nav.helved:utils:$libVersion")
    implementation("no.nav.helved:xml:$libVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")
    testImplementation(kotlin("test"))
}
