val libVersion = "3.1.165"

dependencies {
    implementation("no.nav.helved:jdbc:$libVersion")
    implementation("no.nav.helved:utils:$libVersion")
    implementation(kotlin("test"))
    api("org.testcontainers:postgresql:1.21.2")
}
