package utsjekk.utbetaling

import TestRuntime
import http
import httpClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.*
import java.util.*
import java.time.LocalDateTime
import libs.kafka.vanilla.Kafka
import utsjekk.clients.Oppdrag
import libs.kafka.vanilla.KafkaConfig
import libs.task.TaskDao
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatusDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class UtbetalingStrategyTest {

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
        val task = task(kind = libs.task.Kind.Iverksetting)
        assertFalse(strategy.isApplicable(task))
    }

    class KafkaFake: Kafka<UtbetalingStatus> {
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

        suspend fun waitFor(key: String): UtbetalingStatus{
            return produced[key]?.await() ?: error("kafka fake is not setup to expect $key")
        } 
    }

    class OppdragFake: Oppdrag {
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
    kind: libs.task.Kind = libs.task.Kind.Utbetaling,
): TaskDao {
    val now = LocalDateTime.now()
    return TaskDao(UUID.randomUUID(), kind, "", status, 0, now, now, now, null)
} 

