package libs.mq

import libs.utils.env
import org.testcontainers.containers.GenericContainer

class MQTestcontainer {
    private val mq: GenericContainer<Nothing> =
        GenericContainer<Nothing>("ibmcom/mq").apply {
            if (!isGHA()) {
                withReuse(true)
                withCreateContainerCmdModifier { cmd ->
                    cmd.withName("mq")
                    cmd.hostConfig?.apply {
                        withMemory(512 * 1024 * 1024)
                        withMemorySwap(2 * 512 * 1024 * 1024)
                    }
                }
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
}

fun isGHA(): Boolean = env("GITHUB_ACTIONS", false)
