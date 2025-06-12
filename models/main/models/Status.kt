package models

import java.time.LocalDate
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import no.trygdeetaten.skjema.oppdrag.OppdragsLinje150
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje


data class StatusReply(
    val status: Status, 
    val detaljer: Detaljer? = null,
    val error: ApiError? = null,
) {
    companion object {
        fun mottatt(oppdrag: Oppdrag): StatusReply = StatusReply(Status.MOTTATT, detaljer(oppdrag))
        fun ok(oppdrag: Oppdrag): StatusReply = StatusReply(Status.OK, detaljer(oppdrag))
        fun ok(oppdrag: Oppdrag, error: ApiError) = StatusReply(Status.OK, detaljer(oppdrag), error)
        fun sendt(oppdrag: Oppdrag): StatusReply = StatusReply(Status.HOS_OPPDRAG, detaljer(oppdrag))
        fun err(oppdrag: Oppdrag, error: ApiError) = StatusReply(Status.FEILET, detaljer(oppdrag), error)
        fun err(error: ApiError) = StatusReply(Status.FEILET, null, error)
    }
}

enum class Status {
    OK,
    FEILET,
    MOTTATT,
    HOS_OPPDRAG,
}

data class Detaljer(
    val ytelse: Fagsystem,
    val linjer: List<DetaljerLinje>,
)

data class DetaljerLinje(
    val behandlingId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val vedtakssats: UInt?,
    val beløp: UInt,
    val klassekode: String,
)


private fun beløp(o: OppdragsLinje150): UInt {
    if (o.kodeStatusLinje == TkodeStatusLinje.OPPH) return 0u
    return o.sats.toLong().toUInt()
}

private fun detaljer(o: Oppdrag): Detaljer {
    return Detaljer(
        ytelse = Fagsystem.fromFagområde(o.oppdrag110.kodeFagomraade.trimEnd()),
        linjer = o.oppdrag110.oppdragsLinje150s.map { linje ->
            DetaljerLinje(
                behandlingId = linje.henvisning.trimEnd(),
                fom = linje.datoVedtakFom.toGregorianCalendar().toZonedDateTime().toLocalDate(),
                tom = linje.datoVedtakTom.toGregorianCalendar().toZonedDateTime().toLocalDate(),
                beløp = beløp(linje),
                vedtakssats = linje.vedtakssats157?.vedtakssats?.toLong()?.toUInt(),
                klassekode = linje.kodeKlassifik.trimEnd(),
            )
        }
    )
}
