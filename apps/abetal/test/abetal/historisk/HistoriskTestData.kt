package abetal.historisk

import java.time.LocalDate
import java.time.LocalDateTime
import models.*
import abetal.*
import models.kontrakter.objectMapper

fun MutableList<DetaljerLinje>.linje(
    behandlingId: BehandlingId,
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    klassekode: String = "HJRIM",
) {
    add(DetaljerLinje(behandlingId.id, fom, tom, null, beløp, klassekode))
}

object Historisk {
    fun utbetaling(
        uid: UtbetalingId,
        sakId: String = "$nextInt",
        behandlingId: String = "$nextInt",
        dryrun: Boolean = false,
        ident: String = "12345678910",
        vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        stønad: StønadTypeHistorisk = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
        periodetype: Periodetype = Periodetype.EN_GANG,
        utbetalinger: () -> List<HistoriskPeriode>,
    ): HistoriskUtbetaling = HistoriskUtbetaling(
        dryrun = dryrun, id = uid.id,
        stønad = stønad,
        behandlingId = behandlingId,
        sakId = sakId,
        personident = ident,
        vedtakstidspunkt = vedtakstidspunkt,
        periodetype = periodetype,
        perioder = utbetalinger(),
    )

    fun periode(
        fom: LocalDate,
        tom: LocalDate,
        beløp: UInt,
    ): List<HistoriskPeriode> {
        return listOf(HistoriskPeriode(fom, tom, beløp))
    }

    fun mottatt(linjer: MutableList<DetaljerLinje>.() -> Unit): StatusReply {
        return StatusReply(
            Status.MOTTATT,
            Detaljer(Fagsystem.HISTORISK, mutableListOf<DetaljerLinje>().apply(linjer))
        )
    }

    fun feilet(error: ApiError): StatusReply {
        return StatusReply(Status.FEILET, null, error)
    }
}

internal fun HistoriskUtbetaling.asBytes() = objectMapper.writeValueAsBytes(this)