plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("branntaarn.BranntaarnKt")
}

val ktorVersion = "3.2.0"
val libVersion = "3.1.199"

dependencies {
    implementation(project(":models"))
    implementation(project(":libs:auth"))
    implementation(project(":libs:utils"))
    implementation("no.nav.helved:xml:$libVersion")

    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.19.0-alpha")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.0")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:ktor-test"))
    testImplementation(project(":libs:auth-test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
