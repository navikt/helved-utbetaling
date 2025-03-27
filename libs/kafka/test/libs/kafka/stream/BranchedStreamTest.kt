package libs.kafka.stream

import libs.kafka.Mock
import libs.kafka.Tables
import libs.kafka.Topics
import libs.kafka.produce
import libs.kafka.JsonSerde
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class BranchedStreamTest {

    @Test
    fun `branch from consumed`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .branch({ v -> v == "lol" }, {
                    produce(Topics.C)
                })
                .branch({ v -> v != "lol" }, {
                    produce(Topics.B)
                })
        }

        kafka.inputTopic(Topics.A).produce("1", "lol")
        kafka.inputTopic(Topics.A).produce("2", "ikke lol")

        val resultC = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        val resultB = kafka.outputTopic(Topics.B).readKeyValuesToMap()

        assertEquals("lol", resultC["1"])
        assertEquals("ikke lol", resultB["2"])
    }

    @Test
    fun `default branch from consumed`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .branch({ v -> v == "lol" }, {
                    produce(Topics.C)
                })
                .default {
                    produce(Topics.B)
                }
        }

        kafka.inputTopic(Topics.A).produce("1", "lol")
        kafka.inputTopic(Topics.A).produce("2", "ikke lol")

        val resultC = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        val resultB = kafka.outputTopic(Topics.B).readKeyValuesToMap()

        assertEquals("lol", resultC["1"])
        assertEquals("ikke lol", resultB["2"])
    }

    @Test
    fun `branch from mapped`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .map { i -> i }
                .branch({ v -> v == "lol" }, {
                    produce(Topics.C)
                })
                .branch({ v -> v != "lol" }, {
                    produce(Topics.B)
                })
        }

        kafka.inputTopic(Topics.A).produce("1", "lol")
        kafka.inputTopic(Topics.A).produce("2", "ikke lol")

        val resultC = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        val resultB = kafka.outputTopic(Topics.B).readKeyValuesToMap()

        assertEquals("lol", resultC["1"])
        assertEquals("ikke lol", resultB["2"])
    }

    @Test
    fun `branch en branched stream from mapped`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .map { i -> i }
                .branch({ v -> v == "lol" }, {
                    this
                        .branch({ true }) { produce(Topics.C) }
                        .branch({ false }) { produce(Topics.B) }
                })
                .branch({ v -> v != "lol" }, {
                    produce(Topics.B)
                })
        }

        kafka.inputTopic(Topics.A).produce("1", "lol")
        kafka.inputTopic(Topics.A).produce("2", "ikke lol")

        val resultC = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        val resultB = kafka.outputTopic(Topics.B).readKeyValuesToMap()

        assertEquals("lol", resultC["1"])
        assertEquals("ikke lol", resultB["2"])
    }

    @Test
    fun `default branch stream from mapped`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .map { i -> i }
                .branch({ v -> v == "lol" }, {
                    produce(Topics.C)
                })
                .default {
                    produce(Topics.B)
                }
        }

        kafka.inputTopic(Topics.A).produce("1", "lol")
        kafka.inputTopic(Topics.A).produce("2", "ikke lol")

        val resultC = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        val resultB = kafka.outputTopic(Topics.B).readKeyValuesToMap()

        assertEquals("lol", resultC["1"])
        assertEquals("ikke lol", resultB["2"])
    }

    @Test
    fun `branch stream from joined stream`() {
        val kafka = Mock.withTopology {
            val tableB = consume(Tables.B)
            consume(Topics.A)
                .join(Topics.A, tableB)
                .branch({ (left, _) -> left == "lol" }, {
                    map { (left, right) -> left + right }
                        .produce(Topics.C)
                })
                .branch({ (_, right) -> right == "lol" }, {
                    map { (_, right) -> right + right }
                        .produce(Topics.D)
                })
        }

        kafka.inputTopic(Topics.B).produce("1", "lol") // right
        kafka.inputTopic(Topics.B).produce("2", "lol") // right
        kafka.inputTopic(Topics.A).produce("1", "lol") // left
        kafka.inputTopic(Topics.A).produce("2", "ikke lol")

        val resultC = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        val resultD = kafka.outputTopic(Topics.D).readKeyValuesToMap()

        assertEquals("lollol", resultC["1"])
        assertEquals("lollol", resultD["2"])
    }

    @Test
    fun `default branch from joined stream`() {
        val kafka = Mock.withTopology {
            val tableB = consume(Tables.B)
            consume(Topics.A)
                .join(Topics.A, tableB)
                .branch({ (left, _) -> left == "lol" }, {
                    // StreamsPair<L, R> -> LR krever ny serde. L er implisitt StreamsPair<L, R>
                    map { (left, right) -> left + right }.produce(Topics.C)

                })
                .default {
                    map { (_, right) -> right + right }.produce(Topics.D)
                }
        }

        kafka.inputTopic(Topics.B).produce("1", "lol") // right
        kafka.inputTopic(Topics.B).produce("2", "lol") // right
        kafka.inputTopic(Topics.A).produce("1", "lol") // left
        kafka.inputTopic(Topics.A).produce("2", "ikke lol")

        val resultC = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        val resultD = kafka.outputTopic(Topics.D).readKeyValuesToMap()

        assertEquals("lollol", resultC["1"])
        assertEquals("lollol", resultD["2"])
    }

    @Test
    fun `branch stream from left joined stream`() {
        val kafka = Mock.withTopology {
            val tableB = consume(Tables.B)
            consume(Topics.A)
                .leftJoin(Topics.A, tableB)
                .branch({ (left, _) -> left == "lol" }, {
                    map { (left, right) -> left + right }.produce(Topics.C)
                })
                .branch({ (_, right) -> right == "lol" }, {
                    map { (_, right) -> right + right }.produce(Topics.D)
                })
        }

        kafka.inputTopic(Topics.B).produce("1", "lol") // right
        kafka.inputTopic(Topics.B).produce("2", "lol") // right
        kafka.inputTopic(Topics.A).produce("1", "lol") // left
        kafka.inputTopic(Topics.A).produce("2", "ikke lol")

        val resultC = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        val resultD = kafka.outputTopic(Topics.D).readKeyValuesToMap()

        assertEquals("lollol", resultC["1"])
        assertEquals("lollol", resultD["2"])
    }

    @Test
    fun `default branch from left joined stream`() {
        val kafka = Mock.withTopology {
            val tableB = consume(Tables.B)
            consume(Topics.A)
                .leftJoin(Topics.A, tableB)
                .branch({ (left, _) -> left == "lol" }, {
                    map { (left, right) -> left + right }.produce(Topics.C)
                })
                .default {
                    map { (_, right) -> right + right }.produce(Topics.D)
                }
        }

        kafka.inputTopic(Topics.B).produce("1", "lol") // right
        kafka.inputTopic(Topics.B).produce("2", "lol") // right
        kafka.inputTopic(Topics.A).produce("1", "lol") // left
        kafka.inputTopic(Topics.A).produce("2", "ikke lol")

        val resultC = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        val resultD = kafka.outputTopic(Topics.D).readKeyValuesToMap()

        assertEquals("lollol", resultC["1"])
        assertEquals("lollol", resultD["2"])
    }
}
