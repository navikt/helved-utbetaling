package oppdrag.containers

import libs.mq.MQConfig
import oppdrag.isGHA
import org.testcontainers.containers.GenericContainer

class MQTestcontainer : AutoCloseable {
    private val mq: GenericContainer<Nothing> =
        GenericContainer<Nothing>("ibmcom/mq").apply {
            if (!isGHA()) {
                withReuse(true)
                withLabel("app", "oppdrag")
                withCreateContainerCmdModifier { it.withName("oppdrag-mq") }
            }
            withEnv("LICENSE", "accept")
            withEnv("MQ_QMGR_NAME", "QM1")
            withNetwork(null)
            withExposedPorts(1414)
            start()
        }

    val config
        get() = MQConfig(
            host = "127.0.0.1",
            port = mq.getMappedPort(1414),
            channel = "DEV.ADMIN.SVRCONN",
            manager = "QM1",
            username = "admin",
            password = "passw0rd",
        )

    override fun close() {
        if (isGHA()) {
            runCatching {
                mq.close()
            }
        }
    }
}
