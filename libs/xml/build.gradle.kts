
val ktorVersion = "2.3.9"

dependencies {
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.0")
    api("javax.xml.bind:jaxb-api:2.3.1")

    testImplementation(kotlin("test"))
    testImplementation("org.glassfish.jaxb:jaxb-runtime:2.3.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}
