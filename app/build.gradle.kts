plugins {
    kotlin("jvm") version "1.9.23"
    id("io.ktor.plugin") version "2.3.9"
}

application {
    mainClass.set("oppdrag.AppKt")
}

val ktorVersion = "2.3.9"

dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":libs:auth"))

    implementation("no.nav.utsjekk.kontrakter:oppdrag:1.0_20240408113510_4a2db84")

    // server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.4")

    implementation("io.ktor:ktor-server-openapi:$ktorVersion")

    // json
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")

    // logging
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("ch.qos.logback:logback-classic:1.5.3")

    // MQ
    implementation("com.ibm.mq:com.ibm.mq.allclient:9.3.5.0")

    // Databse
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.8.1")

    // XSD to Java
    implementation("no.nav.tjenestespesifikasjoner:avstemming-v1-tjenestespesifikasjon:1.858e92e")
    implementation("no.nav.tjenestespesifikasjoner:nav-virksomhet-oppdragsbehandling-v1-meldingsdefinisjon:1.858e92e")
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.2")

    testImplementation(kotlin("test"))
    testImplementation("org.testcontainers:postgresql:1.19.7")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation(project(":libs:auth-test"))
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
