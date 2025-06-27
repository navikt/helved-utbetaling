package no.nav.aap.kafka.streams.v2.stream

import libs.kafka.*
import libs.kafka.Mock
import libs.kafka.Tables
import libs.kafka.Topics
import libs.kafka.produce
import no.nav.aap.kafka.streams.v2.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class JoinedStreamTest {

    @AfterEach
    fun cleanup() {
        Names.clear()
    }

    @Test
    fun `join topic with table`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .join(Topics.A, consume(Tables.B))
                .map { a, b -> b + a }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B).produce("1", "B")
        kafka.inputTopic(Topics.A).produce("1", "A").produce("2", "A") // last should be skipped

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        assertEquals(1, result.size)
        assertEquals("BA", result["1"])
        assertNull(result["2"])
    }

    @Test
    fun `join filtered topic with table`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .filter { it != "humbug" }
                .join(Topics.A, consume(Tables.B))
                .map { a, b -> b + a }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B)
            .produce("1", "awesome")
            .produce("2", "nice")

        kafka.inputTopic(Topics.A)
            .produce("1", "sauce")
            .produce("1", "humbug")
            .produce("2", "humbug")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        assertEquals(2, result.size)
        assertEquals("awesomesauce", result["1"])
        assertEquals("niceprice", result["2"])
    }

    @Test
    fun `join topic with table and write back to topic`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .leftJoin(Topics.A, consume(Tables.B))
                .map { a, b -> a + b }
                .produce(Topics.B)
        }

        kafka.inputTopic(Topics.B)
            .produce("1", "sauce")
            .produce("2", "price")

        kafka.inputTopic(Topics.A)
            .produce("1", "awesome")
            .produce("2", "nice")

        val result = kafka.outputTopic(Topics.B).readKeyValuesToMap()
        assertEquals(2, result.size)
        assertEquals("awesomesauce", result["1"])
        assertEquals("niceprice", result["2"])
    }

    @Test
    fun `left join topic with table`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .leftJoin(Topics.A, consume(Tables.B))
                .map { left, _ -> left }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B).produce("1", "B")
        kafka.inputTopic(Topics.A).produce("1", "A")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        assertEquals(1, result.size)
        assertEquals("A", result["1"])
    }

    @Test
    fun `left join topic with table with no match`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .leftJoin(Topics.A, consume(Tables.B))
                .map { left, right -> right ?: left }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.A).produce("1", "A")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        assertEquals(1, result.size)
        assertEquals("A", result["1"])
    }

    @Test
    fun `left join filtered topic with table`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .filter { it != "humbug" }
                .leftJoin(Topics.A, consume(Tables.B))
                .map { a, b -> b + a }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B)
            .produce("1", "awesome")
            .produce("2", "nice")

        kafka.inputTopic(Topics.A)
            .produce("1", "sauce")
            .produce("1", "humbug")
            .produce("2", "humbug")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        assertEquals(2, result.size)
        assertEquals("awesomesauce", result["1"])
        assertEquals("niceprice", result["2"])
    }

    @Test
    fun `left join filtered topic with empty table is not filtered out`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .filter { it != "humbug" }
                .leftJoin(Topics.A, consume(Tables.B))
                .map { a, b -> (b ?: "") + a }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.A)
            .produce("1", "sauce")
            .produce("1", "humbug")
            .produce("2", "humbug")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        assertEquals(2, result.size)
        assertEquals("sauce", result["1"])
        assertEquals("price", result["2"])
    }

    @Test
    fun `join and flat map key and value`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .filter { it != "humbug" }
                .join(Topics.A, consume(Tables.B))
                .flatMapKeyValue { s, a, b -> listOf(KeyValue(s, a), KeyValue(s, b)) }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B).produce("1", "humbug").produce("2", "humbug")
        kafka.inputTopic(Topics.A).produce("1", "sauce").produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        assertEquals(2, result.size)
        assertEquals("humbug", result["1"])
        assertEquals("humbug", result["2"])
    }

    @Test
    fun `join and secure log with key`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .filter { it != "humbug" }
                .join(Topics.A, consume(Tables.B))
                .secureLogWithKey { key, left, right -> info("$key$left$right") }
        }
        kafka.inputTopic(Topics.B).produce("1", "humbug").produce("2", "humbug")
        kafka.inputTopic(Topics.A).produce("1", "sauce").produce("2", "price")
    }

    @Test
    fun `left join and secure log with key`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .filter { it != "humbug" }
                .leftJoin(Topics.A, consume(Tables.B))
                .secureLogWithKey { key, left, right -> info("$key$left$right") }
        }
        kafka.inputTopic(Topics.A).produce("1", "sauce").produce("2", "price")
    }

    @Test
    fun `filter a mapped joined stream`() {
        val kafka = Mock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.A)
                .join(Topics.A, table)
                .map { a, b -> b + a }
                .filter { it == "niceprice" }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B)
            .produce("1", "awesome")
            .produce("2", "nice")

        kafka.inputTopic(Topics.A)
            .produce("1", "sauce")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(1, result.size)
        assertNull(result["1"])
        assertEquals("niceprice", result["2"])
    }

    @Test
    fun `rekey a joined stream`() {
        val kafka = Mock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.A)
                .join(Topics.A, table)
                .rekey { a, b -> b + a }
                .map { a, _ -> a }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B).produce("1", "awesome")
        kafka.inputTopic(Topics.A).produce("1", "sauce")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        assertEquals(1, result.size)
        assertEquals("sauce", result["awesomesauce"])
    }
}
