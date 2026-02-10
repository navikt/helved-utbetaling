plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("smokesignal.SmokesignalKt")
}

val ktorVersion = "3.3.3"
val libVersion = "3.1.219"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:auth"))
    implementation(project(":libs:utils"))

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.24.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.1")

    testImplementation(kotlin("test"))
}
