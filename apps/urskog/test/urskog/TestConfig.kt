package urskog

import com.ibm.mq.jms.MQQueue
import java.util.Properties
import libs.auth.AzureConfig
import libs.jdbc.JdbcConfig
import libs.kafka.SslConfig
import libs.kafka.StreamsConfig
import libs.mq.MQConfig
import libs.ws.SoapConfig
import org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG
import org.apache.kafka.streams.state.BuiltInDslStoreSuppliers

object TestConfig {
    fun create(
        proxy: ProxyConfig,
        azure: AzureConfig,
        simulering: SoapConfig,
        jdbc: JdbcConfig,
    ): Config {
        val oppdrag = OppdragConfig(
            avstemmingKø = MQQueue("DEV.QUEUE.3"),
            kvitteringsKø = MQQueue("DEV.QUEUE.2"),
            sendKø = MQQueue("DEV.QUEUE.1")
        )
        val kafka = StreamsConfig("", "", SslConfig("", "", ""), additionalProperties = Properties().apply {
            put("state.dir", "build/kafka-streams")
            put("max.task.idle.ms", -1L)
            put(DSL_STORE_SUPPLIERS_CLASS_CONFIG, BuiltInDslStoreSuppliers.InMemoryDslStoreSuppliers::class.java)
        })
        val mq = MQConfig(
            host = "og hark",
            port = 99,
            channel = "",
            manager = "anders",
            username = "",
            password = "",
        )
        return Config(
            jdbc = jdbc,
            kafka = kafka,
            oppdrag = oppdrag,
            mq = mq,
            proxy = proxy,
            azure = azure,
            simulering = simulering,
        )
    }
}

