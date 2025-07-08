dependencies {
    implementation(project(":libs:mq"))
    implementation(project(":libs:utils"))
    api("com.ibm.mq:com.ibm.mq.allclient:9.4.3.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

