package libs.kafka

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class TopologyTest {

    @Test
    fun `consume again`() {
        val kafka = Mock.withTopology {
            consume(Topics.A).produce(Topics.B)
            consumeForMock(Topics.A).produce(Topics.C)
        }

        kafka.inputTopic(Topics.A).produce("1", "hello")

        val resultB = kafka.outputTopic(Topics.B).readKeyValuesToMap()
        assertEquals(1, resultB.size)
        assertEquals("hello", resultB["1"])

        val resultC = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        assertEquals(1, resultC.size)
        assertEquals("hello", resultC["1"])
    }

    @Test
    fun `consume on each`() {
        val result = mutableListOf<Int>()

        val kafka = Mock.withTopology {
            consume(Topics.A) { _, value, _ ->
                result.add(value?.length ?: -1)
            }
        }

        kafka.inputTopic(Topics.A).produce("1", "something").produceTombstone("2")

        assertEquals(result, mutableListOf(9, -1))
    }
}
