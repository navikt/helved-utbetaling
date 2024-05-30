package oppdrag.containers

import libs.mq.MQConfig
import oppdrag.isGHA
import org.testcontainers.containers.GenericContainer

class MQContainer : AutoCloseable {
    private val container: GenericContainer<Nothing> =
        GenericContainer<Nothing>("ibmcom/mq").apply {
            if (!isGHA()) {
                withLabel("service", "oppdrag")
                withReuse(true)
                withNetwork(null)
                withCreateContainerCmdModifier { cmd ->
                    cmd.withName("oppdrag-mq")
                    cmd.hostConfig?.apply {
                        withMemory(512 * 1024 * 1024)
                        withMemorySwap(2 * 512 * 1024 * 1024)
                    }
                }
            }
            withEnv("LICENSE", "accept")
            withEnv("MQ_QMGR_NAME", "QM1")
            withExposedPorts(1414)
            start()
        }

    val config by lazy {
        MQConfig(
            host = "127.0.0.1",
            port = container.getMappedPort(1414),
            channel = "DEV.ADMIN.SVRCONN",
            manager = "QM1",
            username = "admin",
            password = "passw0rd",
        )
    }

    override fun close() {
        if (isGHA()) {
            runCatching {
                container.close()
            }
        }
    }
}
