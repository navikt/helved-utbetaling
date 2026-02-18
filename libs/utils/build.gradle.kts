dependencies {
    api("ch.qos.logback:logback-classic:1.5.22")
    api(kotlin("reflect"))
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:9.0")
    testImplementation(kotlin("test"))
}
