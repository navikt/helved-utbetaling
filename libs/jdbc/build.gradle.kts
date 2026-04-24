dependencies {
    implementation(project(":libs:utils"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    api("org.postgresql:postgresql:42.7.8")
    api("com.zaxxer:HikariCP:7.0.2")

    testImplementation("com.h2database:h2:2.4.240")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation(kotlin("test"))
}

// libs:jdbc tests each use their own in-memory H2 datasource via captured local
// `ctx` and unique DB names, so class-concurrency is safe.
