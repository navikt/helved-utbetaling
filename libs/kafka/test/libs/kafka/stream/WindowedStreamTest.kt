package libs.kafka.stream

import libs.kafka.Mock
import libs.kafka.Topics
import libs.kafka.ms
import libs.kafka.produce
import libs.kafka.string
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class WindowedStreamTest {

    @Test
    fun `reduce with sliding windows`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .slidingWindow(string(), 100.ms)
                .reduce { s, s2 -> "$s$s2" }
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
                .hoppingWindow(string(), 100.ms, advanceSize = 50.ms)
                .reduce { s, s2 -> "$s$s2" }
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
                .tumblingWindow(string(), 100.ms)
                .reduce { s, s2 -> "$s$s2" }
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
    fun `reduce with session windows`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .sessionWindow(string(), 50.ms)
                .reduce { s, s2 -> "$s$s2" }
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
