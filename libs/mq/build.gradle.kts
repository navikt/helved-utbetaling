val libVersion = "3.1.217"

dependencies {
    api(project(":libs:tracing"))
    api(project(":libs:utils"))

    api("no.nav.helved:xml:$libVersion")

    api("com.ibm.mq:com.ibm.mq.allclient:9.4.4.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:mq-test"))
}
