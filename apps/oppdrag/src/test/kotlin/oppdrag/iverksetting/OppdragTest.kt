package oppdrag.iverksetting

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import libs.utils.Resource
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatusDto
import oppdrag.*
import oppdrag.iverksetting.domene.Kvitteringstatus
import oppdrag.iverksetting.tilstand.OppdragId
import oppdrag.iverksetting.tilstand.OppdragLager
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OppdragTest {

    @BeforeEach
    @AfterEach
    fun cleanup() = TestRuntime.cleanup()

    @Nested
    inner class Routes {

        @Test
        fun `POST oppdrag svarer 201`(): Unit = runBlocking {
            val utbetalingsoppdrag = etUtbetalingsoppdrag()

            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken())
                setBody(utbetalingsoppdrag)
            }.also {
                assertEquals(HttpStatusCode.Created, it.status)
            }
        }

        @Test
        fun `POST oppdrag svarer 409 ved uplikat`(): Unit = runBlocking {
            val utbetalingsoppdrag = etUtbetalingsoppdrag()

            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken())
                setBody(utbetalingsoppdrag)
            }.also {
                assertEquals(HttpStatusCode.Created, it.status)
            }

            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken())
                setBody(utbetalingsoppdrag)
            }.also {
                assertEquals(HttpStatusCode.Conflict, it.status)
            }
        }

        @Test
        fun `POST oppdragPaaNytt svarer 201`(): Unit = runBlocking {
            val utbetalingsoppdrag = etUtbetalingsoppdrag()


            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken())
                setBody(utbetalingsoppdrag)
            }.also {
                assertEquals(HttpStatusCode.Created, it.status)
            }

            TestRuntime.postgres.transaction {
                OppdragLagerRepository.hentAlleVersjonerAvOppdrag(utbetalingsoppdrag.oppdragId, it)
            }.also {
                assertEquals(0, it.single().versjon)
            }

            httpClient.post("/oppdragPaaNytt/1") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken())
                setBody(utbetalingsoppdrag)
            }.also {
                assertEquals(HttpStatusCode.Created, it.status)
            }

            TestRuntime.postgres.transaction {
                OppdragLagerRepository.hentAlleVersjonerAvOppdrag(utbetalingsoppdrag.oppdragId, it)
            }.also {
                assertEquals(2, it.size)
                assertEquals(1, it.last().versjon)
            }
        }

        @Test
        fun `POST status svarer 200 når oppdrag finnes`(): Unit = runBlocking {
            val utbetalingsoppdrag = etUtbetalingsoppdrag()

            TestRuntime.postgres.transaction {
                OppdragLagerRepository.opprettOppdrag(utbetalingsoppdrag.somOppdragLager, it)
            }

            fun OppdragId.toDto(): OppdragIdDto = OppdragIdDto(
                fagsystem = fagsystem,
                sakId = fagsakId,
                behandlingId = behandlingId,
                iverksettingId = iverksettingId
            )

            httpClient.post("/status") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken())
                setBody(utbetalingsoppdrag.oppdragId.toDto())
            }.also {
                val actual = it.body<OppdragStatusDto>()
                val expected = OppdragStatusDto(OppdragStatus.LAGT_PÅ_KØ, null)
                assertEquals(HttpStatusCode.OK, it.status)
                assertEquals(expected, actual)
            }
        }

        @Test
        fun `POST status svarer 404 når oppdrag ikke finnes`(): Unit = runBlocking {
            val utbetalingsoppdrag = etUtbetalingsoppdrag()

            fun OppdragId.toDto(): OppdragIdDto = OppdragIdDto(
                fagsystem = fagsystem,
                sakId = fagsakId,
                behandlingId = behandlingId,
                iverksettingId = iverksettingId
            )

            httpClient.post("/status") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken())
                setBody(utbetalingsoppdrag.oppdragId.toDto())
            }.also {
                assertEquals(HttpStatusCode.NotFound, it.status)
            }
        }

        @Test
        fun `sender inn 10 oppdrag sammtidig`() = runTest {
            repeat(10) {
                launch {
                    httpClient.post("/oppdrag") {
                        contentType(ContentType.Application.Json)
                        bearerAuth(TestRuntime.azure.generateToken())
                        setBody(etUtbetalingsoppdrag())
                    }.also {
                        assertEquals(HttpStatusCode.Created, it.status)
                    }
                }
            }
        }
    }

    @Nested
    inner class Kvittering {

        @Test
        fun `utbetaling kvitterer ok`(): Unit = runBlocking {
            val periode = enUtbetalingsperiode(behandlingId = "p6AF4PE5kd4HxDeIfcs8")
            val utbetaling =
                etUtbetalingsoppdrag(fagsak = "fhU2NI7YWJDsnZpRjJfz", utbetalingsperiode = arrayOf(periode))

            TestRuntime.postgres.transaction {
                OppdragLagerRepository.opprettOppdrag(utbetaling.somOppdragLager, it)
            }

            val xml = Resource.read("/xml/kvittering-ok.xml")
            TestRuntime.oppdrag.kvitteringsKø.produce(xml)

            val oppdrag = repeatUntil(::statusChanged) {
                TestRuntime.postgres.transaction {
                    OppdragLagerRepository.hentOppdrag(utbetaling.oppdragId, it)
                }
            }

            assertEquals(OppdragStatus.KVITTERT_OK, oppdrag.status)
            assertEquals(Kvitteringstatus.OK, oppdrag.kvitteringstatus)
        }

        @Test
        fun `utbetaling kvitterer med mangler`(): Unit = runBlocking {
            val utbetaling = etUtbetalingsoppdrag(
                fagsak = "vr0nXC3zUNnPl3hDsFyv",
                utbetalingsperiode = arrayOf(enUtbetalingsperiode(behandlingId = "5rNZ1ldCxZ0TdTmsv66"))
            )

            TestRuntime.postgres.transaction {
                OppdragLagerRepository.opprettOppdrag(utbetaling.somOppdragLager, it)
            }

            val xml = Resource.read("/xml/kvittering-med-mangler.xml")
            TestRuntime.oppdrag.kvitteringsKø.produce(xml)

            val oppdrag = repeatUntil(::statusChanged) {
                TestRuntime.postgres.transaction {
                    OppdragLagerRepository.hentOppdrag(utbetaling.oppdragId, it)
                }
            }

            assertEquals(OppdragStatus.KVITTERT_MED_MANGLER, oppdrag.status)
            assertEquals(Kvitteringstatus.AKSEPTERT_MEN_NOE_ER_FEIL, oppdrag.kvitteringstatus)
        }

        @Test
        fun `utbetaling kvitterer funksjonell feil`(): Unit = runBlocking {
            val utbetaling = etUtbetalingsoppdrag(
                fagsak = "fU2Vo7NQHKHRD75Hu5LW",
                utbetalingsperiode = arrayOf(enUtbetalingsperiode(behandlingId = "rKrMKcTDUVjiZeGfXc7B"))
            )

            TestRuntime.postgres.transaction {
                OppdragLagerRepository.opprettOppdrag(utbetaling.somOppdragLager, it)
            }

            val xml = Resource.read("/xml/kvittering-funksjonell-feil.xml")
            TestRuntime.oppdrag.kvitteringsKø.produce(xml)

            val oppdrag = repeatUntil(::statusChanged) {
                TestRuntime.postgres.transaction {
                    OppdragLagerRepository.hentOppdrag(utbetaling.oppdragId, it)
                }
            }

            assertEquals(OppdragStatus.KVITTERT_FUNKSJONELL_FEIL, oppdrag.status)
            assertEquals(Kvitteringstatus.AVVIST_FUNKSJONELLE_FEIL, oppdrag.kvitteringstatus)
        }

        @Test
        fun `utbetaling kvitterer teknisk feil`(): Unit = runBlocking {
            val utbetaling = etUtbetalingsoppdrag(
                fagsak = "sT9DJxq1zN8ra6EEjeaf",
                utbetalingsperiode = arrayOf(enUtbetalingsperiode(behandlingId = "gIP574Gdi7RHvQdmqKrX"))
            )

            TestRuntime.postgres.transaction {
                OppdragLagerRepository.opprettOppdrag(utbetaling.somOppdragLager, it)
            }

            val xml = Resource.read("/xml/kvittering-teknisk-feil.xml")
            TestRuntime.oppdrag.kvitteringsKø.produce(xml)

            val oppdrag = repeatUntil(::statusChanged) {
                TestRuntime.postgres.transaction {
                    OppdragLagerRepository.hentOppdrag(utbetaling.oppdragId, it)
                }
            }

            assertEquals(OppdragStatus.KVITTERT_TEKNISK_FEIL, oppdrag.status)
            assertEquals(Kvitteringstatus.AVVIST_TEKNISK_FEIL, oppdrag.kvitteringstatus)
        }

        @Test
        fun `utbetaling kvitterer ukjent`(): Unit = runBlocking {
            val utbetaling = etUtbetalingsoppdrag(
                fagsak = "m8dXZI4Iav9BIEIGdtQY",
                utbetalingsperiode = arrayOf(enUtbetalingsperiode(behandlingId = "2i8wmacupHZdfh9Pc0iQ"))
            )

            TestRuntime.postgres.transaction {
                OppdragLagerRepository.opprettOppdrag(utbetaling.somOppdragLager, it)
            }

            val xml = Resource.read("/xml/kvittering-ukjent-feil.xml")
            TestRuntime.oppdrag.kvitteringsKø.produce(xml)

            val oppdrag = repeatUntil(::statusChanged) {
                TestRuntime.postgres.transaction {
                    OppdragLagerRepository.hentOppdrag(utbetaling.oppdragId, it)
                }
            }

            assertEquals(OppdragStatus.KVITTERT_UKJENT, oppdrag.status)
            assertEquals(Kvitteringstatus.UKJENT, oppdrag.kvitteringstatus)
        }
    }
}

private val OppdragLager.kvitteringstatus: Kvitteringstatus
    get() = when (kvitteringsmelding?.alvorlighetsgrad) {
        "00" -> Kvitteringstatus.OK
        "04" -> Kvitteringstatus.AKSEPTERT_MEN_NOE_ER_FEIL
        "08" -> Kvitteringstatus.AVVIST_FUNKSJONELLE_FEIL
        "12" -> Kvitteringstatus.AVVIST_TEKNISK_FEIL
        else -> Kvitteringstatus.UKJENT
    }

private fun statusChanged(oppdrag: OppdragLager): Boolean {
    return oppdrag.status != OppdragStatus.LAGT_PÅ_KØ
}

private fun <T> repeatUntil(
    predicate: (T) -> Boolean,
    timeoutMs: Long = 1_000,
    action: () -> T,
): T = runBlocking {
    withTimeout(timeoutMs) {
        var result = action()
        while (!predicate(result)) result = action()
        result
    }
}
