package abetal

import abetal.dp.Dp
import abetal.dp.asBytes
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.Names
import libs.kafka.StreamsMock
import org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG
import org.apache.kafka.streams.state.BuiltInDslStoreSuppliers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Properties
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class MetricsTest {

    @BeforeEach
    fun `clear kafka names`() {
        Names.clear()
    }

    @AfterEach
    fun `cleanup kafka names`() {
        Names.clear()
    }

    @Test
    fun `metrics stay bounded and pii-free`() {
        val kafka = StreamsMock()
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val metrics = Metrics(prometheus)

        kafka.connect(
            createTopology(kafka, metrics),
            config(kafka),
            prometheus,
        )

        driveHappyPayment(kafka)

        repeat(1000) { index ->
            val key = "metrics-key-$index-${UUID.randomUUID()}"
            kafka.testInputTopic(Topics.dp).produce(key) {
                dpUtbetaling(
                    ident = "12345678910",
                    sakId = "sak-$index",
                    behandlingId = "behandling-$index",
                )
            }
        }

        val scrape = prometheus.scrape()

        listOf(
            Metrics.PAYMENT_PROCESSED_TOTAL,
            Metrics.OPPDRAG_SEND_SECONDS,
            Metrics.KVITTERING_WAIT_SECONDS,
            Metrics.PROCESSING_ERROR_TOTAL,
            Metrics.STATE_STORE_SIZE,
        ).forEach { assertTrue(scrape.contains(it), "mangler metric $it") }

        val okCount = Regex("""helved_abetal_payment_processed_total\{result=\"ok\"} ([0-9.]+)""")
            .find(scrape)
            ?.groupValues
            ?.get(1)
            ?.toDouble()
        assertTrue(okCount != null && okCount >= 1.0, "ok counter incrementerte ikke")

        val helvedSeries = scrape.lineSequence()
            .filter { it.startsWith("helved_abetal_") }
            .filter { !it.startsWith("#") }
            .toList()
        assertTrue(helvedSeries.size < 50, "for mange serier: ${helvedSeries.size}")

        val resultValues = Regex("""result=\"([^\"]+)\"""")
            .findAll(scrape)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue(resultValues.subtract(setOf("ok", "error")).isEmpty())

        val kindValues = Regex("""kind=\"([^\"]+)\"""")
            .findAll(scrape)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue(kindValues.subtract(setOf("validation", "topology", "other")).isEmpty())

        val storeValues = Regex("""store=\"([^\"]+)\"""")
            .findAll(scrape)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue(storeValues.size <= 2, "for mange store-labels: $storeValues")

        val labelSections = helvedSeries.mapNotNull { Regex("""\{([^}]*)} """).find(it)?.groupValues?.get(1) }
        assertTrue(labelSections.none { Regex("""\b\d{11}\b""").containsMatchIn(it) })
        assertTrue(labelSections.none { it.contains("personident", ignoreCase = true) })
        assertTrue(labelSections.none { it.contains("sakId", ignoreCase = true) })
        assertTrue(labelSections.none { it.contains("behandlingId", ignoreCase = true) })
        assertFalse(scrape.contains("12345678910"))
    }

    private fun driveHappyPayment(kafka: StreamsMock) {
        val key = "happy-${UUID.randomUUID()}"
        kafka.testInputTopic(Topics.dp).produce(key) {
            dpUtbetaling(ident = "12345678910")
        }
    }

    private fun dpUtbetaling(
        ident: String,
        sakId: String = "metrics-sak",
        behandlingId: String = UUID.randomUUID().toString(),
    ): ByteArray {
        return Dp.utbetaling(
            sakId = sakId,
            behandlingId = behandlingId,
            ident = ident,
            vedtakstidspunkt = LocalDateTime.now(),
        ) {
            addAll(Dp.meldekort("2025-08-01-2025-08-14", 4.jun25, 8.jun25, 1000u))
        }.asBytes()
    }

    private fun config(kafka: StreamsMock) = kafka.config.copy(additionalProperties = Properties().apply {
        this[org.apache.kafka.streams.StreamsConfig.PROCESSING_EXCEPTION_HANDLER_CLASS_CONFIG] =
            StatusOnProcessingErrorHandler::class.java
        put("state.dir", "build/kafka-streams-metrics")
        put("max.task.idle.ms", -1L)
        put(DSL_STORE_SUPPLIERS_CLASS_CONFIG, BuiltInDslStoreSuppliers.InMemoryDslStoreSuppliers::class.java)
    })
}
