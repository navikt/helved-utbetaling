package urskog

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.test.assertNotNull
import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.ObjectFactory as RootFactory
import no.nav.system.os.entiteter.oppdragskjema.ObjectFactory as OppdragFactory
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.ObjectFactory
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import no.nav.system.os.entiteter.typer.simpletypes.KodeStatusLinje
import org.junit.jupiter.api.Test
import libs.utils.secureLog
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje

class SimuleringTest {
    private var seq: Int = 0
        get() = field++

    @Test
    fun `send to mq`() {
        val uid = UUID.randomUUID().toString()

        TestTopics.simulering.produce(uid) {
            simulering(
                fagsystemId = "$seq",
                fagområde = "AAP",
                oppdragslinjer = listOf(
                    oppdragslinje(
                        delytelsesId = "a",
                        klassekode = "AAPUAA",
                        datoVedtakFom = LocalDate.of(2025, 11, 3),
                        datoVedtakTom = LocalDate.of(2025, 11, 7),
                        typeSats = "DAG",
                        sats = 700L,
                    )
                ),
            )
        }

        TestTopics.simuleringResult.assertThat()
            .hasNumberOfRecordsForKey(uid, 1)
            .hasValueMatching(uid, 0) {
                assertNotNull(it)
            }

        secureLog.warn(TestRuntime.ws.received.first())
    }
}

private val rootFactory = RootFactory()
private val objectFactory = ObjectFactory()
private val oppdragFactory = OppdragFactory()

private fun LocalDate.format() = format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))

private fun simulering(
    oppdragslinjer: List<Oppdragslinje>,
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
    delytelsesId: String,
    sats: Long,
    datoVedtakFom: LocalDate,
    datoVedtakTom: LocalDate,
    typeSats: String, // DAG/DAG7/MND/ENG
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
