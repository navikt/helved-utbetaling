import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

// Convention plugin that wires all fat-JAR-related build customizations for
// helved-utbetaling apps. Apply this to every subproject; behavior is gated
// on the shadow / application plugins, so non-shadow modules (libs, models)
// are no-ops.
//
// Provides:
//  - rocksdbjni native-lib stripping on shadowJar (linux64-only deploys)
//  - distZip / distTar disabling (we ship fat JARs, not distributions)
//  - splitFatJar task that produces deps.jar + app.jar for Docker layering,
//    finalized by buildFatJar so CI's package step always runs the split

// Fat JARs ship a 200 MB rocksdbjni dependency containing native libs for 14
// architectures. NAIS runs cgr-nav/jre on linux/amd64 only, so strip every
// non-linux64 native from the shadowJar output. The non-fat-jar runtime
// classpath (used by tests) still receives the full rocksdbjni jar from
// Maven, so macOS/arm64 dev tests continue to work.
//
// Per-app savings: ~66 MB compressed (utsjekk: 134 MB -> ~68 MB).
// Total across 7 Kafka Streams apps: ~460 MB.
plugins.withId("com.gradleup.shadow") {
    tasks.named<ShadowJar>("shadowJar") {
        exclude("librocksdbjni-linux32*.so")
        exclude("librocksdbjni-linux64-musl.so")
        exclude("librocksdbjni-linux-aarch64*.so")
        exclude("librocksdbjni-linux-ppc64le*.so")
        exclude("librocksdbjni-linux-s390x*.so")
        exclude("librocksdbjni-linux-riscv64.so")
        exclude("librocksdbjni-osx-*.jnilib")
        exclude("librocksdbjni-win64.dll")
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
    val shadowJar = tasks.named<ShadowJar>("shadowJar")
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

// The Ktor / application plugin produces distZip + distTar archives we
// never use - the deployable artifact is the shadow fat JAR. Disabling
// them shaves seconds off `buildFatJar` (which depends on `assemble`).
plugins.withId("application") {
    tasks.matching { it.name == "distZip" || it.name == "distTar" }.configureEach {
        enabled = false
    }
}
