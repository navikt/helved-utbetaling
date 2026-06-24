package abetal.valp

import abetal.nextInt
import models.*
import kotlinx.serialization.encodeToString
import libs.kafka.JsonSerde
import java.time.*

fun MutableList<DetaljerLinje>.linje(
    behandlingId: BehandlingId,
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    klassekode: String = "TTOHOYUDSTUDREIS",
) {
    add(DetaljerLinje(behandlingId.id, fom, tom, null, beløp, klassekode))
}

object Valp {
    fun utbetaling(
        uid: UtbetalingId,
        sakId: String = "$nextInt",
        behandlingId: String = "$nextInt",
        dryrun: Boolean = false,
        ident: String = "12345678910",
        belop: UInt,
        besluttetTidspunkt: Instant = Instant.now(),
        saksbehandler: String = "teamvalp",
        tilskuddstype: ValpUtbetaling.Tilskuddstype = ValpUtbetaling.Tilskuddstype.STUDIEREISE,
        tiltakskode: ValpUtbetaling.Tiltakskode = ValpUtbetaling.Tiltakskode.HOYERE_UTDANNING,
        kostnadssted: String? = null,
        periode: () -> ValpUtbetaling.Periode,
    ): ValpUtbetaling = ValpUtbetaling(
        dryrun = dryrun, id = uid.id,
        behandlingId = behandlingId,
        sakId = sakId,
        personIdent = ident,
        belop = belop,
        besluttetTidspunkt = besluttetTidspunkt,
        saksbehandler = saksbehandler,
        tilskuddstype = tilskuddstype,
        tiltakskode = tiltakskode,
        kostnadssted = kostnadssted,
        periode = periode()
    )

    fun mottatt(linjer: MutableList<DetaljerLinje>.() -> Unit): StatusReply {
        return StatusReply(
            Status.MOTTATT,
            Detaljer(Fagsystem.VALP, mutableListOf<DetaljerLinje>().apply(linjer))
        )
    }

    fun feilet(error: ApiError): StatusReply {
        return StatusReply(Status.FEILET, null, error)
    }
}

internal fun ValpUtbetaling.asBytes() = libs.kotlinx.KotlinxJson.encodeToString(this).toByteArray()
