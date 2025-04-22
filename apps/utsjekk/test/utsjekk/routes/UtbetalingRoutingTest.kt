package utsjekk.utbetaling

import TestRuntime
import httpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import libs.postgres.concurrency.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.*
import utsjekk.iverksetting.RandomOSURId
import java.util.UUID
import kotlin.test.assertNotNull

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
        val error = res.body<ApiError.Response>()
        assertEquals(error.msg, "inkonsistens blant datoene i periodene.")
        assertEquals(error.doc, "${DEFAULT_DOC_STR}opprett_en_utbetaling")
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
        val error = res.body<ApiError.Response>()
        assertEquals(error.msg, "periode strekker seg over årsskifte")
        assertEquals(error.field, "tom")
        assertEquals(error.doc, "${DEFAULT_DOC_STR}opprett_en_utbetaling")
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
        val error = res.body<ApiError.Response>()
        assertEquals(error.msg, "fom må være før eller lik tom")
        assertEquals(error.field, "fom")
        assertEquals(error.doc, "${DEFAULT_DOC_STR}opprett_en_utbetaling")
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
        val error = res.body<ApiError.Response>()
        assertEquals(error.msg, "kan ikke sende inn duplikate perioder")
        assertEquals(error.field, "fom")
        assertEquals(error.doc, "${DEFAULT_DOC_STR}opprett_en_utbetaling")
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
        val error = res.body<ApiError.Response>()
        assertEquals(error.msg, "kan ikke sende inn duplikate perioder")
        assertEquals(error.field, "tom")
        assertEquals(error.doc, "${DEFAULT_DOC_STR}opprett_en_utbetaling")
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
        val error = httpClient.get("/utbetalinger/${UUID.randomUUID()}") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.also {
            assertEquals(HttpStatusCode.NotFound, it.status)
        }.body<ApiError.Response>()
        assertEquals("Fant ikke utbetaling", error.msg)
        assertEquals("uid", error.field)
        assertEquals(DEFAULT_DOC_STR, error.doc)
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
        }.body<ApiError.Response>()
        assertEquals("cant change immutable field", error.msg)
        assertEquals("sakId", error.field)
        assertEquals(DEFAULT_DOC_STR, error.doc)
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
        }.body<ApiError.Response>()
        assertEquals("cant change immutable field", error.msg)
        assertEquals("personident", error.field)
        assertEquals(DEFAULT_DOC_STR, error.doc)
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
            stønad = StønadTypeDagpenger.PERMITTERING_ORDINÆR
        )
        val error = httpClient.put("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(updatedUtbetaling)
        }.also {
            assertEquals(HttpStatusCode.BadRequest, it.status)
        }.body<ApiError.Response>()
        assertEquals("cant change immutable field", error.msg)
        assertEquals("stønad", error.field)
        assertEquals(DEFAULT_DOC_STR, error.doc)
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
        }.body<ApiError.Response>()
        assertEquals("can't change the flavour of perioder", error.msg)
        assertEquals("perioder", error.field)
        assertEquals("${DEFAULT_DOC_STR}opprett_en_utbetaling", error.doc)
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
            val error = it.body<ApiError.Response>()
            assertEquals("Fant ikke utbetaling", error.msg)
            assertEquals("uid", error.field)
            assertEquals(DEFAULT_DOC_STR, error.doc)
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
        val error = res.body<ApiError.Response>()
        assertEquals(error.msg, "perioder kan ikke være tom")
        assertEquals(error.field, "perioder")
        assertEquals(error.doc, "${DEFAULT_DOC_STR}opprett_en_utbetaling")
    }
}
