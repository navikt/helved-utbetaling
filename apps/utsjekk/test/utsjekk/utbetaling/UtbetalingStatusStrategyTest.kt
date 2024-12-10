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
import java.time.temporal.ChronoUnit
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
import libs.task.Tasks
import libs.postgres.concurrency.transaction

class UtbetalingStatusStrategyTest {

    private val oppdragFake = OppdragFake()
    private val strategy = UtbetalingStatusTaskStrategy(oppdragFake)

    @Test
    fun `payload is deserialized to UtbetalingId`() {
        val uid = UtbetalingId(UUID.randomUUID())
        val payload = objectMapper.writeValueAsString(uid)
        val actual = objectMapper.readValue<UtbetalingId>(payload)
        assertEquals(uid, actual)
    }

    @Test
    fun `task is applicable`() = runTest(TestRuntime.context) {
        val uid = UtbetalingId(UUID.randomUUID())
        val task = task(kind = libs.task.Kind.StatusUtbetaling, payload = uid)

        transaction {
            task.insert()
        }

        assertTrue(strategy.isApplicable(task))
    }

    @Test
    fun `status FAIL when utbetaling is missing`() = runTest(TestRuntime.context) {
        val uid = UtbetalingId(UUID.randomUUID())
        val task = task(kind = libs.task.Kind.StatusUtbetaling, payload = uid)

        transaction {
            task.insert()
        }

        assertTrue(strategy.isApplicable(task))
        strategy.execute(task)
        val actual = Tasks.forId(task.id)!!
        assertEquals(libs.task.Status.FAIL, actual.status)
    }

    @Test
    fun `status IN_PROGRESS when LAGT_PÅ_KØ`() = runTest(TestRuntime.context) {
        val uid = UtbetalingId(UUID.randomUUID())
        val task = task(kind = libs.task.Kind.StatusUtbetaling, payload = uid)
        val utbetaling = Utbetaling.dagpenger(
            vedtakstidspunkt = 1.feb, 
            periode = Utbetalingsperiode.dagpenger(1.feb, 8.feb, 800u, Satstype.DAG)
        )
        val uDao = UtbetalingDao(utbetaling)

        transaction {
            task.insert()
            uDao.insert(uid)
        }

        val oppdragDto = utbetalingsoppdragDto(utbetaling, uid) 
        val oppdragIdDto = oppdragIdDto(oppdragDto.into())
        oppdragFake.oppdrag[oppdragIdDto] = OppdragStatusDto(status = OppdragStatus.LAGT_PÅ_KØ, "")

        assertTrue(strategy.isApplicable(task))
        strategy.execute(task)

        val actual = Tasks.forId(task.id)!!
        assertEquals(libs.task.Status.IN_PROGRESS, actual.status)
    }

    @Test
    fun `status COMPLETE when KVITTERT_OK`() = runTest(TestRuntime.context) {
        val uid = UtbetalingId(UUID.randomUUID())
        val task = task(kind = libs.task.Kind.StatusUtbetaling, payload = uid)
        val utbetaling = Utbetaling.dagpenger(
            vedtakstidspunkt = 1.feb, 
            periode = Utbetalingsperiode.dagpenger(1.feb, 8.feb, 800u, Satstype.DAG)
        )
        val uDao = UtbetalingDao(utbetaling)

        transaction {
            task.insert()
            uDao.insert(uid)
        }

        val oppdragDto = utbetalingsoppdragDto(utbetaling, uid) 
        val oppdragIdDto = oppdragIdDto(oppdragDto.into())
        oppdragFake.oppdrag[oppdragIdDto] = OppdragStatusDto(status = OppdragStatus.KVITTERT_OK, "")

        assertTrue(strategy.isApplicable(task))
        strategy.execute(task)

        val actual = Tasks.forId(task.id)!!
        assertEquals(libs.task.Status.COMPLETE, actual.status)
    }

    @Test
    fun `status MANUAL when KVITTERT_MED_MANGLER`() = runTest(TestRuntime.context) {
        val uid = UtbetalingId(UUID.randomUUID())
        val task = task(kind = libs.task.Kind.StatusUtbetaling, payload = uid)
        val utbetaling = Utbetaling.dagpenger(
            vedtakstidspunkt = 1.feb, 
            periode = Utbetalingsperiode.dagpenger(1.feb, 8.feb, 800u, Satstype.DAG)
        )
        val uDao = UtbetalingDao(utbetaling)

        transaction {
            task.insert()
            uDao.insert(uid)
        }

        val oppdragDto = utbetalingsoppdragDto(utbetaling, uid) 
        val oppdragIdDto = oppdragIdDto(oppdragDto.into())
        oppdragFake.oppdrag[oppdragIdDto] = OppdragStatusDto(status = OppdragStatus.KVITTERT_MED_MANGLER, "")

        assertTrue(strategy.isApplicable(task))
        strategy.execute(task)

        val actual = Tasks.forId(task.id)!!
        assertEquals(libs.task.Status.MANUAL, actual.status)
    }

