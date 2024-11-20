package utsjekk.utbetaling

// imports
import TestData
import TestData.random
import TestRuntime
import http
import httpClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import utsjekk.ApiError
import utsjekk.avstemming.nesteVirkedag
import utsjekk.iverksetting.RandomOSURId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertNotNull

private val Int.feb: LocalDate get() = LocalDate.of(2024, 2, this)
private val Int.mar: LocalDate get() = LocalDate.of(2024, 3, this)
private val Int.des: LocalDate get() = LocalDate.of(2024, 12, this)
private val Int.jan: LocalDate get() = LocalDate.of(2025, 3, this)
private val virkedager: (LocalDate) -> LocalDate = { it.nesteVirkedag() }
private val alleDager: (LocalDate) -> LocalDate = { it.plusDays(1) }

class UtbetalingRoutingTest {
    
    @Test
    fun `can create Utbetaling`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
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
    fun `can create Utbetaling with multiple MND`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            listOf(
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
            listOf(
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
        assertEquals(error.doc, "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder")
//        assertEquals(200, http.head(error.doc).status.value) // TODO: enable for å teste at doc lenka virker
    }

    @Test
    fun `bad request with different beløp among days`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.mar,
            listOf(
                UtbetalingsperiodeApi(1.mar, 1.mar, 800u),
                UtbetalingsperiodeApi(2.mar, 2.mar, 800u),
                UtbetalingsperiodeApi(3.mar, 3.mar, 50u), // <-- must be 800u
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
        assertEquals(error.msg, "fant fler ulike beløp blant dagene")
        assertEquals(error.field, "beløp")
        assertEquals(error.doc, "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder")
//        assertEquals(200, http.head(error.doc).status.value) // TODO: enable for å teste at doc lenka virker
    }

    @Test
    fun `bad request when dates span over New Years Eve`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.mar,
            listOf(
                UtbetalingsperiodeApi(15.des, 15.jan, 30_000u), // <-- must be two requests: [15.12 - 31.12] and [1.1 - 15.1]
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
        assertEquals(error.doc, "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder")
//        assertEquals(200, http.head(error.doc).status.value) // TODO: enable for å teste at doc lenka virker
    }

    @Test
    fun `bad request when tom is before fom`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 10.des,
            listOf(
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
        assertEquals(error.doc, "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder")
//        assertEquals(200, http.head(error.doc).status.value) // TODO: enable for å teste at doc lenka virker
    }

    @Test
    fun `bad request when fom is duplicate`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 10.des,
            listOf(
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
        assertEquals(error.doc, "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder")
//        assertEquals(200, http.head(error.doc).status.value) // TODO: enable for å teste at doc lenka virker
    }

    @Test
    fun `bad request when tom is duplicate`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 10.des,
            listOf(
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
        assertEquals(error.doc, "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder")
        // assertEquals(200, http.head(error.doc).status.value) // TODO: enable for å teste at doc lenka virker
    }

    @Test
    fun `can get Utbetaling`() = runTest() { 
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
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

        val res = httpClient.get("/utbetalinger/${uid}") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(utbetaling, res.body<UtbetalingApi>())
    }

    @Test
    fun `not found when Utbetaling is missing`() = runTest() { 
        val error = httpClient.get("/utbetalinger/${UUID.randomUUID()}") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.also {
            assertEquals(HttpStatusCode.NotFound, it.status)
        }.body<ApiError.Response>()
        assertEquals("utbetaling", error.msg)
        assertEquals("uid", error.field)
        assertEquals("https://navikt.github.io/utsjekk-docs/", error.doc)
        assertEquals(200, http.head(error.doc).status.value)
    }

    @Test
    fun `can update Utbetaling`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
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
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )
        val res = httpClient.put("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(updatedUtbetaling)
        }

