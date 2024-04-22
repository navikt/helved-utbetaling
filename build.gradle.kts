plugins {
    kotlin("jvm") version "1.9.23"
    id("io.ktor.plugin") version "2.3.9" apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    tasks {
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions.jvmTarget = "21"
        }

        withType<Test> {
            useJUnitPlatform()
        }

        kotlin.sourceSets["main"].kotlin.srcDirs("main")
        kotlin.sourceSets["test"].kotlin.srcDirs("test")
        sourceSets["main"].resources.srcDirs("main")
        sourceSets["test"].resources.srcDirs("test")
    }

}

allprojects {
    repositories {
        mavenCentral()
        maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }

    kotlin {
        jvmToolchain(21)
    }
}
