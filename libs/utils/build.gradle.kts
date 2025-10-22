dependencies {
    api("ch.qos.logback:logback-classic:1.5.20")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.1")
    testImplementation(kotlin("test"))
}