        assertEquals(HttpStatusCode.NoContent, res.status)
    }

    @Test
    fun `bad request when sakId changes`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
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
        val error = httpClient.put("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(updatedUtbetaling)
        }.also {
            assertEquals(HttpStatusCode.BadRequest, it.status)
        }.body<ApiError.Response>()
        assertEquals("cant change immutable field", error.msg)
        assertEquals("sakId", error.field)
        assertEquals("https://navikt.github.io/utsjekk-docs/", error.doc)
        assertEquals(200, http.head(error.doc).status.value)
    }

    @Test
    fun `bad request when behandlingId changes`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
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
            behandlingId = "this shit should not change"
        )
        val error = httpClient.put("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(updatedUtbetaling)
        }.also {
            assertEquals(HttpStatusCode.BadRequest, it.status)
        }.body<ApiError.Response>()
        assertEquals("cant change immutable field", error.msg)
        assertEquals("behandlingId", error.field)
        assertEquals("https://navikt.github.io/utsjekk-docs/", error.doc)
        assertEquals(200, http.head(error.doc).status.value)
    }

    @Test
    fun `bad request when personident changes`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
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
        val error = httpClient.put("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(updatedUtbetaling)
        }.also {
            assertEquals(HttpStatusCode.BadRequest, it.status)
        }.body<ApiError.Response>()
        assertEquals("cant change immutable field", error.msg)
        assertEquals("personident", error.field)
        assertEquals("https://navikt.github.io/utsjekk-docs/", error.doc)
        assertEquals(200, http.head(error.doc).status.value)
    }

    @Test
    fun `bad request when stønad changes`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
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
        val error = httpClient.put("/utbetalinger/$uid") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(updatedUtbetaling)
        }.also {
            assertEquals(HttpStatusCode.BadRequest, it.status)
        }.body<ApiError.Response>()
        assertEquals("cant change immutable field", error.msg)
        assertEquals("stønad", error.field)
        assertEquals("https://navikt.github.io/utsjekk-docs/", error.doc)
        assertEquals(200, http.head(error.doc).status.value)
    }

    @Test
    fun `bad request when satstype changes`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
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
        assertEquals("cant change the flavour of perioder", error.msg)
        assertEquals("perioder", error.field)
        assertEquals("https://navikt.github.io/utsjekk-docs/utbetalinger/perioder", error.doc)
        // assertEquals(200, http.head(error.doc).status.value)
    }

    @Test
    fun `can delete Utbetaling`() = runTest() {
        val utbetaling = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
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
            assertEquals("utbetaling", error.msg)
            assertEquals("uid", error.field)
            assertEquals("https://navikt.github.io/utsjekk-docs/", error.doc)
            assertEquals(200, http.head(error.doc).status.value)
        }
    }
}

private fun Personident.Companion.random(): Personident {
    return Personident(no.nav.utsjekk.kontrakter.felles.Personident.random().verdi)
}

fun UtbetalingApi.Companion.dagpenger(
    vedtakstidspunkt: LocalDate,
    perioder: List<UtbetalingsperiodeApi>,
    stønad: StønadTypeDagpenger = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
    sakId: SakId = SakId(RandomOSURId.generate()),
    personident: Personident = Personident.random(),
    behandlingId: BehandlingId = BehandlingId(RandomOSURId.generate()),
    saksbehandlerId: Navident = Navident(TestData.DEFAULT_SAKSBEHANDLER),
    beslutterId: Navident = Navident(TestData.DEFAULT_BESLUTTER),
): UtbetalingApi {
    return UtbetalingApi(
        sakId.id,
        behandlingId.id,
        personident.ident,
        vedtakstidspunkt.atStartOfDay(),
        stønad,
        beslutterId.ident,
        saksbehandlerId.ident,
        perioder,
    )
}

private fun UtbetalingsperiodeApi.Companion.expand(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    expansionStrategy: (LocalDate) -> LocalDate,
    betalendeEnhet: NavEnhet? = null,
    fastsattDagpengesats: UInt? = null,
): List<UtbetalingsperiodeApi> = buildList {
    var date = fom
    while (date.isBefore(tom) || date.isEqual(tom)) {
        add(UtbetalingsperiodeApi(date, date, beløp, betalendeEnhet?.enhet, fastsattDagpengesats))
        date = expansionStrategy(date)
    }
}

private fun Utbetalingsperiode.Companion.dagpenger(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    satstype: Satstype,
    betalendeEnhet: NavEnhet? = null,
    fastsattDagpengesats: UInt? = null,
    id: UUID = UUID.randomUUID(),
): Utbetalingsperiode = Utbetalingsperiode(
    id,
    fom,
    tom,
    beløp,
    satstype,
    betalendeEnhet,
    fastsattDagpengesats,
)

fun Utbetaling.Companion.dagpenger(
    vedtakstidspunkt: LocalDate,
    periode: Utbetalingsperiode,
    stønad: StønadTypeDagpenger = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
    sakId: SakId = SakId(RandomOSURId.generate()),
    personident: Personident = Personident.random(),
    behandlingId: BehandlingId = BehandlingId(RandomOSURId.generate()),
    saksbehandlerId: Navident = Navident(TestData.DEFAULT_SAKSBEHANDLER),
    beslutterId: Navident = Navident(TestData.DEFAULT_BESLUTTER),
): Utbetaling = Utbetaling(
    sakId,
    behandlingId,
    personident,
    vedtakstidspunkt.atStartOfDay(),
    stønad,
    beslutterId,
    saksbehandlerId,
    periode,
)

