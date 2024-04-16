plugins {
    kotlin("jvm") version "1.9.23"
}

val ktorVersion = "2.3.9"

dependencies {
    implementation(project(":libs:utils"))

    api("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-client-cio:$ktorVersion")
    api("io.ktor:ktor-serialization-jackson:$ktorVersion")
    api("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    api("io.ktor:ktor-client-logging:$ktorVersion")
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }
    withType<Test> {
        useJUnitPlatform()
    }
}
