package abetal.ts

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
    klassekode: String = "TSTBASISP2-OP",
) {
    add(DetaljerLinje(behandlingId.id, fom, tom, null, beløp, klassekode))
}

fun MutableList<TsPeriode>.periode(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
) {
    add(TsPeriode(fom, tom, beløp))
}

object Ts {
    fun dto(
        sakId: String = "$nextInt",
        behandlingId: String = "$nextInt",
        dryrun: Boolean = false,
        ident: String = "12345678910",
        vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        periodetype: Periodetype = Periodetype.EN_GANG,
        utbetalinger: () -> List<TsUtbetaling>,
    ): TsDto = TsDto(
        dryrun = dryrun,
        sakId = sakId,
        behandlingId = behandlingId,
        personident = ident,
        vedtakstidspunkt = vedtakstidspunkt,
        periodetype = periodetype,
        saksbehandler = null,
        beslutter = null,
        utbetalinger = utbetalinger(),
    )

    fun utbetaling(
        uid: UtbetalingId,
        brukFagområdeTillst: Boolean = true,
        stønad: StønadTypeTilleggsstønader = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
        perioder: MutableList<TsPeriode>.() -> Unit,
    ): List<TsUtbetaling> = listOf(TsUtbetaling(
        id = uid.id,
        stønad = stønad,
        perioder = mutableListOf<TsPeriode>().apply(perioder),
        brukFagområdeTillst = brukFagområdeTillst,
    ))

    fun mottatt(fagsystem: Fagsystem = Fagsystem.TILLSTPB, linjer: MutableList<DetaljerLinje>.() -> Unit): StatusReply {
        return StatusReply(
            Status.MOTTATT,
            Detaljer(fagsystem, mutableListOf<DetaljerLinje>().apply(linjer))
        )
    }

    fun feilet(error: ApiError): StatusReply {
        return StatusReply(Status.FEILET, null, error)
    }
}

internal fun TsDto.asBytes() = objectMapper.writeValueAsBytes(this)