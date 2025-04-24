plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("vedskiva.VedskivaKt")
}

val ktorVersion = "3.1.2"
val libVersion = "3.1.96"

dependencies {
    implementation(project(":libs:kafka"))
    implementation(project(":models"))
    implementation("no.nav.helved:jdbc:$libVersion")

    implementation("org.apache.kafka:kafka-clients:4.0.0")
    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.15.0-alpha")

    implementation("no.nav.helved:utils:$libVersion")
    implementation("no.nav.helved:xml:$libVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")

    testImplementation(kotlin("test"))
    testImplementation("no.nav.helved:jdbc-test:$libVersion")
    testImplementation(project(":libs:kafka-test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}
