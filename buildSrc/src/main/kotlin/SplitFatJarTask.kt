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
