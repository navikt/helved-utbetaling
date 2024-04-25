package oppdrag.iverksetting

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import oppdrag.*
import oppdrag.iverksetting.tilstand.OppdragLager
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OppdragTest {

    @AfterEach
    @BeforeEach
    fun cleanup() = TestRuntime.cleanup()

    @Test
    fun `POST oppdrag svarer 201`() {
        val utbetalingsoppdrag = etUtbetalingsoppdrag()

        testApplication {
            application { server(TestRuntime.config) }

            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken())
                setBody(utbetalingsoppdrag)
            }.also {
                assertEquals(HttpStatusCode.Created, it.status)
            }
        }
    }

    @Test
    fun `POST oppdrag svarer 409 ved uplikat`() {
        val utbetalingsoppdrag = etUtbetalingsoppdrag()

        testApplication {
            application { server(TestRuntime.config) }

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
    }

    @Test
    fun `utbetaling kvitterer ok`() {
        val periode = enUtbetalingsperiode(behandlingId = "p6AF4PE5kd4HxDeIfcs8")
        val utbetaling = etUtbetalingsoppdrag(fagsak = "fhU2NI7YWJDsnZpRjJfz", utbetalingsperiode = arrayOf(periode))

        val app = TestApplication {
            application {
                server(TestRuntime.config)
            }
        }

        app.start()

        TestRuntime.postgres.transaction {
            OppdragLagerRepository.opprettOppdrag(utbetaling.somOppdragLager, it)
        }

        val xml = Resource.read("/kvittering-ok.xml")
        TestRuntime.oppdrag.kvitteringsKø.produce(xml)

        val oppdrag = repeatUntil(::statusChanged) {
            TestRuntime.postgres.transaction {
                OppdragLagerRepository.hentOppdrag(utbetaling.oppdragId, it)
            }
        }

        app.stop()

        assertEquals(OppdragStatus.KVITTERT_OK, oppdrag.status)
    }

    @Test
    fun `utbetaling kvitterer med mangler`() {
        val utbetaling = etUtbetalingsoppdrag(
            fagsak = "vr0nXC3zUNnPl3hDsFyv",
            utbetalingsperiode = arrayOf(enUtbetalingsperiode(behandlingId = "5rNZ1ldCxZ0TdTmsv66"))
        )

        val app = TestApplication {
            application {
                server(TestRuntime.config)
            }
        }

        app.start()

        TestRuntime.postgres.transaction {
            OppdragLagerRepository.opprettOppdrag(utbetaling.somOppdragLager, it)
        }

        val xml = Resource.read("/kvittering-med-mangler.xml")
        TestRuntime.oppdrag.kvitteringsKø.produce(xml)

        val oppdrag = repeatUntil(::statusChanged) {
            TestRuntime.postgres.transaction {
                OppdragLagerRepository.hentOppdrag(utbetaling.oppdragId, it)
            }
        }

        app.stop()

        assertEquals(OppdragStatus.KVITTERT_MED_MANGLER, oppdrag.status)
    }

    @Test
    fun `utbetaling kvitterer funksjonell feil`() {
        val utbetaling = etUtbetalingsoppdrag(
            fagsak = "fU2Vo7NQHKHRD75Hu5LW",
            utbetalingsperiode = arrayOf(enUtbetalingsperiode(behandlingId = "rKrMKcTDUVjiZeGfXc7B"))
        )

        val app = TestApplication {
            application {
                server(TestRuntime.config)
            }
        }

        app.start()

        TestRuntime.postgres.transaction {
            OppdragLagerRepository.opprettOppdrag(utbetaling.somOppdragLager, it)
        }

        val xml = Resource.read("/kvittering-funksjonell-feil.xml")
        TestRuntime.oppdrag.kvitteringsKø.produce(xml)

        val oppdrag = repeatUntil(::statusChanged) {
            TestRuntime.postgres.transaction {
                OppdragLagerRepository.hentOppdrag(utbetaling.oppdragId, it)
            }
        }

        app.stop()

        assertEquals(OppdragStatus.KVITTERT_FUNKSJONELL_FEIL, oppdrag.status)
    }

    @Test
    fun `utbetaling kvitterer teknisk feil`() {
        val utbetaling = etUtbetalingsoppdrag(
            fagsak = "sT9DJxq1zN8ra6EEjeaf",
            utbetalingsperiode = arrayOf(enUtbetalingsperiode(behandlingId = "gIP574Gdi7RHvQdmqKrX"))
        )

        val app = TestApplication {
            application {
                server(TestRuntime.config)
            }
        }

        app.start()

        TestRuntime.postgres.transaction {
            OppdragLagerRepository.opprettOppdrag(utbetaling.somOppdragLager, it)
        }

        val xml = Resource.read("/kvittering-teknisk-feil.xml")
        TestRuntime.oppdrag.kvitteringsKø.produce(xml)

        val oppdrag = repeatUntil(::statusChanged) {
            TestRuntime.postgres.transaction {
                OppdragLagerRepository.hentOppdrag(utbetaling.oppdragId, it)
            }
        }

        app.stop()

        assertEquals(OppdragStatus.KVITTERT_TEKNISK_FEIL, oppdrag.status)
    }

    @Test
    fun `utbetaling kvitterer ukjent`() {
        val utbetaling = etUtbetalingsoppdrag(
            fagsak = "m8dXZI4Iav9BIEIGdtQY",
            utbetalingsperiode = arrayOf(enUtbetalingsperiode(behandlingId = "2i8wmacupHZdfh9Pc0iQ"))
        )

        val app = TestApplication {
            application {
                server(TestRuntime.config)
            }
        }

        app.start()

        TestRuntime.postgres.transaction {
            OppdragLagerRepository.opprettOppdrag(utbetaling.somOppdragLager, it)
        }

        val xml = Resource.read("/kvittering-ukjent-feil.xml")
        TestRuntime.oppdrag.kvitteringsKø.produce(xml)

        val oppdrag = repeatUntil(::statusChanged) {
            TestRuntime.postgres.transaction {
                OppdragLagerRepository.hentOppdrag(utbetaling.oppdragId, it)
            }
        }

        app.stop()

        assertEquals(OppdragStatus.KVITTERT_UKJENT, oppdrag.status)
    }
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
