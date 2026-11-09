plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform("io.micrometer:micrometer-bom:1.17.1"))

    constraints {
        val kafkaVersion = "4.3.1"
        api("org.apache.kafka:kafka-streams:$kafkaVersion")
        api("org.apache.kafka:kafka-clients:$kafkaVersion")
        api("org.apache.kafka:kafka-streams-test-utils:$kafkaVersion")
    }
}
