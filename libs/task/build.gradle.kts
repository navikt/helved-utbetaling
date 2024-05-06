val ktorVersion = "2.3.9"

dependencies {
    api(project(":libs:utils"))
    api(project(":libs:postgres"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:job"))
    testImplementation("com.h2database:h2:2.2.224")
}
