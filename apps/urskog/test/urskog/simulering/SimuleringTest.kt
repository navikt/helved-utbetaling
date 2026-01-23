package urskog.simulering

import libs.utils.Resource
import models.*
import org.junit.jupiter.api.Test
import urskog.TestRuntime
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals

class SimuleringTest {

    @Test
    fun `send to mq`() {
        TestRuntime.ws.respondWith = Resource.read("/simuler-ok.xml")
        val uid = UUID.randomUUID().toString()

        TestRuntime.topics.simuleringer.produce(uid) {
            simulering()
        }

        TestRuntime.topics.dryrunAap.assertThat()
            .has(uid)
            .with(uid, 0) {
                val expected = Simulering(
                    perioder = listOf(
                        Simuleringsperiode(
                            fom = LocalDate.of(2025, 1, 1),
                            tom = LocalDate.of(2025, 1, 3),
                            utbetalinger = listOf(
                                SimulertUtbetaling(
                                    fagsystem = Fagsystem.AAP,
                                    sakId = "25",
                                    utbetalesTil = "12345678910",
                                    stønadstype = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                                    tidligereUtbetalt = 0,
                                    nyttBeløp = 600,
                                )
                            )
                        )
                    )
                )
                assertEquals(expected, it)
            }
    }

    @Test
    fun `parse tom simulering`() {
        TestRuntime.ws.respondWith = Resource.read("/simuler-tom.xml")
        val uid = UUID.randomUUID().toString()

        TestRuntime.topics.simuleringer.produce(uid) {
            simulering()
        }

        TestRuntime.topics.dryrunAap.assertThat().has(uid)
    }

    @Test
    fun `parse soap fault`() {
        TestRuntime.ws.respondWith = Resource.read("/simuler-fault.xml")
        val uid = UUID.randomUUID().toString()

        TestRuntime.topics.simuleringer.produce(uid) {
            simulering()
        }

        TestRuntime.topics.dryrunAap.assertThat().hasNot(uid)

        val expectedStatus = StatusReply(
            status = Status.FEILET,
            error = ApiError(400, "ukjent soap feil Fault(faultcode=SOAP-ENV:Client, faultstring=Malformed SOAP message, detail={cics:FaultDetail={cics:XMLSSParser={cics:ParserResponse=XRC_NOT_WELL_FORMED, cics:ParserReason=00012388, cics:ParserOffset=00000732}}})")
        )

        TestRuntime.topics.status.assertThat()
            .has(uid)
            .has(uid, expectedStatus)
    }

    @Test
    fun `DELYTELSE-ID eller LINJE-ID ved endring finnes ikke`() {
        TestRuntime.ws.respondWith = Resource.read("/simuler-fault-delytelsesid.xml")
        val uid = UUID.randomUUID().toString()

        TestRuntime.topics.simuleringer.produce(uid) {
            simulering()
        }

        TestRuntime.topics.dryrunAap.assertThat().hasNot(uid)

        val expectedStatus = StatusReply(
            status = Status.FEILET,
            error = ApiError(422, "DELYTELSE-ID/LINJE-ID ved endring finnes ikke: 0nMih85oRkaV5FqgMN6E")
        )

        TestRuntime.topics.status.assertThat()
            .has(uid)
            .has(uid, expectedStatus)
    }

    @Test
    fun `antall tegn i saksbehandler er for lang`() {
        TestRuntime.ws.respondWith = Resource.read("/simuler-fault-overflow.xml")
        val uid = UUID.randomUUID().toString()

        TestRuntime.topics.simuleringer.produce(uid) {
            simulering()
        }

        TestRuntime.topics.dryrunAap.assertThat().hasNot(uid)

        val expectedStatus = StatusReply(
            status = Status.FEILET,
            error = ApiError(400, "DFHPI1009 02/09/2025 13:45:58 CICSQ1OS OSW8 21049 XML to data transformation failed. A conversion error (OUTPUT_OVERFLOW) occurred when converting field saksbehId for WEBSERVICE simulerFpServiceWSBinding.")
        )

        TestRuntime.topics.status.assertThat()
            .has(uid)
            .has(uid, expectedStatus)
    }
}

