dependencies {
    api("ch.qos.logback:logback-classic")
    api("net.logstash.logback:logstash-logback-encoder")
    api(kotlin("reflect"))
    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    inputs.files(rootProject.fileTree("apps") {
        include("*/main/logback.xml")
    })
}
