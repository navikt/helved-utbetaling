package abetal.tp

import java.time.LocalDate
import java.time.LocalDateTime
import models.*
import abetal.*

fun MutableList<DetaljerLinje>.linje(
    behandlingId: BehandlingId,
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    klassekode: String = "TPTPAFT",
) {
    add(DetaljerLinje(behandlingId.id, fom, tom, null, beløp, klassekode))
}

object Tp {
    fun utbetaling(
        sakId: String = "$nextInt",
        behandlingId: String = "$nextInt",
        dryrun: Boolean = false,
        personident: String = "12345678910",
        vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        saksbehandler: String? = null,
        beslutter: String? = null,
        perioder: () -> List<TpPeriode>,
    ): TpUtbetaling = TpUtbetaling(
        sakId = sakId,
        behandlingId = behandlingId,
        dryrun = dryrun,
        personident = personident,
        vedtakstidspunkt = vedtakstidspunkt,
        perioder = perioder(),
        saksbehandler = saksbehandler,
        beslutter = beslutter,
    )

    fun periode(
        meldeperiode: String,
        fom: LocalDate,
        tom: LocalDate,
        beløp: UInt,
        betalendeEnhet: NavEnhet? = null,
        barnetillegg: Boolean = false,
        stønad: StønadTypeTiltakspenger = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
    ): List<TpPeriode> {
        return listOf(TpPeriode(meldeperiode, fom, tom, betalendeEnhet, barnetillegg, beløp, stønad))
    }

    fun mottatt(linjer: MutableList<DetaljerLinje>.() -> Unit): StatusReply {
        return StatusReply(
            Status.MOTTATT,
            Detaljer(Fagsystem.TILTAKSPENGER, mutableListOf<DetaljerLinje>().apply(linjer))
        )
    }

    fun feilet(linjer: MutableList<DetaljerLinje>.() -> Unit): StatusReply {
        return StatusReply(
            Status.FEILET,
            Detaljer(Fagsystem.TILTAKSPENGER, mutableListOf<DetaljerLinje>().apply(linjer))
        )
    }
}

