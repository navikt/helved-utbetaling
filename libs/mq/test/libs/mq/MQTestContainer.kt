package libs.mq

import org.testcontainers.containers.GenericContainer

class MQTestContainer {
    private val mq = GenericContainer<Nothing>("ibmcom/mq").apply {
        withReuse(true)
        withLabel("app", "oppdrag")
        withCreateContainerCmdModifier { it.withName("oppdrag-mq") }
        withEnv("LICENSE", "accept")
        withEnv("MQ_QMGR_NAME", "QM1")
        withNetwork(null)
        withExposedPorts(1414)
        start()
    }

    val config
        get() = MQConfig(
            host = "localhost",
            port = mq.firstMappedPort,
            channel = "DEV.ADMIN.SVRCONN",
            manager = "QM1",
            username = "admin",
            password = "passw0rd",
        )
}
