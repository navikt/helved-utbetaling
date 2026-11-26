plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform("io.micrometer:micrometer-bom:1.18.0"))
    api(platform("tools.jackson:jackson-bom:3.3.0"))
    api(platform("io.netty:netty-bom:4.2.21.Final"))
    api(platform("io.opentelemetry:opentelemetry-bom:1.68.0"))
    api(platform("org.testcontainers:testcontainers-bom:2.0.5"))

    constraints {
        val logbackVersion = "1.6.8"
        api("ch.qos.logback:logback-classic:$logbackVersion")
        api("ch.qos.logback:logback-core:$logbackVersion")
        api("net.logstash.logback:logstash-logback-encoder:9.0")

        val kafkaVersion = "4.4.0"
        api("org.apache.kafka:kafka-streams:$kafkaVersion")
        api("org.apache.kafka:kafka-clients:$kafkaVersion")
        api("org.apache.kafka:kafka-streams-test-utils:$kafkaVersion")
    }
}
