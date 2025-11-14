package libs.kafka.stream

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import libs.kafka.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.apache.kafka.streams.state.ValueAndTimestamp

internal class MappedStreamTest {
    @AfterEach
    fun cleanup() {
        Names.clear()
    }

    @Test
    fun `map a filtered joined stream`() {
        val kafka = Mock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.A)
                .join(Topics.A, table)
                .filter { (a, _) -> a == "sauce" }
                .map { a, b -> b + a }
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
        assertEquals("awesomesauce", result["1"])
        assertNull(result["2"])
    }

    @Test
    fun `map a filtered left joined stream`() {
        val kafka = Mock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.A)
                .leftJoin(Topics.A, table)
                .filter { (a, _) -> a == "sauce" }
                .map { a, b -> b + a }
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
        assertEquals("awesomesauce", result["1"])
        assertNull(result["2"])
    }

    @Test
    fun `map a joined stream`() {
        val kafka = Mock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.A)
                .join(Topics.A, table)
                .map { a, b -> b + a }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B)
            .produce("1", "awesome")
            .produce("2", "nice")

        kafka.inputTopic(Topics.A)
            .produce("1", "sauce")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(2, result.size)
        assertEquals("awesomesauce", result["1"])
        assertEquals("niceprice", result["2"])
    }

    @Test
    fun `mapNotNull a branched stream`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .mapNotNull { key, value -> if (key == "1") null else value }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.A)
            .produce("1", "sauce")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(1, result.size)

        assertEquals("price", result["2"])
    }

    @Test
    fun `map a left joined stream`() {
        val kafka = Mock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.A)
                .leftJoin(Topics.A, table)
                .map { a, b -> b + a }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B)
            .produce("1", "awesome")
            .produce("2", "nice")

        kafka.inputTopic(Topics.A)
            .produce("1", "sauce")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(2, result.size)
        assertEquals("awesomesauce", result["1"])
        assertEquals("niceprice", result["2"])
    }

    @Test
    fun `map key and value`() {
        val kafka = Mock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.A)
                .leftJoin(Topics.A, table)
                .mapKeyValue { key, left, right -> KeyValue("$key$key", right + left) }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B)
            .produce("1", "awesome")
            .produce("2", "nice")

        kafka.inputTopic(Topics.A)
            .produce("1", "sauce")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(2, result.size)
        assertEquals("awesomesauce", result["11"])
        assertEquals("niceprice", result["22"])
    }

    @Test
    fun `map and use custom processor`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .map { v -> v }
                .processor(CustomProcessor())
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.A).produce("1", "a")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(1, result.size)
        assertEquals("a.v2", result["1"])
    }

    // @Test
    // fun `map and use custom processor with table`() {
    //     val kafka = Mock.withTopology {
    //         val table = consume(Tables.B)
    //         consume(Topics.A)
    //             .map { v -> v }
    //             .stateProcessor(CustomProcessorWithTable(table))
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
    // }

    @Test
    fun `rekey a mapped stream`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .map { v -> "${v}alue" }
                .rekey { v -> v }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.A).produce("k", "v")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(1, result.size)
        assertEquals("value", result["value"])
    }

    @Test
    fun `filter a filtered mapped stream`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .filter { it.contains("nice") }
                .filter { it.contains("price") }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.A)
            .produce("1", "awesomenice")
            .produce("2", "niceprice")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(1, result.size)
        assertEquals("niceprice", result["2"])
    }

    @Test
    fun `rekey with mapKeyValue`() {
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .mapKeyAndValue { key, value -> "test:$key" to "$value$value" }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.A)
            .produce("1", "a")
            .produce("2", "b")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(2, result.size)
        assertEquals("aa", result["test:1"])
        assertEquals("bb", result["test:2"])
    }


    @Test
    fun `join mapped stream`() {
        val kafka = Mock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.E)
                .map { it -> ChangedDto(9, it.data) }
                .leftJoin(json(), table, "changed-leftjoin-B")
                .map { l, r -> jacksonObjectMapper().writeValueAsString(l.copy(data = r!!)) }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B).produce("1", "heyheyho")
        kafka.inputTopic(Topics.E).produce("1", JsonDto(1, "lol"))

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(1, result.size)
        assertEquals(ChangedDto(9, "heyheyho"), jacksonObjectMapper().readValue<ChangedDto<String>>(result["1"]!!))
    }

    @Test
    fun `materialize stream`() {
        val store = Store("yey", Serdes<KeyDto, JsonDto>(JsonSerde.jackson(), JsonSerde.jackson()))
        val kafka = Mock.withTopology {
            consume(Topics.A)
                .map { it -> JsonDto(9, it) }
                .rekey { jsonDto -> KeyDto(jsonDto.data) }
                .materialize(store)
        }

        val jsonDtoStore = kafka.getStore<KeyDto, JsonDto>(store)

        kafka.inputTopic(Topics.A).produce("1", "lol")

        val actual = jsonDtoStore.getOrNull(KeyDto("lol"))
        assertEquals(JsonDto(9, "lol"), actual)
    }
}

data class KeyDto(val id: String)

data class ChangedDto<T>(
    val id: Int,
    val data: T,
)
