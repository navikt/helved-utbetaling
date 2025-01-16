package utsjekk.utbetaling

import TestRuntime
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import libs.task.TaskDao
import libs.task.Tasks
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatusDto
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.clients.Oppdrag
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertTrue

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
            perioder = listOf(Utbetalingsperiode.dagpenger(1.feb, 8.feb, 800u, Satstype.DAG))
        )
        val uDao = UtbetalingDao(utbetaling)

        transaction {
            task.insert()
            uDao.insert(uid)
        }

        oppdragFake.oppdragV2[uid] = OppdragStatusDto(status = OppdragStatus.LAGT_PÅ_KØ, "")

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
            perioder = listOf(Utbetalingsperiode.dagpenger(1.feb, 8.feb, 800u, Satstype.DAG))
        )
        val uDao = UtbetalingDao(utbetaling)

        transaction {
            task.insert()
            uDao.insert(uid)
        }

        oppdragFake.oppdragV2[uid] = OppdragStatusDto(status = OppdragStatus.KVITTERT_OK, "")

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
            perioder = listOf(Utbetalingsperiode.dagpenger(1.feb, 8.feb, 800u, Satstype.DAG))
        )
        val uDao = UtbetalingDao(utbetaling)

        transaction {
            task.insert()
            uDao.insert(uid)
        }

        oppdragFake.oppdragV2[uid] = OppdragStatusDto(status = OppdragStatus.KVITTERT_MED_MANGLER, "")

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
            perioder = listOf(Utbetalingsperiode.dagpenger(1.feb, 8.feb, 800u, Satstype.DAG))
        )
        val uDao = UtbetalingDao(utbetaling)

        transaction {
            task.insert()
            uDao.insert(uid)
        }

        oppdragFake.oppdragV2[uid] = OppdragStatusDto(status = OppdragStatus.KVITTERT_MED_MANGLER, "")

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
            perioder = listOf(Utbetalingsperiode.dagpenger(1.feb, 8.feb, 800u, Satstype.DAG))
        )
        val uDao = UtbetalingDao(utbetaling)

        transaction {
            task.insert()
            uDao.insert(uid)
        }

        oppdragFake.oppdragV2[uid] = OppdragStatusDto(status = OppdragStatus.KVITTERT_MED_MANGLER, "")

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
            perioder = listOf(Utbetalingsperiode.dagpenger(1.feb, 8.feb, 800u, Satstype.DAG))
        )
        val uDao = UtbetalingDao(utbetaling)

        transaction {
            task.insert()
            uDao.insert(uid)
        }

        oppdragFake.oppdragV2[uid] = OppdragStatusDto(status = OppdragStatus.KVITTERT_MED_MANGLER, "")

        assertTrue(strategy.isApplicable(task))
        strategy.execute(task)

        val actual = Tasks.forId(task.id)!!
        assertEquals(libs.task.Status.MANUAL, actual.status)
    }

    class OppdragFake : Oppdrag {
        val oppdrag = mutableMapOf<OppdragIdDto, OppdragStatusDto>()
        val oppdragV2 = mutableMapOf<UtbetalingId, OppdragStatusDto>()

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

