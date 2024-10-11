package utsjekk.task.strategies

import TestRuntime
import awaitDatabase
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import libs.postgres.concurrency.transaction
import libs.task.TaskDao
import libs.task.Tasks
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.avstemming.AvstemmingTaskStrategy
import utsjekk.clients.Oppdrag
import java.time.LocalDateTime

class AvstemmingStrategyTest {

    @AfterEach
    fun reset() {
        runBlocking {
            withContext(TestRuntime.context) {
                transaction {
                    Tasks.forKind(libs.task.Kind.Avstemming).forEach {
                        it.copy(status = libs.task.Status.COMPLETE).update()
                    }
                }
            }
        }
    }

    @Test
    fun `avstemming task sender grensesnittavstemming`() = runTest(TestRuntime.context) {
        val avstemming = GrensesnittavstemmingRequest(
            Fagsystem.DAGPENGER,
            fra = LocalDateTime.now(),
            til = LocalDateTime.now(),
        )

        Tasks.create(libs.task.Kind.Avstemming, avstemming) {
            objectMapper.writeValueAsString(it)
        }

        TestRuntime.oppdrag.avstemmingRespondWith(avstemming.fagsystem, HttpStatusCode.Created)
        val actual = TestRuntime.oppdrag.awaitAvstemming(Fagsystem.DAGPENGER)

        assertEquals(avstemming.til.toLocalDate().atStartOfDay(), actual.til)
//        assertEquals(avstemming.fra.truncatedTo(ChronoUnit.SECONDS), actual.til.truncatedTo(ChronoUnit.SECONDS))
        assertEquals(Fagsystem.DAGPENGER, actual.fagsystem)
    }

    @Test
    fun `COMPLETED avstemming task oppretter ny task for neste virkedag`() = runTest(TestRuntime.context) {

        val now = LocalDateTime.now()
        val task = TaskDao(
            kind = libs.task.Kind.Avstemming,
            payload =
            objectMapper.writeValueAsString(
                GrensesnittavstemmingRequest(
                    Fagsystem.DAGPENGER,
                    fra = LocalDateTime.now(),
                    til = LocalDateTime.now(),
                )
            ),
            status = libs.task.Status.IN_PROGRESS,
            attempt = 0,
            message = null,
            createdAt = now,
            updatedAt = now,
            scheduledFor = now,
        )

        TestRuntime.oppdrag.avstemmingRespondWith(Fagsystem.DAGPENGER, HttpStatusCode.Created)

        transaction {
            task.insert()
        }

        val actual = awaitDatabase {
            TaskDao.select {
                it.id = task.id
                it.status = listOf(libs.task.Status.COMPLETE)
            }.firstOrNull()
        }

        assertEquals(libs.task.Status.COMPLETE, actual?.status)
    }

    @Test
    fun `kun manglende fagsystemer legges automatisk inn i task`() = runTest(TestRuntime.context) {
        val oppdrag = object : Oppdrag {
            override suspend fun avstem(grensesnittavstemming: GrensesnittavstemmingRequest) {}
            override suspend fun iverksettOppdrag(utbetalingsoppdrag: Utbetalingsoppdrag) {}
            override suspend fun hentStatus(oppdragIdDto: OppdragIdDto) = TODO("stub")
        }

        val strat = AvstemmingTaskStrategy(oppdrag)

        suspend fun countActiveAvstemminger(): Int = transaction {
            Tasks.forKind(libs.task.Kind.Avstemming).count { it.status.name == "IN_PROGRESS" }
        }

        reset()
        assertEquals(0, countActiveAvstemminger())

        strat.initiserAvstemmingForNyeFagsystemer()
        assertEquals(3, countActiveAvstemminger())

        strat.initiserAvstemmingForNyeFagsystemer()
        assertEquals(3, countActiveAvstemminger())
    }
}
