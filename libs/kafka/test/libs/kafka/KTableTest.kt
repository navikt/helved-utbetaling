package libs.kafka

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals

class KTableTest {

    @AfterEach
    fun cleanup() {
        Names.clear()
    }

    @Test
    fun `can make it to stream`() {
        val kafka = Mock.withTopology {
            consume(Tables.B)
                .toStream()
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B)
            .produce("1", "hello")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        assertEquals(1, result.size)
        assertEquals("hello", result["1"])
    }

    @Test
    fun `join filtered topic with table and make it to stream`() {
        val kafka = Mock.withTopology {
            consume(Tables.B)
                .toStream()
                .filter { it != "humbug" }
                .join(Topics.B, consume(Tables.C))
                .map { a, b -> b + a }
                .produce(Topics.D)
        }

        kafka.inputTopic(Topics.C)
            .produce("1", "awesome")
            .produce("2", "nice")

        kafka.inputTopic(Topics.B)
            .produce("1", "sauce")
            .produce("1", "humbug")
            .produce("2", "humbug")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.D).readKeyValuesToMap()
        assertEquals(2, result.size)
        assertEquals("awesomesauce", result["1"])
        assertEquals("niceprice", result["2"])
    }

    @Test
    fun `join state store with ktable`() {
        val kafka = Mock.withTopology {
            val b = consume(Tables.B)
            val f = consume(Topics.A)
                .map { v -> v + v }
                .materialize(Stores.F)
            f.join(b)
                .filter { (_, b) -> b != null }
                .map { (f, b) -> f + b }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.A).produce("1", "sauce")
        kafka.inputTopic(Topics.B).produce("1", "awesome")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        assertEquals(1, result.size)
        assertEquals("saucesauceawesome", result["1"])
    }

    @Test
    fun `join state store with ktable without left`() {
        val kafka = Mock.withTopology {
            val b = consume(Tables.B)
            val f = consume(Topics.A)
                .map { v -> v + v }
                .materialize(Stores.F)
            f.join(b)
                .filter { (_, b) -> b != null }
                .map { (f, b) -> f + b }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B).produce("1", "awesome")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        assertEquals(0, result.size)
    }

    @Test
    fun `join state store with ktable without right`() {
        val kafka = Mock.withTopology {
            val b = consume(Tables.B)
            val f = consume(Topics.A)
                .map { v -> v + v }
                .materialize(Stores.F)
            f.join(b)
                .filter { (_, b) -> b != null }
                .map { (f, b) -> f + b }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.A).produce("1", "sauce")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        assertEquals(0, result.size)
    }
}
