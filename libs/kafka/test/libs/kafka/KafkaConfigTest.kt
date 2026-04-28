package libs.kafka

import kotlin.test.Test
import kotlin.test.assertEquals
import org.apache.kafka.streams.StreamsConfig as KStreamsConfig

class KafkaConfigTest {

    @Test
    fun `ensure explicit internal resource naming is enabled`() {
        val props = StreamsConfig(
            applicationId = "test-app",
            brokers = "localhost:9092",
            ssl = null,
        ).streamsProperties()

        assertEquals(true, props[KStreamsConfig.ENSURE_EXPLICIT_INTERNAL_RESOURCE_NAMING_CONFIG])
    }
}
