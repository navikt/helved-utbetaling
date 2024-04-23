package libs.mq

import libs.utils.env
import org.testcontainers.containers.GenericContainer

class MQTestcontainer {
    private val mq: GenericContainer<Nothing>? =
        when (isGHA()) {
            true -> null
            else -> GenericContainer<Nothing>("ibmcom/mq").apply {
                withReuse(true)
                withLabel("app", "oppdrag")
                withCreateContainerCmdModifier { it.withName("oppdrag-mq") }
                withEnv("LICENSE", "accept")
                withEnv("MQ_QMGR_NAME", "QM1")
                withNetwork(null)
                withExposedPorts(1414)
                start()
            }
        }

    val config
        get() = MQConfig(
            host = "localhost",
            port = mq?.firstMappedPort ?: 1414,
            channel = "DEV.ADMIN.SVRCONN",
            manager = "QM1",
            username = "admin",
            password = "passw0rd",
        )
}

fun isGHA(): Boolean {
    return runCatching {
        env<Boolean>("GITHUB_ACTIONS")
    }.getOrDefault(false)
}
