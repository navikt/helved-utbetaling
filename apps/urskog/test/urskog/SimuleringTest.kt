package urskog

import libs.utils.Resource
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import libs.utils.secureLog
import models.ApiError
import models.Fagsystem
import models.Simulering
import models.Simuleringsperiode
import models.SimulertUtbetaling
import models.Status
import models.StatusReply
import models.StønadTypeAAP
import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.entiteter.oppdragskjema.ObjectFactory as OppdragFactory
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import no.nav.system.os.entiteter.typer.simpletypes.KodeStatusLinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.ObjectFactory as RootFactory
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.ObjectFactory
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import org.junit.jupiter.api.Test

class SimuleringTest {
    private var seq: Int = 0
        get() = field++

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

private val rootFactory = RootFactory()
private val objectFactory = ObjectFactory()
private val oppdragFactory = OppdragFactory()

private fun LocalDate.format() = format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))

private fun simulering(
    oppdragslinjer: List<Oppdragslinje> = listOf(oppdragslinje()),
    kodeEndring: String = "NY", // NY/ENDR
    fagområde: String = "AAP",
    fagsystemId: String = "1", // sakid
    oppdragGjelderId: String = "12345678910", // personident 
    saksbehId: String = "Z999999",
    enhet: String? = null, // betalende enhet (lokalkontor)
): SimulerBeregningRequest {
    val oppdrag = objectFactory.createOppdrag().apply {
        this.kodeEndring = kodeEndring
        this.kodeFagomraade = fagområde
        this.fagsystemId = fagsystemId
        this.utbetFrekvens = "MND"
        this.oppdragGjelderId = oppdragGjelderId
        this.datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).format()
        this.saksbehId = saksbehId
        this.enhets.addAll(enheter(enhet))
        oppdragslinjes.addAll(oppdragslinjer)
    }
    return rootFactory.createSimulerBeregningRequest().apply {
        request = objectFactory.createSimulerBeregningRequest().apply {
            this.oppdrag = oppdrag
        }
    }
}

private fun oppdragslinje(
    delytelsesId: String = "DEL 1",
    sats: Long = 700,
    datoVedtakFom: LocalDate = LocalDate.now(),
    datoVedtakTom: LocalDate = LocalDate.now(),
    typeSats: String = "DAG", // DAG/DAG7/MND/ENG
    refDelytelsesId: String? = null,
    kodeEndring: String = "NY", // NY/ENDR
    opphør: LocalDate? = null,
    fagsystemId: String = "1", // sakid
    klassekode: String = "AAPUAA",
    saksbehId: String = "Z999999", // saksbehandler
    beslutterId: String = "Z999999", // beslutter
    utbetalesTilId: String = "12345678910", // personident
): Oppdragslinje {
    val attestant = oppdragFactory.createAttestant().apply {
        this.attestantId = beslutterId
    }
    return objectFactory.createOppdragslinje().apply {
        this.kodeEndringLinje = kodeEndring
        opphør?.let {
            this.kodeStatusLinje = KodeStatusLinje.OPPH
            this.datoStatusFom = opphør.format()
        }
        refDelytelsesId?.let {
            this.refDelytelseId = refDelytelsesId
            this.refFagsystemId = fagsystemId
        }
        this.delytelseId = delytelsesId
        this.kodeKlassifik = klassekode
        this.datoVedtakFom = datoVedtakFom.format()
        this.datoVedtakTom = datoVedtakTom.format()
        this.sats = BigDecimal.valueOf(sats)
        this.fradragTillegg = FradragTillegg.T
        this.typeSats = typeSats
        this.brukKjoreplan = "N"
        this.saksbehId = saksbehId
        this.utbetalesTilId = utbetalesTilId
        this.attestants.add(attestant)
    }
}


private fun enheter(enhet: String? = null): List<Enhet> {
    val bos = oppdragFactory.createEnhet().apply {
        this.enhet = enhet ?: "8020"
        this.typeEnhet = "BOS"
        this.datoEnhetFom = LocalDate.of(1970, 1, 1).format()
    }
    val beh = oppdragFactory.createEnhet().apply {
        this.enhet = "8020"
        this.typeEnhet = "BEH"
        this.datoEnhetFom = LocalDate.of(1970, 1, 1).format()
    }
    return when (enhet) {
        null -> listOf(bos)
        else -> listOf(bos, beh)
    }
}
