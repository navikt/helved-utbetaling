import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.attribute.FileTime
import java.util.jar.Manifest
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    kotlin("jvm") version "2.3.10"
    id("io.ktor.plugin") version "3.4.0" apply false
}

// Cacheable typed task: same logic as the previous ad-hoc `doLast` block, but
// with explicit @InputFile / @Input / @OutputDirectory annotations and
// @CacheableTask so Gradle's local + remote build cache can serve repeat
// invocations from cache. Because the output JARs are byte-deterministic
// (fixed compression level + 1980 timestamps), identical fatJar input always
// produces identical deps.jar/app.jar, making cache hits safe and useful.
@CacheableTask
abstract class SplitFatJarTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fatJar: RegularFileProperty

    @get:Input
    abstract val moduleName: Property<String>

    @get:OutputDirectory
    abstract val outDir: DirectoryProperty

    @TaskAction
    fun run() {
        val src: File = fatJar.get().asFile
        val outDirFile: File = outDir.get().asFile.also { it.mkdirs() }
        val depsJar = outDirFile.resolve("deps.jar")
        val appJar = outDirFile.resolve("app.jar")

        val appPackages = setOf(moduleName.get(), "libs", "models")
        val migrationRegex = Regex("V\\d+__.*\\.sql")

        ZipFile(src).use { zf: ZipFile ->
            val manifestEntry: ZipEntry? = zf.getEntry("META-INF/MANIFEST.MF")
            val manifestBytes: ByteArray = if (manifestEntry != null) {
                zf.getInputStream(manifestEntry).use { input -> input.readAllBytes() }
            } else {
                "Manifest-Version: 1.0\r\n".toByteArray()
            }

            // Patch manifest with `Class-Path: deps.jar` so
            // `java -jar app.jar` resolves classes in deps.jar without
            // needing -cp on the command line.
            val patchedManifest: ByteArray = run {
                val mf = Manifest(ByteArrayInputStream(manifestBytes))
                mf.mainAttributes.putValue("Class-Path", "deps.jar")
                val buf = ByteArrayOutputStream()
                mf.write(buf)
                buf.toByteArray()
            }

            ZipOutputStream(depsJar.outputStream().buffered()).use { depsOut: ZipOutputStream ->
                ZipOutputStream(appJar.outputStream().buffered()).use { appOut: ZipOutputStream ->
                    // Determinism: fixed compression level and a stable
                    // entry timestamp (1980-01-01, the ZIP format's
                    // earliest representable date) so identical inputs
                    // always produce byte-identical output JARs. This
                    // is what lets CI compare deps.jar SHAs across runs
                    // to skip SLSA attestation when nothing changed.
                    depsOut.setLevel(Deflater.DEFAULT_COMPRESSION)
                    appOut.setLevel(Deflater.DEFAULT_COMPRESSION)
                    val stableTime = 315532800000L // 1980-01-01T00:00:00Z

                    fun writeEntry(target: ZipOutputStream, name: String, bytes: ByteArray) {
                        val e = ZipEntry(name)
                        e.time = stableTime
                        e.creationTime = FileTime.fromMillis(stableTime)
                        e.lastModifiedTime = FileTime.fromMillis(stableTime)
                        e.lastAccessTime = FileTime.fromMillis(stableTime)
                        target.putNextEntry(e)
                        target.write(bytes)
                        target.closeEntry()
                    }

                    writeEntry(appOut, "META-INF/MANIFEST.MF", patchedManifest)

                    // Sort entries by name for deterministic ordering;
                    // ZipFile.entries() iteration order is whatever the
                    // shadowJar plugin happened to emit (parallel build
                    // order varies across runs).
                    val sortedNames = zf.entries().asSequence()
                        .filter { !it.isDirectory && it.name != "META-INF/MANIFEST.MF" }
                        .map { it.name }
                        .sorted()
                        .toList()

                    for (name in sortedNames) {
                        val entry = zf.getEntry(name)
                        val first = name.substringBefore('/')
                        val isApp = first in appPackages ||
                            name == "logback.xml" ||
                            name == "migrations.sql" ||
                            migrationRegex.matches(name) ||
                            (name.startsWith("META-INF/") && name.endsWith(".kotlin_module"))
                        val target: ZipOutputStream = if (isApp) appOut else depsOut
                        val bytes = zf.getInputStream(entry).use { it.readAllBytes() }
                        writeEntry(target, name, bytes)
                    }
                }
            }
        }

        logger.lifecycle(
            "splitFatJar: ${src.length() / 1024 / 1024} MB -> " +
                "deps ${depsJar.length() / 1024 / 1024} MB + " +
                "app ${appJar.length() / 1024 / 1024} MB",
        )
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

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

// Fat JARs ship a 200 MB rocksdbjni dependency containing native libs for 14
// architectures. NAIS runs cgr-nav/jre on linux/amd64 only, so strip every
// non-linux64 native from the shadowJar output. The non-fat-jar runtime
// classpath (used by tests) still receives the full rocksdbjni jar from
// Maven, so macOS/arm64 dev tests continue to work.
//
// Per-app savings: ~66 MB compressed (utsjekk: 134 MB -> ~68 MB).
// Total across 7 Kafka Streams apps: ~460 MB.
subprojects {
    plugins.withId("com.gradleup.shadow") {
        tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
            exclude("librocksdbjni-linux32*.so")
            exclude("librocksdbjni-linux64-musl.so")
            exclude("librocksdbjni-linux-aarch64*.so")
            exclude("librocksdbjni-linux-ppc64le*.so")
            exclude("librocksdbjni-linux-s390x*.so")
            exclude("librocksdbjni-linux-riscv64.so")
            exclude("librocksdbjni-osx-*.jnilib")
            exclude("librocksdbjni-win64.dll")
        }
    }

    // The Ktor / application plugin produces distZip + distTar archives we
    // never use - the deployable artifact is the shadow fat JAR. Disabling
    // them shaves seconds off `buildFatJar` (which depends on `assemble`).
    plugins.withId("application") {
        tasks.matching { it.name == "distZip" || it.name == "distTar" }.configureEach {
            enabled = false
        }
    }

    // splitFatJar: split shadowJar output into two jars so the Docker image
    // gets two layers - a stable ~66 MB deps layer and a small ~2 MB app layer.
    // The deps layer is cached across builds whenever third-party libraries
    // don't change, so the docker push only uploads the app layer.
    //
    // Classification rule: anything under our own packages (utsjekk/, abetal/,
    // urskog/ etc., plus libs/ and models/) plus app resources (logback.xml,
    // migrations.sql, V*__*.sql, META-INF/<module>.kotlin_module) goes in
    // app.jar. Everything else goes in deps.jar.
    plugins.withId("com.gradleup.shadow") {
        val shadowJar = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar")
        val splitFatJar = tasks.register<SplitFatJarTask>("splitFatJar") {
            group = "build"
            description = "Split the shadow fat JAR into deps.jar (third-party) and app.jar (our code)."
            dependsOn(shadowJar)
            fatJar.set(shadowJar.flatMap { it.archiveFile })
            moduleName.set(project.name)
            outDir.set(layout.buildDirectory.dir("split-jars"))
        }

        // Hook into buildFatJar so the split runs as part of CI's package step.
        tasks.matching { it.name == "buildFatJar" }.configureEach {
            finalizedBy(splitFatJar)
        }
    }
}

// SplitFatJarTask is declared at the top of this file. It's @CacheableTask, so
// repeat invocations with identical fatJar input are served from the Gradle
// build cache (local + GHA-restored gradle home cache). The task body itself
// produces byte-deterministic output (fixed compression + 1980 timestamps) so
// cached artifacts are reproducible.
