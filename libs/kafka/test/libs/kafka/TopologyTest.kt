package libs.kafka

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals

internal class TopologyTest {

    @AfterEach
    fun cleanup() {
        Names.clear()
    }

    @Test
    fun `consume foreach`() {
        val result = mutableListOf<Int>()

        val kafka = Mock.withTopology {
            forEach(Topics.A) { _, value, _ ->
                result.add(value?.length ?: -1)
            }
        }

        kafka.inputTopic(Topics.A).produce("1", "something").produceTombstone("2")

        assertEquals(result, mutableListOf(9, -1))
    }
}
