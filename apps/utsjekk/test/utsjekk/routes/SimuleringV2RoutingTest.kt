package utsjekk.utbetaling

import TestRuntime
import httpClient
import io.ktor.client.call.body
import io.ktor.client.statement.*
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.ApiError
import utsjekk.DEFAULT_DOC_STR
import utsjekk.iverksetting.RandomOSURId

class SimuleringRoutingTest {

  @Test
  fun `kan simulere enkel utbetaling`() = runTest {
    val utbetaling = UtbetalingApi.dagpenger(
        vedtakstidspunkt = 1.feb,
        periodeType = PeriodeType.MND,
        perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
    )

    val uid = UUID.randomUUID()
    val res = httpClient.post("/utbetalinger/$uid/simuler") {
        bearerAuth(TestRuntime.azure.generateToken())
        contentType(ContentType.Application.Json)
        setBody(utbetaling)
    }

    assertEquals(HttpStatusCode.OK, res.status)
  }

  @Test
  fun `can simulate Utbetaling with multiple MND`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(
                UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u),
                UtbetalingsperiodeApi(1.mar, 31.mar, 24_000u),
            ),
        )

        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid/simuler") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }

        assertEquals(HttpStatusCode.OK, res.status)
      }

    @Test
    fun `bad request with different kinds of utbetalingsperiode`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(
                UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u), // <-- must be a single day or..
                UtbetalingsperiodeApi(1.mar, 1.mar, 800u), // <-- must be sent in a different request
            ),
        )
        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid/simuler") {
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
                UtbetalingsperiodeApi(15.des, 15.jan, 30_000u), // <-- must be two requests: [15.12 - 31.12] and [1.1 - 15.1]
            ),
        )

        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid/simuler") {
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
            periodeType = PeriodeType.UKEDAG,
            perioder = listOf(
                UtbetalingsperiodeApi(10.des, 9.des, 1_000u),
            ),
        )

        val uid = UUID.randomUUID()
        val res = httpClient.post("/utbetalinger/$uid/simuler") {
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
        val res = httpClient.post("/utbetalinger/$uid/simuler") {
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
        val res = httpClient.post("/utbetalinger/$uid/simuler") {
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
    fun `can simulate update of Utbetaling`() = runTest(TestRuntime.context) {
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
        val res = httpClient.post("/utbetalinger/$uid/simuler") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(updatedUtbetaling)
        }

        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun `can simulate new utbetaling to sak`() = runTest(TestRuntime.context) {
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
        httpClient.post("/utbetalinger/$uid2/simuler") {
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
            assertEquals(HttpStatusCode.OK, it.status)
        }
    }

    @Test
    fun `conflict when simlating new period that already exist`() = runTest(TestRuntime.context) {
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

        val putDto = postDto.copy(vedtakstidspunkt = 1.mar.atStartOfDay())
        httpClient.post("/utbetalinger/$uid/simuler") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(putDto)
        }.also {
            assertEquals(HttpStatusCode.Conflict, it.status)
        }
    }

    @Test
    fun `bad request when sakId changes`() = runTest() {
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
            sakId = "this shit should not change"
        )
        val error = httpClient.post("/utbetalinger/$uid/simuler") {
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
    fun `bad request when personident changes`() = runTest() {
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
            personident = "this shit should not change"
        )
        val error = httpClient.post("/utbetalinger/$uid/simuler") {
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
    fun `bad request when stønad changes`() = runTest() {
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
            stønad = StønadTypeDagpenger.PERMITTERING_ORDINÆR
        )
        val error = httpClient.post("/utbetalinger/$uid/simuler") {
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
    fun `bad request when satstype changes`() = runTest() {
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
            vedtakstidspunkt = 2.feb.atStartOfDay(),
            periodeType = PeriodeType.UKEDAG,
            perioder = listOf(
                UtbetalingsperiodeApi(1.feb, 1.feb, 2_000u),
                UtbetalingsperiodeApi(2.feb, 2.feb, 2_000u),
            ),
        )
        val error = httpClient.post("/utbetalinger/$uid/simuler") {
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
    fun `can delete Utbetaling`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
            periodeType = PeriodeType.MND,
        )

        val uid = UUID.randomUUID()
        httpClient.post("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals("", it.bodyAsText())
            assertEquals(HttpStatusCode.Created, it.status)
        }
        httpClient.delete("/utbetalinger/$uid/simuler") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }.also {
            assertEquals(HttpStatusCode.OK, it.status)
        }
    }
}

