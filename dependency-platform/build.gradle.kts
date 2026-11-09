plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform("io.micrometer:micrometer-bom:1.17.1"))
}
