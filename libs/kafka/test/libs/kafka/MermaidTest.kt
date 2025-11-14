package libs.kafka

import libs.kafka.*
import libs.kafka.Tables
import libs.kafka.Topics
import libs.kafka.produce
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals

class MermaidTest {

    @AfterEach
    fun cleanup() {
        Names.clear()
    }

    @Test
    fun `join i en stream og initier i en annen`() {
        val kafka = Mock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.A)
                .join(Topics.A, table)
                .map { l, r -> r + l }
                .produce(Topics.C)

            consume(Topics.D)
                .produce(Topics.A)
        }

        kafka.inputTopic(Topics.B).produce("1", "hello")
        kafka.inputTopic(Topics.D).produce("1", " på do")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals("hello på do", result["1"])

        println(kafka.visulize().mermaid().generateDiagram())
    }

    @Test
    fun `include custom topic to db`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
            consume(Topics.B)
            consume(Topics.C)
        }

        println(
            kafka.visulize().mermaid().generateDiagramWithDatabaseSink(
                topicToDb = mapOf(
                    Topics.A.name to "postgres",
                    Topics.B.name to "postgres",
                    Topics.C.name to "postgres",
                )
            )
        )
    }

    // @Test
    // fun `custom state processor`() {
    //     val kafka = Mock.withTopology {
    //         val table = consume(Tables.B)
    //         consume(Topics.A)
    //             .processor(CustomProcessorWithTable(table))
    //             .produce(Topics.C)
    //     }
    //
    //     kafka.inputTopic(Topics.B).produce("1", ".v2")
    //     kafka.inputTopic(Topics.A).produce("1", "a")
    //
    //     val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()
    //
    //     assertEquals(1, result.size)
    //     assertEquals("a.v2", result["1"])
    //
    //     println(kafka.visulize().mermaid().generateDiagram())
    // }
}
