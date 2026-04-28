package utsjekk.routes

import TestRuntime
import fakes.Azp
import httpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import models.*
import org.junit.jupiter.api.Test
import utsjekk.Topics

class SimuleringRoutesValidateTest {

    @Test
    fun `dryrun dagpenger avviser ugyldig request før kafka`() = runTest(TestRuntime.context) {
        val transactionId = UUID.randomUUID().toString()
        val producer = TestRuntime.kafka.getProducer(Topics.utbetalingDp)
        producer.clear()

        val response = postDagpenger(
            transactionId = transactionId,
            dto = gyldigDpUtbetaling().copy(utbetalinger = emptyList()),
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(DocumentedErrors.Async.Utbetaling.MANGLER_PERIODER.msg, response.body<ApiError>().msg)
        assertNull(producer.history().lastOrNull { (key, _) -> key == transactionId }?.second)
    }

    @Test
    fun `dryrun dagpenger validerer og sender gyldig request til kafka`() = runTest(TestRuntime.context) {
        val transactionId = UUID.randomUUID().toString()
        val producer = TestRuntime.kafka.getProducer(Topics.utbetalingDp)
        producer.clear()
        val dto = gyldigDpUtbetaling()

        val response = async { postDagpenger(transactionId, dto) }
        TestRuntime.topics.dryrunDp.produce(transactionId) { Info.OkUtenEndring(Fagsystem.DAGPENGER) }

        val actualResponse = response.await()
        val body = actualResponse.body<Info>()
        val actual = producer.history().lastOrNull { (key, _) -> key == transactionId }?.second

        assertEquals(HttpStatusCode.Found, actualResponse.status)
        assertEquals(Info.Status.OK_UTEN_ENDRING, body.status)
        assertEquals(Fagsystem.DAGPENGER, body.fagsystem)
        assertEquals(dto.copy(dryrun = true), actual)
    }

    @Test
    fun `dryrun aap avviser ugyldig request før kafka`() = runTest(TestRuntime.context) {
        val transactionId = UUID.randomUUID().toString()
        val producer = TestRuntime.kafka.getProducer(Topics.utbetalingAap)
        producer.clear()

        val response = postAap(
            transactionId = transactionId,
            dto = gyldigAapUtbetaling().copy(
                utbetalinger = listOf(
                    gyldigAapUtbetaling().utbetalinger.single().copy(utbetaltBeløp = 0u)
                )
            ),
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(DocumentedErrors.Async.Utbetaling.UGYLDIG_BELØP.msg, response.body<ApiError>().msg)
        assertNull(producer.history().lastOrNull { (key, _) -> key == transactionId }?.second)
    }

    @Test
    fun `dryrun aap validerer og sender gyldig request til kafka`() = runTest(TestRuntime.context) {
        val transactionId = UUID.randomUUID().toString()
        val producer = TestRuntime.kafka.getProducer(Topics.utbetalingAap)
        producer.clear()
        val dto = gyldigAapUtbetaling()

        val response = async { postAap(transactionId, dto) }
        TestRuntime.topics.dryrunAap.produce(transactionId) { Info.OkUtenEndring(Fagsystem.AAP) }

        val actualResponse = response.await()
        val body = actualResponse.body<Info>()
        val actual = producer.history().lastOrNull { (key, _) -> key == transactionId }?.second

        assertEquals(HttpStatusCode.Found, actualResponse.status)
        assertEquals(Info.Status.OK_UTEN_ENDRING, body.status)
        assertEquals(Fagsystem.AAP, body.fagsystem)
        assertEquals(dto.copy(dryrun = true), actual)
    }

    @Test
    fun `dryrun tilleggsstønader avviser ugyldig request før kafka`() = runTest(TestRuntime.context) {
        val transactionId = UUID.randomUUID().toString()
        val producer = TestRuntime.kafka.getProducer(Topics.utbetalingTs)
        producer.clear()

        val dto = gyldigTsUtbetaling().let {
            it.copy(
                utbetalinger = listOf(
                    it.utbetalinger.single().copy(
                        perioder = listOf(
                            it.utbetalinger.single().perioder.single().copy(
                                fom = LocalDate.of(2025, 10, 31),
                                tom = LocalDate.of(2025, 10, 1),
                            )
                        )
                    )
                )
            )
        }

        val response = postTilleggsstønader(transactionId, dto)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(DocumentedErrors.Async.Utbetaling.UGYLDIG_PERIODE.msg, response.body<ApiError>().msg)
        assertNull(producer.history().lastOrNull { (key, _) -> key == transactionId }?.second)
    }

    @Test
    fun `dryrun tilleggsstønader validerer og sender gyldig request til kafka`() = runTest(TestRuntime.context) {
        val transactionId = UUID.randomUUID().toString()
        val producer = TestRuntime.kafka.getProducer(Topics.utbetalingTs)
        producer.clear()
        val dto = gyldigTsUtbetaling()

        val response = async { postTilleggsstønader(transactionId, dto) }
        TestRuntime.topics.dryrunTs.produce(transactionId) { Info.OkUtenEndring(Fagsystem.TILLEGGSSTØNADER) }

        val actualResponse = response.await()
        val body = actualResponse.body<Info>()
        val actual = producer.history().lastOrNull { (key, _) -> key == transactionId }?.second

        assertEquals(HttpStatusCode.Found, actualResponse.status)
        assertEquals(Info.Status.OK_UTEN_ENDRING, body.status)
        assertEquals(Fagsystem.TILLEGGSSTØNADER, body.fagsystem)
        assertEquals(dto.copy(dryrun = true), actual)
    }

    @Test
    fun `dryrun tiltakspenger avviser ugyldig request før kafka`() = runTest(TestRuntime.context) {
        val transactionId = UUID.randomUUID().toString()
        val producer = TestRuntime.kafka.getProducer(Topics.utbetalingTp)
        producer.clear()

        val response = postTiltakspenger(
            transactionId = transactionId,
            dto = gyldigTpUtbetaling().copy(vedtakstidspunkt = LocalDateTime.now().plusDays(1)),
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Vedtakstidspunkt kan ikke være i fremtiden", response.body<ApiError>().msg)
        assertNull(producer.history().lastOrNull { (key, _) -> key == transactionId }?.second)
    }

    @Test
    fun `dryrun tiltakspenger validerer og sender gyldig request til kafka`() = runTest(TestRuntime.context) {
        val transactionId = UUID.randomUUID().toString()
        val producer = TestRuntime.kafka.getProducer(Topics.utbetalingTp)
        producer.clear()
        val dto = gyldigTpUtbetaling()

        val response = async { postTiltakspenger(transactionId, dto) }
        TestRuntime.topics.dryrunTp.produce(transactionId) { Info.OkUtenEndring(Fagsystem.TILTAKSPENGER) }

        val actualResponse = response.await()
        val body = actualResponse.body<Info>()
        val actual = producer.history().lastOrNull { (key, _) -> key == transactionId }?.second

        assertEquals(HttpStatusCode.Found, actualResponse.status)
        assertEquals(Info.Status.OK_UTEN_ENDRING, body.status)
        assertEquals(Fagsystem.TILTAKSPENGER, body.fagsystem)
        assertNotNull(actual)
        assertEquals(dto.copy(dryrun = true), actual)
    }
}

private fun gyldigDpUtbetaling() = DpUtbetaling(
    sakId = "sakId",
    behandlingId = "behandlingId",
    ident = "12345678910",
    vedtakstidspunktet = LocalDateTime.now().minusDays(1),
    utbetalinger = listOf(
        DpUtbetalingsdag(
            meldeperiode = "2025-10",
            dato = LocalDate.of(2025, 10, 1),
            sats = 1000u,
            utbetaltBeløp = 1000u,
            utbetalingstype = Utbetalingstype.Dagpenger,
        )
    ),
)

private fun gyldigAapUtbetaling() = AapUtbetaling(
    sakId = "sakId",
    behandlingId = "behandlingId",
    ident = "12345678910",
    vedtakstidspunktet = LocalDateTime.now().minusDays(1),
    utbetalinger = listOf(
        AapUtbetalingsdag(
            id = UUID.randomUUID(),
            fom = LocalDate.of(2025, 10, 1),
            tom = LocalDate.of(2025, 10, 3),
            sats = 1000u,
            utbetaltBeløp = 1000u,
        )
    ),
)

private fun gyldigTsUtbetaling() = TsDto(
    sakId = "sakId",
    behandlingId = "behandlingId",
    personident = "12345678910",
    vedtakstidspunkt = LocalDateTime.now().minusDays(1),
    periodetype = Periodetype.EN_GANG,
    utbetalinger = listOf(
        TsUtbetaling(
            id = UUID.randomUUID(),
            stønad = StønadTypeTilleggsstønader.DAGLIG_REISE_AAP,
            perioder = listOf(
                TsPeriode(
                    fom = LocalDate.of(2025, 10, 1),
                    tom = LocalDate.of(2025, 10, 31),
                    beløp = 1000u,
                )
            ),
        )
    ),
)

private fun gyldigTpUtbetaling() = TpUtbetaling(
    sakId = "sakId",
    behandlingId = "behandlingId",
    personident = "12345678910",
    vedtakstidspunkt = LocalDateTime.now().minusDays(1),
    perioder = listOf(
        TpPeriode(
            meldeperiode = "2025-10",
            fom = LocalDate.of(2025, 10, 1),
            tom = LocalDate.of(2025, 10, 3),
            beløp = 1000u,
            stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
        )
    ),
)

private suspend fun postDagpenger(transactionId: String, dto: DpUtbetaling) = httpClient.post("/api/dryrun/dagpenger") {
    contentType(ContentType.Application.Json)
    bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.DAGPENGER))
    header("Transaction-ID", transactionId)
    setBody(dto)
}

private suspend fun postAap(transactionId: String, dto: AapUtbetaling) = httpClient.post("/api/dryrun/aap") {
    contentType(ContentType.Application.Json)
    bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.AAP))
    header("Transaction-ID", transactionId)
    setBody(dto)
}

private suspend fun postTilleggsstønader(transactionId: String, dto: TsDto) = httpClient.post("/api/dryrun/tilleggsstonader") {
    contentType(ContentType.Application.Json)
    bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.TILLEGGSSTØNADER))
    header("Transaction-ID", transactionId)
    setBody(dto)
}

private suspend fun postTiltakspenger(transactionId: String, dto: TpUtbetaling) = httpClient.post("/api/dryrun/tiltakspenger") {
    contentType(ContentType.Application.Json)
    bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.TILTAKSPENGER))
    header("Transaction-ID", transactionId)
    setBody(dto)
}