    @Test
    fun `status MANUAL when KVITTERT_TEKNISK_FEIL`() = runTest(TestRuntime.context) {
        val uid = UtbetalingId(UUID.randomUUID())
        val task = task(kind = libs.task.Kind.StatusUtbetaling, payload = uid)
        val utbetaling = Utbetaling.dagpenger(
            vedtakstidspunkt = 1.feb, 
            periode = Utbetalingsperiode.dagpenger(1.feb, 8.feb, 800u, Satstype.DAG)
        )
        val uDao = UtbetalingDao(utbetaling)

        transaction {
            task.insert()
            uDao.insert(uid)
        }

        val oppdragDto = utbetalingsoppdragDto(utbetaling, uid) 
        val oppdragIdDto = oppdragIdDto(oppdragDto.into())
        oppdragFake.oppdrag[oppdragIdDto] = OppdragStatusDto(status = OppdragStatus.KVITTERT_MED_MANGLER, "")

        assertTrue(strategy.isApplicable(task))
        strategy.execute(task)

        val actual = Tasks.forId(task.id)!!
        assertEquals(libs.task.Status.MANUAL, actual.status)
    }

    @Test
    fun `status MANUAL when KVITTERT_FUNKSJONELL_FEIL`() = runTest(TestRuntime.context) {
        val uid = UtbetalingId(UUID.randomUUID())
        val task = task(kind = libs.task.Kind.StatusUtbetaling, payload = uid)
        val utbetaling = Utbetaling.dagpenger(
            vedtakstidspunkt = 1.feb, 
            periode = Utbetalingsperiode.dagpenger(1.feb, 8.feb, 800u, Satstype.DAG)
        )
        val uDao = UtbetalingDao(utbetaling)

        transaction {
            task.insert()
            uDao.insert(uid)
        }

        val oppdragDto = utbetalingsoppdragDto(utbetaling, uid) 
        val oppdragIdDto = oppdragIdDto(oppdragDto.into())
        oppdragFake.oppdrag[oppdragIdDto] = OppdragStatusDto(status = OppdragStatus.KVITTERT_MED_MANGLER, "")

        assertTrue(strategy.isApplicable(task))
        strategy.execute(task)

        val actual = Tasks.forId(task.id)!!
        assertEquals(libs.task.Status.MANUAL, actual.status)
    }

    @Test
    fun `status MANUAL when KVITTERT_UKJENT`() = runTest(TestRuntime.context) {
        val uid = UtbetalingId(UUID.randomUUID())
        val task = task(kind = libs.task.Kind.StatusUtbetaling, payload = uid)
        val utbetaling = Utbetaling.dagpenger(
            vedtakstidspunkt = 1.feb, 
            periode = Utbetalingsperiode.dagpenger(1.feb, 8.feb, 800u, Satstype.DAG)
        )
        val uDao = UtbetalingDao(utbetaling)

        transaction {
            task.insert()
            uDao.insert(uid)
        }

        val oppdragDto = utbetalingsoppdragDto(utbetaling, uid) 
        val oppdragIdDto = oppdragIdDto(oppdragDto.into())
        oppdragFake.oppdrag[oppdragIdDto] = OppdragStatusDto(status = OppdragStatus.KVITTERT_MED_MANGLER, "")

        assertTrue(strategy.isApplicable(task))
        strategy.execute(task)

        val actual = Tasks.forId(task.id)!!
        assertEquals(libs.task.Status.MANUAL, actual.status)
    }

    class OppdragFake: Oppdrag {
        val oppdrag = mutableMapOf<OppdragIdDto, OppdragStatusDto>() 

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

private fun oppdragIdDto(u: Utbetalingsoppdrag): OppdragIdDto {
    return OppdragIdDto(
        fagsystem = u.fagsystem,
        sakId = u.saksnummer, 
        behandlingId = u.utbetalingsperiode.single().behandlingId, 
        iverksettingId = null, 
    )
}

private fun utbetalingsoppdragDto(
    utbetaling: Utbetaling,
    uid: UtbetalingId,
) = UtbetalingsoppdragDto (
    uid, 
    erFørsteUtbetalingPåSak = true, // TODO: må vi gjøre sql select på sakid for fagområde?
    fagsystem = FagsystemDto.DAGPENGER,
    saksnummer = utbetaling.sakId.id,
    aktør = utbetaling.personident.ident,
    saksbehandlerId = utbetaling.saksbehandlerId.ident,
    beslutterId = utbetaling.beslutterId.ident,
    avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
    brukersNavKontor = utbetaling.periode.betalendeEnhet?.enhet,
    utbetalingsperiode = utbetaling.periode.let {
        UtbetalingsperiodeDto(
            erEndringPåEksisterendePeriode = false,
            opphør = null,
            id = it.id, 
            vedtaksdato = utbetaling.vedtakstidspunkt.toLocalDate(),
            klassekode = "DPORAS",
            fom = it.fom,
            tom = it.tom,
            sats = it.beløp,
            satstype = it.satstype,
            utbetalesTil = utbetaling.personident.ident,
            behandlingId = utbetaling.behandlingId.id,
        )
    }
)

private fun task(
    status: libs.task.Status = libs.task.Status.IN_PROGRESS,
    kind: libs.task.Kind = libs.task.Kind.StatusUtbetaling,
    payload: UtbetalingId,
): TaskDao {
    val now = LocalDateTime.now()
    return TaskDao(
        id = UUID.randomUUID(),
        kind = kind,
        payload = objectMapper.writeValueAsString(payload),
        status = status,
        attempt = 0,
        createdAt = now,
        updatedAt = now,
        scheduledFor = now,
        message = null
    )
} 

