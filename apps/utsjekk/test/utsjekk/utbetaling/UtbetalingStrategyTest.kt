package utsjekk.utbetaling

import TestRuntime
import awaitDatabase
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import libs.kafka.vanilla.Kafka
import libs.kafka.vanilla.KafkaConfig
import libs.postgres.concurrency.transaction
import libs.task.*
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import utsjekk.clients.Oppdrag
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UtbetalingStrategyTest {

    @Test
    fun `can execute delete`() = runTest(TestRuntime.context) {
        val uid = UtbetalingId.random()
        val utbetaling = Utbetaling.dagpenger(
            LocalDate.of(2021, 1, 1),
            listOf(
                Utbetalingsperiode.dagpenger(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 31), 100u),
            )
        )

        transaction {
            UtbetalingDao(utbetaling).insert(uid)
            UtbetalingDao.delete(uid)
        }

        val utbetalingsoppdrag = UtbetalingsoppdragDto.dagpenger(
            uid, utbetaling, listOf(
                UtbetalingsperiodeDto.opphør(
                    utbetaling,
                    LocalDate.of(2021, 1, 1),
                    LocalDate.of(2021, 1, 1),
                    LocalDate.of(2021, 1, 31),
                    100u,
                    StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG_AVDØD.klassekode,
                )
            )
        )

        TestRuntime.oppdrag.utbetalRespondWith(uid, HttpStatusCode.Created)
        Tasks.create(Kind.Utbetaling, utbetalingsoppdrag) { objectMapper.writeValueAsString(it) }
        TestRuntime.oppdrag.awaitUtbetaling(uid)

        val actual = awaitDatabase {
            UtbetalingDao.findOrNull(uid, true)?.let {
                if (it.status == Status.IKKE_PÅBEGYNT) { null } else { it }
            }
        }

        assertNotNull(actual)
    }

    @Test
    fun `task is applicable`() = runTest() {
        val kafka = KafkaFake()
        val oppdrag = OppdragFake()
        val strategy = UtbetalingTaskStrategy(
            oppdrag,
            // kafka
        )
        val task = task()
        assertTrue(strategy.isApplicable(task))
    }

    @Test
    fun `task is not applicable`() = runTest() {
        val kafka = KafkaFake()
        val oppdrag = OppdragFake()
        val strategy = UtbetalingTaskStrategy(
            oppdrag,
            // kafka
        )
        val task = task(kind = Kind.Iverksetting)
        assertFalse(strategy.isApplicable(task))
    }

    class KafkaFake : Kafka<UtbetalingStatus> {
        private val produced = mutableMapOf<String, CompletableDeferred<UtbetalingStatus>>()

        val config = KafkaConfig(
            brokers = "mock",
            truststore = "",
            keystore = "",
            credstorePassword = ""
        )

        override fun close() {
            produced.clear()
        }

        override fun produce(key: String, value: UtbetalingStatus) {
            produced[key]?.complete(value)
        }

        suspend fun waitFor(key: String): UtbetalingStatus {
            return produced[key]?.await() ?: error("kafka fake is not setup to expect $key")
        }
    }

    class OppdragFake : Oppdrag {
        private val oppdrag = mutableMapOf<OppdragIdDto, OppdragStatusDto>()
        private val oppdragV2 = mutableMapOf<UtbetalingId, OppdragStatusDto>()

        override suspend fun iverksettOppdrag(utbetalingsoppdrag: Utbetalingsoppdrag) {
            val oppdragIdDto = OppdragIdDto(
                fagsystem = utbetalingsoppdrag.fagsystem,
                sakId = utbetalingsoppdrag.saksnummer,
                behandlingId = utbetalingsoppdrag.utbetalingsperiode.single().behandlingId,
                iverksettingId = null,
            )
            oppdrag[oppdragIdDto] = OppdragStatusDto(status = OppdragStatus.KVITTERT_OK, "")
        }

        override suspend fun hentStatus(oppdragIdDto: OppdragIdDto): OppdragStatusDto {
            return oppdrag[oppdragIdDto] ?: error("status for oppdrag not found")
        }

        override suspend fun avstem(grensesnittavstemming: GrensesnittavstemmingRequest) {
            TODO("not implemented")
        }

        override suspend fun utbetal(utbetalingsoppdrag: UtbetalingsoppdragDto) {
            oppdragV2[utbetalingsoppdrag.uid] = OppdragStatusDto(status = OppdragStatus.KVITTERT_OK, "")
        }

        override suspend fun utbetalStatus(uid: UtbetalingId): OppdragStatusDto {
            return oppdragV2[uid] ?: error("status for oppdrag not found")
        }
    }
}

private fun task(
    status: libs.task.Status = libs.task.Status.IN_PROGRESS,
    kind: Kind = Kind.Utbetaling,
): TaskDao {
    val now = LocalDateTime.now()
    return TaskDao(UUID.randomUUID(), kind, "", status, 0, now, now, now, null)
} 

