dependencies {
    implementation(project(":libs:utils"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    api("org.postgresql:postgresql:42.7.7")
    api("com.zaxxer:HikariCP:7.0.2")

    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation(kotlin("test"))
}
