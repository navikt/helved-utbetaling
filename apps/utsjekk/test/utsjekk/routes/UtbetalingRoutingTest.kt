package utsjekk.utbetaling

import TestRuntime
import httpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import libs.jdbc.concurrency.transaction
import models.ApiError
import models.DocumentedErrors
import models.StatusReply
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.*
import utsjekk.iverksetting.RandomOSURId

class UtbetalingRoutingTest {

    @Test
    fun `can create Utbetaling`() = runTest {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }

        assertEquals(HttpStatusCode.Created, res.status)
        assertEquals("/utbetalinger/$uid", res.headers["location"])
    }

    @Test
    fun `cannot create the same Utbetaling twice`() = runTest {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()

        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.let {
            assertEquals(HttpStatusCode.Created, it.status)
            assertEquals("/utbetalinger/$uid", it.headers["location"])
        }


        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.let {
            assertEquals(HttpStatusCode.Conflict, it.status)
        }
    }

    @Test
    fun `erFørsteUtbetaling lik true på sak skal ha kodeendring lik NY`() = runTest {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
            erFørsteUtbetalingPåSak = true
        )

        val uid = UUID.randomUUID()

        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.let {
            assertEquals(HttpStatusCode.Created, it.status)
        }
        val oppdragTopic = TestRuntime.kafka.getProducer(Topics.oppdrag)
        assertEquals(0, oppdragTopic.uncommitted().size)
        val actual = oppdragTopic.history().singleOrNull { (key, _) -> key == uid.toString() }?.second
        assertNotNull(actual)
        assertEquals("NY", actual.oppdrag110.kodeEndring)
    }

    @Test
    fun `avstemming115 er påkrevd`() = runTest {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
            erFørsteUtbetalingPåSak = true
        )

        val uid = UUID.randomUUID()

        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.let {
            assertEquals(HttpStatusCode.Created, it.status)
        }
        val oppdragTopic = TestRuntime.kafka.getProducer(Topics.oppdrag)
        assertEquals(0, oppdragTopic.uncommitted().size)
        val oppdrag = oppdragTopic.history().singleOrNull { (key, _) -> key == uid.toString() }?.second
        assertNotNull(oppdrag)
        assertEquals("DP", oppdrag.oppdrag110.avstemming115.kodeKomponent)
        val todayAtTen = LocalDateTime.now().with(LocalTime.of(10, 10, 0, 0)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS"))
        assertEquals(todayAtTen, oppdrag.oppdrag110.avstemming115.nokkelAvstemming)
        assertEquals(todayAtTen, oppdrag.oppdrag110.avstemming115.tidspktMelding)
    }

    @Test
    fun `erFørsteUtbetaling lik false på sak skal ha kodeendring lik ENDR`() = runTest {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
            erFørsteUtbetalingPåSak = false
        )

        val uid = UUID.randomUUID()

        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.let {
            assertEquals(HttpStatusCode.Created, it.status)
        }
        val oppdragTopic = TestRuntime.kafka.getProducer(Topics.oppdrag)
        assertEquals(0, oppdragTopic.uncommitted().size)
        val actual = oppdragTopic.history().singleOrNull { (key, _) -> key == uid.toString() }?.second
        assertNotNull(actual)
        assertEquals("ENDR", actual.oppdrag110.kodeEndring)
    }

    @Test
    fun `hvis erFørsteUtbetaling lik null på sak og den ikke finnes i basen skal kodeendring være lik NY`() = runTest {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
            erFørsteUtbetalingPåSak = null
        )

        val uid = UUID.randomUUID()

        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.let {
            assertEquals(HttpStatusCode.Created, it.status)
        }
        val oppdragTopic = TestRuntime.kafka.getProducer(Topics.oppdrag)
        assertEquals(0, oppdragTopic.uncommitted().size)
        val actual = oppdragTopic.history().singleOrNull { (key, _) -> key == uid.toString() }?.second
        assertNotNull(actual)
        assertEquals("NY", actual.oppdrag110.kodeEndring)
    }
    @Test
    fun `hvis erFørsteUtbetaling lik null på sak og den finnes i basen skal kodeendring være lik ENDR`() = runTest {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
            erFørsteUtbetalingPåSak = null
        )

        val uid = UUID.randomUUID()

        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.let {
            assertEquals(HttpStatusCode.Created, it.status)
        }

        val oppdragTopic = TestRuntime.kafka.getProducer(Topics.oppdrag)
        assertEquals(0, oppdragTopic.uncommitted().size)
        val oppdrag = oppdragTopic.history().singleOrNull { (key, _) -> key == uid.toString() }?.second
        assertNotNull(oppdrag)
        assertEquals("NY", oppdrag.oppdrag110.kodeEndring)

        val endretUtbetaling = utbetaling.copy(perioder = listOf(utbetaling.perioder[0].copy(beløp=1u)))
        TestRuntime.topics.status.produce(uid.toString()) {
            StatusReply.ok(oppdrag)
        }
        val status = runBlocking {
                withTimeout(1000L) {
                var status: Status? = null
                while (status != Status.OK) {
                    httpClient.get("/utbetalinger/$uid/status") {
                        bearerAuth(TestRuntime.azure.generateToken())
                        accept(ContentType.Application.Json)
                    }.let {
                        status = it.body<Status>()
                    }
                }
                status
            }
        }
        assertEquals(status, Status.OK)
        httpClient.put("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(endretUtbetaling)
        }.let {
            assertEquals(HttpStatusCode.NoContent, it.status)
        }

        assertEquals(0, oppdragTopic.uncommitted().size)
        val endretOppdrag = oppdragTopic.history().filter { (key, _) -> key == uid.toString() }
        assertEquals(2, endretOppdrag.size)
        assertEquals("NY", endretOppdrag[0].second.oppdrag110.kodeEndring)
        assertEquals("ENDR", endretOppdrag[1].second.oppdrag110.kodeEndring)
    }

    @Test
    fun `can recreate the same Utbetaling after deletion`() = runTest {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()

        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.let {
            assertEquals(HttpStatusCode.Created, it.status)
            assertEquals("/utbetalinger/$uid", it.headers["location"])
        }

        // Oppdater status på utbetaling sånn at vi kan slette den
        withContext(TestRuntime.context) {
            transaction {
                val utbetalingId = UtbetalingId(uid)
                UtbetalingDao
                    .findOrNull(utbetalingId)!!
                    .copy(status = Status.OK)
                    .update(utbetalingId)
            }
        }

        httpClient.delete("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.let {
            assertEquals(HttpStatusCode.NoContent, it.status)
        }

        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.let {
            assertEquals(HttpStatusCode.Created, it.status)
            assertEquals("/utbetalinger/$uid", it.headers["location"])
        }
    }

    @Test
    fun `can create Utbetaling with multiple MND`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(
                UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u),
                UtbetalingsperiodeApi(1.mar, 31.mar, 24_000u),
            ),
        )

        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }

        assertEquals(HttpStatusCode.Created, res.status)
        assertEquals("/utbetalinger/$uid", res.headers["location"])
    }

    @Test
    fun `bad request with different kinds of utbetalingsperiode`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(
                UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u), // <-- must be a single day or..
                UtbetalingsperiodeApi(1.mar, 1.mar, 800u),     // <-- must be sent in a different request 
            ),
        )
        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
        val error = res.body<ApiError>()
        assertEquals(error.msg, "inkonsistens blant datoene i periodene.")
        assertEquals(error.doc, "opprett_en_utbetaling")
    }

    @Test
    fun `periodetype UKEDAG can span over New Years Eve`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.mar,
            periodeType = PeriodeType.UKEDAG,
            perioder = listOf(
                UtbetalingsperiodeApi(31.des, 31.des, 500u),
                UtbetalingsperiodeApi(1.jan, 1.jan, 500u),
            ),
        )
        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }
        assertEquals(HttpStatusCode.Created, res.status)
        assertEquals("/utbetalinger/$uid", res.headers["location"])
    }

    @Test
    fun `periodetype DAG can span over New Years Eve`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.mar,
            periodeType = PeriodeType.DAG,
            perioder = listOf(
                UtbetalingsperiodeApi(31.des, 31.des, 500u),
                UtbetalingsperiodeApi(1.jan, 1.jan, 500u),
            ),
        )
        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }
        assertEquals(HttpStatusCode.Created, res.status)
        assertEquals("/utbetalinger/$uid", res.headers["location"])
    }

    @Test
    fun `periodetype MND can span over New Years Eve`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.mar,
            periodeType = PeriodeType.MND,
            perioder = listOf(
                UtbetalingsperiodeApi(1.des, 31.des, 15_000u),
                UtbetalingsperiodeApi(1.jan, 31.jan, 16_000u),
            ),
        )
        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }
        assertEquals(HttpStatusCode.Created, res.status)
        assertEquals("/utbetalinger/$uid", res.headers["location"])
    }

    @Test
    fun `bad request when dates span over New Years Eve`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.mar,
            periodeType = PeriodeType.EN_GANG,
            perioder = listOf(
                UtbetalingsperiodeApi(
                    15.des,
                    15.jan,
                    30_000u
                ), // <-- must be two requests: [15.12 - 31.12] and [1.1 - 15.1]
            ),
        )

        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
        val error = res.body<ApiError>()
        assertEquals(DocumentedErrors.Async.Utbetaling.ENGANGS_OVER_ÅRSSKIFTE.msg, error.msg)
        assertEquals(DocumentedErrors.Async.Utbetaling.ENGANGS_OVER_ÅRSSKIFTE.doc, error.doc)
    }

    @Test
    fun `bad request when tom is before fom`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 10.des,
            periodeType = PeriodeType.EN_GANG,
            perioder = listOf(
                UtbetalingsperiodeApi(10.des, 9.des, 1_000u),
            ),
        )

        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
        val error = res.body<ApiError>()
        assertEquals(DocumentedErrors.Async.Utbetaling.UGYLDIG_PERIODE.msg, error.msg)
        assertEquals(DocumentedErrors.Async.Utbetaling.UGYLDIG_PERIODE.doc, error.doc)
    }

    @Test
    fun `bad request when fom is duplicate`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 10.des,
            periodeType = PeriodeType.UKEDAG,
            perioder = listOf(
                UtbetalingsperiodeApi(10.des, 10.des, 10_000u),
                UtbetalingsperiodeApi(10.des, 11.des, 10_000u),
            ),
        )

        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
        val error = res.body<ApiError>()
        assertEquals(DocumentedErrors.Async.Utbetaling.DUPLIKATE_PERIODER.msg, error.msg)
        assertEquals(DocumentedErrors.Async.Utbetaling.DUPLIKATE_PERIODER.doc, error.doc)
    }

    @Test
    fun `bad request when tom is duplicate`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 10.des,
            periodeType = PeriodeType.UKEDAG,
            perioder = listOf(
                UtbetalingsperiodeApi(10.des, 10.des, 10_000u),
                UtbetalingsperiodeApi(9.des, 10.des, 1_000u),
            ),
        )

        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
        val error = res.body<ApiError>()
        assertEquals(DocumentedErrors.Async.Utbetaling.DUPLIKATE_PERIODER.msg, error.msg)
        assertEquals(DocumentedErrors.Async.Utbetaling.DUPLIKATE_PERIODER.doc, error.doc)
    }

    @Test
    fun `bad request when beløp is 0`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 10.des,
            periodeType = PeriodeType.UKEDAG,
            perioder = listOf(UtbetalingsperiodeApi(10.des, 10.des, 0u)),
        )

        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
        val error = res.body<ApiError>()
        assertEquals(DocumentedErrors.Async.Utbetaling.UGYLDIG_BELØP.msg, error.msg)
        assertEquals(DocumentedErrors.Async.Utbetaling.UGYLDIG_BELØP.doc, error.doc)
    }

    @Test
    fun `bad request when sakId is over 30 chars`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            sakId = SakId("123456789123456789123456789123456789"),
            vedtakstidspunkt = 10.des,
            periodeType = PeriodeType.UKEDAG,
            perioder = listOf(UtbetalingsperiodeApi(10.des, 10.des, 100u)),
        )

        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
        val error = res.body<ApiError>()
        assertEquals(DocumentedErrors.Async.Utbetaling.UGYLDIG_SAK_ID.msg, error.msg)
        assertEquals(DocumentedErrors.Async.Utbetaling.UGYLDIG_SAK_ID.doc, error.doc)
    }

    @Test
    fun `bad request when behandlingId is over 30 chars`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            behandlingId = BehandlingId("123456789123456789123456789123456789"),
            vedtakstidspunkt = 10.des,
            periodeType = PeriodeType.UKEDAG,
            perioder = listOf(UtbetalingsperiodeApi(10.des, 10.des, 100u)),
        )

        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
        val error = res.body<ApiError>()
        assertEquals(DocumentedErrors.Async.Utbetaling.UGYLDIG_BEHANDLING_ID.msg, error.msg)
        assertEquals(DocumentedErrors.Async.Utbetaling.UGYLDIG_BEHANDLING_ID.doc, error.doc)
    }

    @Test
    fun `can get Utbetaling`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }

        val res = httpClient.get("/utbetalinger/${uid}") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(utbetaling, res.body<UtbetalingApi>())
    }

    @Test
    fun `don't fill in missing days in gap`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 25.mar,
            periodeType = PeriodeType.DAG,
            perioder = listOf(
                UtbetalingsperiodeApi(17.mar, 17.mar, 1000u),
                UtbetalingsperiodeApi(21.mar, 21.mar, 1000u)
            ),
        )

        val uid = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }

        val res = httpClient.get("/utbetalinger/${uid}") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(utbetaling, res.body<UtbetalingApi>())
    }

    @Test
    fun `not found when Utbetaling is missing`() = runTest {
        val uid = UUID.randomUUID()
        val error = httpClient.get("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.also {
            assertEquals(HttpStatusCode.NotFound, it.status)
        }.body<ApiError>()
        assertEquals("Fant ikke utbetaling med uid $uid", error.msg)
        assertEquals(DocumentedErrors.BASE, error.doc)
    }

    @Test
    fun `can update Utbetaling`() = runTest(TestRuntime.context) {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }
        transaction {
            UtbetalingDao
                .findOrNull(UtbetalingId(uid))!!
                .copy(status = Status.OK)
                .update(UtbetalingId(uid))
        }

        val updatedUtbetaling = utbetaling.copy(
            vedtakstidspunkt = 8.des.atStartOfDay(),
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 25_000u)),
        )
        val res = httpClient.put("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(updatedUtbetaling)
        }

        assertEquals(HttpStatusCode.NoContent, res.status)
    }

    @Test
    fun `cant update Utbetaling in flight`() = runTest(TestRuntime.context) {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }
        val updatedUtbetaling = utbetaling.copy(
            vedtakstidspunkt = 8.des.atStartOfDay(),
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 25_000u)),
        )
        val res = httpClient.put("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(updatedUtbetaling)
        }

        assertEquals(HttpStatusCode.Locked, res.status)
    }

    @Test
    fun `can add new utbetaling to sak`() = runTest(TestRuntime.context) {
        val sakId = SakId(RandomOSURId.generate())
        val uid = UUID.randomUUID()


        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(
                UtbetalingApi.dagpenger(
                    sakId = sakId,
                    vedtakstidspunkt = 1.feb,
                    periodeType = PeriodeType.MND,
                    perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
                )
            )
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }

        val uid2 = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(
                UtbetalingApi.dagpenger(
                    sakId = sakId,
                    vedtakstidspunkt = 1.mar,
                    periodeType = PeriodeType.MND,
                    perioder = listOf(UtbetalingsperiodeApi(1.mar, 31.mar, 24_000u)),
                )
            )
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }

        val oppdragTopic = TestRuntime.kafka.getProducer(Topics.oppdrag)
        assertEquals(0, oppdragTopic.uncommitted().size)
        val actual = oppdragTopic.history().singleOrNull { (key, _) -> key == uid2.toString() }?.second
        assertNotNull(actual)
        assertEquals("ENDR", actual.oppdrag110.kodeEndring)
    }

    @Test
    fun `conflict when adding periods that already exists`() = runTest(TestRuntime.context) {
        val person = Personident.random()
        val postDto = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            personident = person,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(postDto)
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }

        transaction {
            UtbetalingDao
                .findOrNull(UtbetalingId(uid))!!
                .copy(status = Status.OK)
                .update(UtbetalingId(uid))
        }

        val putDto = postDto.copy(vedtakstidspunkt = 1.mar.atStartOfDay())
        httpClient.put("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(putDto)
        }.also {
            assertEquals(HttpStatusCode.Conflict, it.status)
        }
    }

    @Test
    fun `bad request when sakId changes`() = runTest(TestRuntime.context) {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }
        transaction {
            UtbetalingDao
                .findOrNull(UtbetalingId(uid))!!
                .copy(status = Status.OK)
                .update(UtbetalingId(uid))
        }

        val updatedUtbetaling = utbetaling.copy(
            sakId = "this shit should not change"
        )
        val error = httpClient.put("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(updatedUtbetaling)
        }.also {
            assertEquals(HttpStatusCode.BadRequest, it.status)
        }.body<ApiError>()
        assertEquals("Kan ikke endre felt 'sakId'", error.msg)
        assertEquals(DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_SAK_ID.doc, error.doc)
    }

    @Test
    fun `bad request when personident changes`() = runTest(TestRuntime.context) {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }
        transaction {
            UtbetalingDao
                .findOrNull(UtbetalingId(uid))!!
                .copy(status = Status.OK)
                .update(UtbetalingId(uid))
        }

        val updatedUtbetaling = utbetaling.copy(
            personident = "this shit should not change"
        )
        val error = httpClient.put("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(updatedUtbetaling)
        }.also {
            assertEquals(HttpStatusCode.BadRequest, it.status)
        }.body<ApiError>()
        assertEquals("Kan ikke endre felt 'personident'", error.msg)
        assertEquals(DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_PERSONIDENT.doc, error.doc)
    }

    @Test
    fun `bad request when stønad changes`() = runTest(TestRuntime.context) {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }
        transaction {
            UtbetalingDao
                .findOrNull(UtbetalingId(uid))!!
                .copy(status = Status.OK)
                .update(UtbetalingId(uid))
        }

        val updatedUtbetaling = utbetaling.copy(
            stønad = StønadTypeDagpenger.DAGPENGERFERIE
        )
        val error = httpClient.put("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(updatedUtbetaling)
        }.also {
            assertEquals(HttpStatusCode.BadRequest, it.status)
        }.body<ApiError>()
        assertEquals("Kan ikke endre felt 'stønad'", error.msg)
        assertEquals(DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_STØNAD.doc, error.doc)
    }

    @Test
    fun `bad request when satstype changes`() = runTest(TestRuntime.context) {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }
        transaction {
            UtbetalingDao
                .findOrNull(UtbetalingId(uid))!!
                .copy(status = Status.OK)
                .update(UtbetalingId(uid))
        }

        val updatedUtbetaling = utbetaling.copy(
            vedtakstidspunkt = 2.feb.atStartOfDay(),
            periodeType = PeriodeType.UKEDAG,
            perioder = listOf(
                UtbetalingsperiodeApi(1.feb, 1.feb, 2_000u),
                UtbetalingsperiodeApi(2.feb, 2.feb, 2_000u),
            ),
        )
        val error = httpClient.put("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(updatedUtbetaling)
        }.also {
            assertEquals(HttpStatusCode.BadRequest, it.status)
        }.body<ApiError>()
        assertEquals("Kan ikke ha forskjellige satstyper", error.msg)
        assertEquals(DocumentedErrors.BASE, error.doc)
    }

    @Test
    fun `can delete Utbetaling`() = runTest(TestRuntime.context) {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }

        transaction {
            UtbetalingDao
                .findOrNull(UtbetalingId(uid))!!
                .copy(status = Status.OK)
                .update(UtbetalingId(uid))
        }

        httpClient.delete("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals(HttpStatusCode.NoContent, it.status)
        }
        httpClient.get("/utbetalinger/${uid}") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.also {
            assertEquals(HttpStatusCode.NotFound, it.status)
            val error = it.body<ApiError>()
            assertEquals("Fant ikke utbetaling med uid $uid", error.msg)
            assertEquals(DocumentedErrors.BASE, error.doc)
        }
    }

    @Test
    fun `can delete with new behandling vedtakstidspunkt beslutter saksbehandler`() = runTest(TestRuntime.context) {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }

        transaction {
            UtbetalingDao
                .findOrNull(UtbetalingId(uid))!!
                .copy(status = Status.OK)
                .update(UtbetalingId(uid))
        }

        httpClient.delete("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling.copy(
                behandlingId = utbetaling.behandlingId + "ny",
                vedtakstidspunkt = LocalDateTime.now().plusDays(1),
                beslutterId = "X654321",
                saksbehandlerId = "Y654321",
            ))
        }.also {
            assertEquals(HttpStatusCode.NoContent, it.status)
        }
        httpClient.get("/utbetalinger/${uid}") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.also {
            assertEquals(HttpStatusCode.NotFound, it.status)
            val error = it.body<ApiError>()
            assertEquals("Fant ikke utbetaling med uid $uid", error.msg)
            assertEquals(DocumentedErrors.BASE, error.doc)
        }
    }

    @Test
    fun `cannot delete if periode is changed`() = runTest(TestRuntime.context) {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }

        transaction {
            UtbetalingDao
                .findOrNull(UtbetalingId(uid))!!
                .copy(status = Status.OK)
                .update(UtbetalingId(uid))
        }

        httpClient.delete("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling.copy(
                perioder = utbetaling.perioder + UtbetalingsperiodeApi(1.mar, 31.mar, 24_000u) 
            ))
        }.also {
            val error = it.body<ApiError>()
            assertEquals("Periodene i utbetalingen samsvarer ikke med det som er lagret hos utsjekk.", error.msg)
            assertEquals(DocumentedErrors.BASE, error.doc)
            assertEquals(HttpStatusCode.BadRequest, it.status)
        }
        httpClient.get("/utbetalinger/${uid}") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }
    }
    

    @Test
    fun `can get UtbetalingStatus`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

        val uid = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals(HttpStatusCode.Created, it.status)
        }

        val status = httpClient.get("/utbetalinger/${uid}/status") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.also {
            assertEquals(HttpStatusCode.OK, it.status)
        }.body<Status>()
        assertEquals(Status.IKKE_PÅBEGYNT, status)
    }

    @Test
    fun `perioder can not be empty`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.mar,
            periodeType = PeriodeType.UKEDAG,
            perioder = listOf(),
        )

        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
        val error = res.body<ApiError>()
        assertEquals(DocumentedErrors.Async.Utbetaling.MANGLER_PERIODER.msg, error.msg)
        assertEquals(DocumentedErrors.Async.Utbetaling.MANGLER_PERIODER.doc, error.doc)
    }
}
