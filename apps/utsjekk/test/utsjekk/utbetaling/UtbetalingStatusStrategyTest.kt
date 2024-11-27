package utsjekk.utbetaling

import TestRuntime
import http
import httpClient
import no.nav.utsjekk.kontrakter.felles.objectMapper
import io.ktor.client.*
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import utsjekk.*
import java.util.*
import java.time.LocalDateTime
import libs.kafka.Kafka
import utsjekk.clients.Oppdrag
import libs.kafka.KafkaConfig
import libs.task.TaskDao
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatusDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import libs.task.Tasks
import libs.postgres.concurrency.transaction
class UtbetalingStatusStrategyTest {

    @Test
    fun `utbetaling_id kan mappes til og fra json`() {
        val uid = UtbetalingId(UUID.randomUUID())
        val payload = objectMapper.writeValueAsString(uid)
        val actual = objectMapper.readValue<UtbetalingId>(payload)
        assertEquals(uid, actual)
    }

    @Test
    // @Disabled
    fun `task is applicable`() = runTest(TestRuntime.context) {
        val oppdrag = OppdragFake()
        val strategy = UtbetalingStatusTaskStrategy(oppdrag)
        val uid = UtbetalingId(UUID.randomUUID())
        val task = task(kind = libs.task.Kind.StatusUtbetaling, payload = uid)

        val taskId = transaction {
            task.insert()
        }

        assertTrue(strategy.isApplicable(task))
        strategy.execute(task)
    }

    class OppdragFake: Oppdrag {
        private val oppdrag = mutableMapOf<OppdragIdDto, OppdragStatusDto>() 

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
    }
}

private fun task(
    status: libs.task.Status = libs.task.Status.IN_PROGRESS,
    kind: libs.task.Kind = libs.task.Kind.StatusUtbetaling,
    payload: UtbetalingId,
): TaskDao {
    val now = LocalDateTime.now()
    return TaskDao(UUID.randomUUID(), kind, objectMapper.writeValueAsString(payload), status, 0, now, now, now, null)
} 

