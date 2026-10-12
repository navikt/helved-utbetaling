dependencies {
    api("ch.qos.logback:logback-classic:1.5.32")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:9.0")
    api(kotlin("reflect"))
    testImplementation("net.logstash.logback:logstash-logback-encoder:9.0")
    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    inputs.files(rootProject.fileTree("apps") {
        include("*/main/logback.xml")
    })
}
