plugins {
    kotlin("jvm") version "2.3.10"
    id("io.ktor.plugin") version "3.4.0" apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "helved.fatjar")

    tasks {
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
            compilerOptions.allWarningsAsErrors.set(true)
        }

        withType<JavaCompile>().configureEach {
            options.isFork = true
        }

        withType<Test> {
            useJUnitPlatform()

            // Each module can run multiple test JVMs in parallel via
            // -DmaxTestForks=N. Defaults to 1 because the per-fork init cost
            // (Postgres template clone + Kafka mock + Ktor server + migrations
            // in TestRuntime) is ~5-8s and dominates per-class test work,
            // making forking a net loss for the current test suite.
            //
            // Module-level parallelism (across the 9 apps) is enabled via
            // org.gradle.parallel=true in gradle.properties, and intra-JVM
            // class-level concurrency is enabled below.
            maxParallelForks = Integer.getInteger("maxTestForks", 1)
            forkEvery = 0L
            maxHeapSize = "1g"

            // JUnit 5 parallel execution. Set as system properties because the
            // root junit-platform.properties file is NOT on any module's
            // classpath under this project's non-standard source layout.
            // Test classes inside a single fork run concurrently; methods
            // inside a class still run on the same thread.
            systemProperty("junit.jupiter.execution.parallel.enabled", "true")
            systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
            systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
            systemProperty("junit.jupiter.execution.parallel.config.strategy", "dynamic")
        }

        sourceSets {
            main {
                kotlin.srcDir("main")
                resources.srcDirs("main", "migrations")
            }
            test {
                kotlin.srcDir("test")
                resources.srcDir("test")
            }
        }
    }
}

allprojects {
    repositories {
        mavenCentral()
        maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            extraWarnings.set(true)
        }
    }
}
