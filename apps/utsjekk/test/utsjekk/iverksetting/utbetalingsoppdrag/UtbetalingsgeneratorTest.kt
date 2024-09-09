package utsjekk.iverksetting.utbetalingsoppdrag

import no.nav.utsjekk.kontrakter.felles.BrukersNavKontor
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.Satstype
import no.nav.utsjekk.kontrakter.felles.StønadTypeDagpenger
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.*
import java.time.LocalDate

class UtbetalingsgeneratorTest {

    @Test
    fun `periode er idempotent`() {
        val andel1 = nyAndel("1", LocalDate.of(2021, 2, 1), LocalDate.of(2021, 3, 31))
        val andel2 = nyAndel("2", LocalDate.of(2021, 2, 1), LocalDate.of(2021, 3, 31))

        val beregnetUtbetalingsoppdrag = Utbetalingsgenerator.lagUtbetalingsoppdrag(
            behandlingsinformasjon = behandlingsinformasjon(),
            nyeAndeler = listOf(andel1, andel2),
            forrigeAndeler = emptyList(),
            sisteAndelPerKjede = mapOf(andel2.stønadsdata.tilKjedenøkkel() to andel2),
        )

        println(beregnetUtbetalingsoppdrag)
    }
}

private fun behandlingsinformasjon(
    saksbehandlerId: String = "ABCDEFG",
    beslutterId: String = "",
    fagsakId: SakId = SakId(""),
    fagsystem: Fagsystem = Fagsystem.DAGPENGER,
    behandlingId: BehandlingId = BehandlingId(""),
    personident: String = "12345678910",
    vedtaksdato: LocalDate = LocalDate.now(),
    brukersNavKontor: BrukersNavKontor? = null,
    iverksettingId: IverksettingId? = null,
): Behandlingsinformasjon = Behandlingsinformasjon(
    saksbehandlerId = saksbehandlerId,
    beslutterId = beslutterId,
    fagsystem = fagsystem,
    fagsakId = fagsakId,
    behandlingId = behandlingId,
    personident = personident,
    brukersNavKontor = brukersNavKontor,
    vedtaksdato = vedtaksdato,
    iverksettingId = iverksettingId,
)

private fun nyAndel(
    andelId: String,
    fom: LocalDate,
    tom: LocalDate,
    beløp: Int = 700,
    satstype: Satstype = Satstype.DAGLIG,
    stønadsdata: Stønadsdata = StønadsdataDagpenger(
        stønadstype = StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR,
        ferietillegg = null,
    ),
    periodeId: Long? = null,
    forrigePeriodeId: Long? = null,
): AndelData = AndelData(
    id = andelId,
    fom = fom,
    tom = tom,
    beløp = beløp,
    satstype = satstype,
    stønadsdata = stønadsdata,
    periodeId = periodeId,
    forrigePeriodeId = forrigePeriodeId,
)

private fun sisteAndelPerKjede(): Map<Kjedenøkkel, AndelData> = TODO("")
