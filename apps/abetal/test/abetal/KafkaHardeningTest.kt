package abetal

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import libs.kafka.Names
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import libs.kafka.StreamsMock
import libs.kafka.Topic
import libs.kafka.json
import libs.kafka.jsonList
import libs.kafka.string
import libs.kafka.topology
import models.Action
import models.Fagsystem
import models.Navident
import models.PeriodeId
import models.Personident
import models.Status
import models.StønadTypeDagpenger
import models.Utbetaling
import models.UtbetalingId
import org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG
import org.apache.kafka.streams.state.BuiltInDslStoreSuppliers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Properties
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class KafkaHardeningTest {

    @BeforeEach
    fun `clear logs`() {
        Names.clear()
        TestRuntime.clearLogs()
    }

    @AfterEach
    fun `cleanup hardening topics`() {
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.simulering.assertThat().isEmpty()
        TestRuntime.topics.saker.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().isEmpty()
        TestRuntime.topics.retryOppdrag.assertThat().isEmpty()
        TestRuntime.topics.dryrunAap.assertThat().isEmpty()
        TestRuntime.topics.dryrunDp.assertThat().isEmpty()
        TestRuntime.topics.dryrunTs.assertThat().isEmpty()
        TestRuntime.topics.dryrunTp.assertThat().isEmpty()
        TestRuntime.topics.status.assertThat().isEmpty()
        Names.clear()
        TestRuntime.clearLogs()
    }

    @Test
    fun `mixed dryrun batch gir feilet status og warn-logg med key uten pii`() {
        val input = Topic("dptuple-hardening-input.v1", jsonList<Utbetaling>())
        val ok = Topic("test.dryrun-validation-ok.v1", string())
        val kafka = validationKafka()

        kafka.connect(
            topology {
                consume(input)
                    .map { key, utbetalinger ->
                        validateSameDryrunBatch(key, utbetalinger)
                        "OK"
                    }
                    .produce(ok)
            },
            validationConfig(kafka),
            SimpleMeterRegistry()
        )

        val key = "mixed-dryrun-${UUID.randomUUID()}"
        val ident = "98765432109"
        kafka.testInputTopic(input).produce(key) {
            listOf(
                testUtbetaling(key, dryrun = true, fom = 1.jun, ident = ident),
                testUtbetaling(key, dryrun = false, fom = 2.jun, ident = ident),
            )
        }

        val status = kafka.testOutputTopic(Topics.status).assertThat()
            .has(key)
            .get(key)

        assertEquals(Status.FEILET, status.status)
        val error = assertNotNull(status.error)
        assertTrue(error.msg.contains("Kan ikke blande dryrun"))
        assertTrue(error.msg.contains("key=$key"))

        val warnLogs = TestRuntime.logAppender.list
            .filter { it.level == Level.WARN }
            .map(ILoggingEvent::getFormattedMessage)

        assertTrue(warnLogs.any { it.contains(key) })
        assertTrue(warnLogs.any { it.contains("dryrun=[true,false]") })
        assertFalse(warnLogs.any { it.contains(ident) })
        assertFalse(warnLogs.any { it.contains("personident", ignoreCase = true) })
    }

    @Test
    fun `all dryrun happy path fortsatt passerer`() {
        val input = Topic("dptuple-hardening-input.v1", jsonList<Utbetaling>())
        val ok = Topic("test.dryrun-validation-ok.v1", string())
        val kafka = validationKafka()

        kafka.connect(
            topology {
                consume(input)
                    .map { key, utbetalinger ->
                        validateSameDryrunBatch(key, utbetalinger)
                        "OK:$key"
                    }
                    .produce(ok)
            },
            validationConfig(kafka),
            SimpleMeterRegistry()
        )

        val key = "all-dryrun-${UUID.randomUUID()}"
        kafka.testInputTopic(input).produce(key) {
            listOf(
                testUtbetaling(key, dryrun = true, fom = 1.jun),
                testUtbetaling(key, dryrun = true, fom = 2.jun),
            )
        }

        kafka.testOutputTopic(ok).assertThat()
            .has(key)
            .with(key) { assertEquals("OK:$key", it) }

        assertTrue(TestRuntime.logAppender.list.none { it.formattedMessage.contains("Kan ikke blande dryrun") })
    }

    @Test
    fun `all non-dryrun happy path fortsatt passerer`() {
        val input = Topic("dptuple-hardening-input.v1", jsonList<Utbetaling>())
        val ok = Topic("test.dryrun-validation-ok.v1", string())
        val kafka = validationKafka()

        kafka.connect(
            topology {
                consume(input)
                    .map { key, utbetalinger ->
                        validateSameDryrunBatch(key, utbetalinger)
                        "OK:$key"
                    }
                    .produce(ok)
            },
            validationConfig(kafka),
            SimpleMeterRegistry()
        )

        val key = "all-nondryrun-${UUID.randomUUID()}"
        kafka.testInputTopic(input).produce(key) {
            listOf(
                testUtbetaling(key, dryrun = false, fom = 1.jun),
                testUtbetaling(key, dryrun = false, fom = 2.jun),
            )
        }

        kafka.testOutputTopic(ok).assertThat()
            .has(key)
            .with(key) { assertEquals("OK:$key", it) }

        assertTrue(TestRuntime.logAppender.list.none { it.formattedMessage.contains("Kan ikke blande dryrun") })
    }

    @Test
    fun `successfulUtbetaling default branch logger error og hopper over`() {
        val input = Topic("test.successful-default-branch.v1", json<DefaultBranchInput>())
        val kafka = StreamsMock()
        kafka.connect(
            topology {
                consume(input)
                    .branch({ value -> !value.hasPending && value.uids.isNotEmpty() }) {
                        this.map { it.originalKey }
                    }
                    .branch({ value -> value.hasPending }) {
                        this.map { it.originalKey }
                    }
                    .default {
                        forEach { _, value ->
                            logUnexpectedSuccessfulUtbetalingDefaultBranch(value.originalKey)
                        }
                    }
            },
            kafka.config,
            SimpleMeterRegistry()
        )

        val key = "unexpected-default-${UUID.randomUUID()}"
        val result = runCatching {
            kafka.testInputTopic(input).produce(key) { DefaultBranchInput(originalKey = key, uids = emptyList(), hasPending = false) }
        }

        assertTrue(result.isSuccess)

        val errorLogs = TestRuntime.logAppender.list
            .filter { it.level == Level.ERROR }
            .map(ILoggingEvent::getFormattedMessage)

        assertTrue(errorLogs.any { it.contains("Uventet default-branch i successfulUtbetalingStream") })
        assertTrue(errorLogs.any { it.contains("key=$key") })
        assertFalse(errorLogs.any { it.contains("personident", ignoreCase = true) })
        assertFalse(errorLogs.any { it.contains("12345678910") })
    }

    private fun validationKafka() = StreamsMock()

    private fun validationConfig(kafka: StreamsMock) = kafka.config.copy(additionalProperties = Properties().apply {
        this[org.apache.kafka.streams.StreamsConfig.PROCESSING_EXCEPTION_HANDLER_CLASS_CONFIG] =
            StatusOnProcessingErrorHandler::class.java
        put("state.dir", "build/kafka-streams-hardening")
        put("max.task.idle.ms", -1L)
        put(DSL_STORE_SUPPLIERS_CLASS_CONFIG, BuiltInDslStoreSuppliers.InMemoryDslStoreSuppliers::class.java)
    })

    private fun testUtbetaling(
        key: String,
        dryrun: Boolean,
        fom: LocalDate,
        ident: String = "12345678910",
    ): Utbetaling {
        return utbetaling(
            action = Action.CREATE,
            uid = UtbetalingId(UUID.randomUUID()),
            originalKey = key,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident(ident),
        ) {
            periode(fom, fom, 100u, 100u)
        }.copy(dryrun = dryrun)
    }

    private data class DefaultBranchInput(
        val originalKey: String,
        val uids: List<String>,
        val hasPending: Boolean,
    )
}
