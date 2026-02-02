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
    fun `simuler sak 4819 på gammelt format`() {
        TestRuntime.ws.respondWith = Resource.read("/simuler-ts-4819.xml")
        val uid = UUID.randomUUID().toString()

        TestRuntime.topics.simuleringer.produce(uid) {
            simulering(fagområde = "TILLST")
        }

        TestRuntime.topics.dryrunTilleggsstønader.assertThat()
            .has(uid)
            .with(uid, 0) {
                val expected = models.v1.Simulering(
                    listOf(
                        models.v1.OppsummeringForPeriode(
                            fom = LocalDate.of(2025, 8, 1),
                            tom = LocalDate.of(2025, 8, 1),
                            tidligereUtbetalt = 4505,
                            nyUtbetaling = 4293,
                            totalEtterbetaling = 0, 
                            totalFeilutbetaling = 212, 
                        ),
                        models.v1.OppsummeringForPeriode(
                            fom = LocalDate.of(2026, 1, 27),
                            tom = LocalDate.of(2026, 1, 27),
                            tidligereUtbetalt = 0,
                            nyUtbetaling = 4605,
                            totalEtterbetaling = 4605, 
                            totalFeilutbetaling = 0, 
                        )
                    ),
                    models.v1.SimuleringDetaljer(
                        gjelderId = "12345678910", 
                        datoBeregnet = LocalDate.of(2026, 1, 20), 
                        totalBeløp = 4499, 
                        perioder = listOf(
                            models.v1.Periode(
                                fom = LocalDate.of(2025, 8, 1),
                                tom = LocalDate.of(2025, 8, 1),
                                posteringer = listOf(
                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("223"),
                                        fom = LocalDate.of(2025, 8, 1),
                                        tom = LocalDate.of(2025, 8, 1),
                                        beløp = 638,
                                        type = models.v1.PosteringType.FEILUTBETALING,
                                        klassekode = "KL_KODE_JUST_ARBYT"
                                    ),

                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("223"),
                                        fom = LocalDate.of(2025, 8, 1),
                                        tom = LocalDate.of(2025, 8, 1),
                                        beløp = -638,
                                        type = models.v1.PosteringType.YTELSE,
                                        klassekode = "TSTBASISP2-OP"
                                    ),
                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("223"),
                                        fom = LocalDate.of(2025, 8, 1),
                                        tom = LocalDate.of(2025, 8, 1),
                                        beløp = 638,
                                        type = models.v1.PosteringType.MOTPOSTERING,
                                        klassekode = "TBMOTOBS"
                                    ),
                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("223"),
                                        fom = LocalDate.of(2025, 8, 1),
                                        tom = LocalDate.of(2025, 8, 1),
                                        beløp = -638,
                                        type = models.v1.PosteringType.FEILUTBETALING,
                                        klassekode = "KL_KODE_FEIL_ARBYT"
                                    ),
                                )
                            ),
                            models.v1.Periode(
                                fom = LocalDate.of(2025, 8, 11),
                                tom = LocalDate.of(2025, 8, 11),
                                posteringer = listOf(
                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("4819"),
                                        fom = LocalDate.of(2025, 8, 11),
                                        tom = LocalDate.of(2025, 8, 11),
                                        beløp = 3867,
                                        type = models.v1.PosteringType.FEILUTBETALING,
                                        klassekode = "KL_KODE_JUST_ARBYT"
                                    ),
                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("4819"),
                                        fom = LocalDate.of(2025, 8, 11),
                                        tom = LocalDate.of(2025, 8, 11),
                                        beløp = -3867,
                                        type = models.v1.PosteringType.YTELSE,
                                        klassekode = "TSLMASISP2-OP"
                                    ),
                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("4819"),
                                        fom = LocalDate.of(2025, 8, 11),
                                        tom = LocalDate.of(2025, 8, 11),
                                        beløp = 3867,
                                        type = models.v1.PosteringType.MOTPOSTERING,
                                        klassekode = "TBMOTOBS"
                                    ),
                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("4819"),
                                        fom = LocalDate.of(2025, 8, 11),
                                        tom = LocalDate.of(2025, 8, 11),
                                        beløp = -3867,
                                        type = models.v1.PosteringType.FEILUTBETALING,
                                        klassekode = "KL_KODE_FEIL_ARBYT"
                                    ),
                                )
                            ),
                            models.v1.Periode(
                                fom = LocalDate.of(2025, 8, 27),
                                tom = LocalDate.of(2025, 8, 27),
                                posteringer = listOf(
                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("223"),
                                        fom = LocalDate.of(2025, 8, 27),
                                        tom = LocalDate.of(2025, 8, 27),
                                        beløp = -106,
                                        type = models.v1.PosteringType.FEILUTBETALING,
                                        klassekode = "KL_KODE_JUST_ARBYT"
                                    ),
                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("223"),
                                        fom = LocalDate.of(2025, 8, 27),
                                        tom = LocalDate.of(2025, 8, 27),
                                        beløp = 106,
                                        type = models.v1.PosteringType.FEILUTBETALING,
                                        klassekode = "KL_KODE_FEIL_ARBYT"
                                    ),
                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("223"),
                                        fom = LocalDate.of(2025, 8, 27),
                                        tom = LocalDate.of(2025, 8, 27),
                                        beløp = -106,
                                        type = models.v1.PosteringType.MOTPOSTERING,
                                        klassekode = "TBMOTOBS"
                                    ),
                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("223"),
                                        fom = LocalDate.of(2025, 8, 27),
                                        tom = LocalDate.of(2025, 8, 27),
                                        beløp = 106,
                                        type = models.v1.PosteringType.FEILUTBETALING,
                                        klassekode = "KL_KODE_FEIL_ARBYT"
                                    ),
                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("223"),
                                        fom = LocalDate.of(2025, 8, 27),
                                        tom = LocalDate.of(2025, 8, 27),
                                        beløp = -106,
                                        type = models.v1.PosteringType.MOTPOSTERING,
                                        klassekode = "TBMOTOBS"
                                    ),

                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("4819"),
                                        fom = LocalDate.of(2025, 8, 27),
                                        tom = LocalDate.of(2025, 8, 27),
                                        beløp = -4505,
                                        type = models.v1.PosteringType.FEILUTBETALING,
                                        klassekode = "KL_KODE_JUST_ARBYT"
                                    ),
                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("4819"),
                                        fom = LocalDate.of(2025, 8, 27),
                                        tom = LocalDate.of(2025, 8, 27),
                                        beløp = 4505,
                                        type = models.v1.PosteringType.YTELSE,
                                        klassekode = "TSLMASISP2-OP"
                                    ),
                                )
                            ),
                            models.v1.Periode(
                                fom = LocalDate.of(2026, 1, 27),
                                tom = LocalDate.of(2026, 1, 27),
                                posteringer = listOf(
                                    models.v1.Postering(
                                        fagområde = models.v1.Fagområde.TILLST,
                                        sakId = SakId("4819"),
                                        fom = LocalDate.of(2026, 1, 27),
                                        tom = LocalDate.of(2026, 1, 27),
                                        beløp = 4605,
                                        type = models.v1.PosteringType.YTELSE,
                                        klassekode = "TSLMASISP2-OP"
                                    ),
                                )
                            ),
                        ),
                    )
                )
                assertEquals(expected, it)
            }

    }

    @Test
    fun `simuler sak 4819 på nytt format`() {
        TestRuntime.ws.respondWith = Resource.read("/simuler-ts-4819.xml")
        val uid = UUID.randomUUID().toString()

        TestRuntime.topics.simuleringer.produce(uid) {
            simulering(fagområde = "AAP")
        }

        TestRuntime.topics.dryrunAap.assertThat()
            .has(uid)
            .with(uid, 0) {
                val expected = v2.Simulering(
                    perioder = listOf(
                        v2.Simuleringsperiode(
                            fom = LocalDate.of(2025, 8, 1),
                            tom = LocalDate.of(2025, 8, 1),
                            utbetalinger = listOf(
                                v2.SimulertUtbetaling(
                                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                                    sakId = "223",
                                    utbetalesTil = "12345678910",
                                    stønadstype = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                                    tidligereUtbetalt = 638,
                                    nyttBeløp = -638,
                                    posteringer = listOf(
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 1),
                                            tom = LocalDate.of(2025, 8, 1),
                                            beløp = 638,
                                            type = v2.Type.FEIL,
                                            klassekode = "KL_KODE_JUST_ARBYT"
                                        ),
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 1),
                                            tom = LocalDate.of(2025, 8, 1),
                                            beløp = -638,
                                            type = v2.Type.YTEL,
                                            klassekode = "TSTBASISP2-OP"
                                        ),
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 1),
                                            tom = LocalDate.of(2025, 8, 1),
                                            beløp = 638,
                                            type = v2.Type.MOTP,
                                            klassekode = "TBMOTOBS"
                                        ),
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 1),
                                            tom = LocalDate.of(2025, 8, 1),
                                            beløp = -638,
                                            type = v2.Type.FEIL,
                                            klassekode = "KL_KODE_FEIL_ARBYT"
                                        ),
                                    )
                                )
                            )
                        ),
                        v2.Simuleringsperiode(
                            fom = LocalDate.of(2025, 8, 11),
                            tom = LocalDate.of(2025, 8, 11),
                            utbetalinger = listOf(
                                v2.SimulertUtbetaling(
                                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                                    sakId = "4819",
                                    utbetalesTil = "12345678910",
                                    stønadstype = StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER,
                                    tidligereUtbetalt = 3867,
                                    nyttBeløp = -3867,
                                    posteringer = listOf(
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 11),
                                            tom = LocalDate.of(2025, 8, 11),
                                            beløp = 3867,
                                            type = v2.Type.FEIL,
                                            klassekode = "KL_KODE_JUST_ARBYT"
                                        ),
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 11),
                                            tom = LocalDate.of(2025, 8, 11),
                                            beløp = -3867,
                                            type = v2.Type.YTEL,
                                            klassekode = "TSLMASISP2-OP"
                                        ),
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 11),
                                            tom = LocalDate.of(2025, 8, 11),
                                            beløp = 3867,
                                            type = v2.Type.MOTP,
                                            klassekode = "TBMOTOBS"
                                        ),
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 11),
                                            tom = LocalDate.of(2025, 8, 11),
                                            beløp = -3867,
                                            type = v2.Type.FEIL,
                                            klassekode = "KL_KODE_FEIL_ARBYT"
                                        ),
                                    )
                                )
                            )
                        ),
                        v2.Simuleringsperiode(
                            fom = LocalDate.of(2025, 8, 27),
                            tom = LocalDate.of(2025, 8, 27),
                            utbetalinger = listOf(
                                v2.SimulertUtbetaling(
                                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                                    sakId = "223",
                                    utbetalesTil = "12345678910",
                                    stønadstype = null,
                                    tidligereUtbetalt = 0,
                                    nyttBeløp = -212,
                                    posteringer = listOf(
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 27),
                                            tom = LocalDate.of(2025, 8, 27),
                                            beløp = -106,
                                            type = v2.Type.FEIL,
                                            klassekode = "KL_KODE_JUST_ARBYT"
                                        ),
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 27),
                                            tom = LocalDate.of(2025, 8, 27),
                                            beløp = 106,
                                            type = v2.Type.FEIL,
                                            klassekode = "KL_KODE_FEIL_ARBYT"
                                        ),
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 27),
                                            tom = LocalDate.of(2025, 8, 27),
                                            beløp = -106,
                                            type = v2.Type.MOTP,
                                            klassekode = "TBMOTOBS"
                                        ),
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 27),
                                            tom = LocalDate.of(2025, 8, 27),
                                            beløp = 106,
                                            type = v2.Type.FEIL,
                                            klassekode = "KL_KODE_FEIL_ARBYT"
                                        ),
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 27),
                                            tom = LocalDate.of(2025, 8, 27),
                                            beløp = -106,
                                            type = v2.Type.MOTP,
                                            klassekode = "TBMOTOBS"
                                        ),
                                    )
                                ),
                                v2.SimulertUtbetaling(
                                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                                    sakId = "4819",
                                    utbetalesTil = "12345678910",
                                    stønadstype = StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER,
                                    tidligereUtbetalt = 0,
                                    nyttBeløp = 4505,
                                    posteringer = listOf(
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 27),
                                            tom = LocalDate.of(2025, 8, 27),
                                            beløp = -4505,
                                            type = v2.Type.FEIL,
                                            klassekode = "KL_KODE_JUST_ARBYT"
                                        ),
                                        v2.Postering(
                                            fom = LocalDate.of(2025, 8, 27),
                                            tom = LocalDate.of(2025, 8, 27),
                                            beløp = 4505,
                                            type = v2.Type.YTEL,
                                            klassekode = "TSLMASISP2-OP"
                                        ),
                                    )
                                )
                            )
                        ),
                        v2.Simuleringsperiode(
                            fom = LocalDate.of(2026, 1, 27),
                            tom = LocalDate.of(2026, 1, 27),
                            utbetalinger = listOf(
                                v2.SimulertUtbetaling(
                                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                                    sakId = "4819",
                                    utbetalesTil = "12345678910",
                                    stønadstype = StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER,
                                    tidligereUtbetalt = 0,
                                    nyttBeløp = 4605,
                                    posteringer = listOf(
                                        v2.Postering(
                                            fom = LocalDate.of(2026, 1, 27),
                                            tom = LocalDate.of(2026, 1, 27),
                                            beløp = 4605,
                                            type = v2.Type.YTEL,
                                            klassekode = "TSLMASISP2-OP"
                                        ),
                                    )
                                )
                            )
                        ),
                    )
                )
                assertEquals(expected, it)
                // libs.utils.appLog.info(libs.kafka.JsonSerde.jackson.writeValueAsString(it))
            }
    }

    @Test
    fun `send to mq`() {
        TestRuntime.ws.respondWith = Resource.read("/simuler-ok.xml")
        val uid = UUID.randomUUID().toString()

        TestRuntime.topics.simuleringer.produce(uid) {
            simulering()
        }

        TestRuntime.topics.dryrunAap.assertThat().has(uid)
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

