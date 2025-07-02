val libVersion = "3.1.171"

dependencies {
    implementation(project(":libs:mq"))
    api("no.nav.helved:utils:$libVersion")
    api("com.ibm.mq:com.ibm.mq.allclient:9.4.1.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

