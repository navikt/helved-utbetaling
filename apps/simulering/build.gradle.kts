
val ktorVersion = "2.3.10"

dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":libs:http"))
    implementation(project(":libs:ws"))
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.4")

    implementation("no.nav.familie.tjenestespesifikasjoner:nav-system-os-simuler-fp-service-tjenestespesifikasjon:1.0_20230718100517_1e1beb0")
    implementation("no.nav.utsjekk.kontrakter:oppdrag:1.0_20240408113510_4a2db84")


    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
