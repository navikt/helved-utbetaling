plugins {
    kotlin("jvm") version "1.9.23"
}

val ktorVersion = "2.3.9"

dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":libs:http"))

    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.4")

    implementation("no.nav.familie.tjenestespesifikasjoner:nav-system-os-simuler-fp-service-tjenestespesifikasjon:1.0_20230718100517_1e1beb0")
    implementation("no.nav.utsjekk.kontrakter:oppdrag:1.0_20240408113510_4a2db84")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.0")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    maven("https://build.shibboleth.net/maven/releases")

}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }
    withType<Test> {
        useJUnitPlatform()
    }
}
