plugins {
    kotlin("jvm") version "1.9.23"
}

val ktorVersion = "2.3.9"

dependencies {
    api("com.nimbusds:nimbus-jose-jwt:9.37.3")
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
