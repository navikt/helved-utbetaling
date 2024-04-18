
val ktorVersion = "2.3.9"

dependencies {
    api(project(":libs:utils"))

    api("com.ibm.mq:com.ibm.mq.allclient:9.3.5.0")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.0")
    api("javax.xml.bind:jaxb-api:2.3.1")
}
