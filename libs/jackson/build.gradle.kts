dependencies {
    implementation(project(":models"))

    api("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.2")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.1")

    testImplementation(kotlin("test"))
}
