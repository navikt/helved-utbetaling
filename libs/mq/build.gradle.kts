val ktorVersion = "2.3.9"

dependencies {
    api(project(":libs:utils"))
    api(project(":libs:xml"))

    api("com.ibm.mq:com.ibm.mq.allclient:9.3.5.0")

    testImplementation(kotlin("test"))
    testImplementation("org.testcontainers:testcontainers:1.19.7")
//    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0")
}
