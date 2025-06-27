package libs.kafka.stream

import libs.kafka.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals

internal class WindowedStreamTest {

    @AfterEach
    fun cleanup() {
        Names.clear()
    }

    @Test
    fun `reduce with sliding windows`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .slidingWindow(string(), 100.ms, "sliding-window")
                .reduce("reduce-store") { s, s2 -> "$s$s2" }
                .produce(Topics.B)
        }

        println(kafka.visulize().uml())
        println(kafka.visulize().mermaid().generateDiagram())

        kafka.inputTopic(Topics.A)
            .produce("1", "a")
            .produce("1", "b")
            .produce("1", "c")

        val result = kafka.outputTopic(Topics.B).readKeyValuesToMap()

        assertEquals(1, result.size)
        assertEquals("abc", result["1"])
    }


    @Test
    fun `reduce with hopping windows`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .hoppingWindow(string(), 100.ms, advanceSize = 50.ms, "hopping-A")
                .reduce("reduce-store") { s, s2 -> "$s$s2" }
                .produce(Topics.B)
        }

        println(kafka.visulize().uml())
        println(kafka.visulize().mermaid().generateDiagram())

        kafka.inputTopic(Topics.A)
            .produce("1", "a")
            .produce("1", "b")
            .produce("1", "c")

        val result = kafka.outputTopic(Topics.B).readKeyValuesToMap()

        assertEquals(1, result.size)
        assertEquals("abc", result["1"])
    }

    @Test
    fun `reduce with tumbling windows`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .tumblingWindow(string(), 100.ms, "tumbling-A")
                .reduce("reduce-store") { s, s2 -> "$s$s2" }
                .produce(Topics.B)
        }

        println(kafka.visulize().uml())
        println(kafka.visulize().mermaid().generateDiagram())

        kafka.inputTopic(Topics.A)
            .produce("1", "a")
            .produce("1", "b")
            .produce("1", "c")

        val result = kafka.outputTopic(Topics.B).readKeyValuesToMap()

        assertEquals(1, result.size)
        assertEquals("abc", result["1"])
    }
}
