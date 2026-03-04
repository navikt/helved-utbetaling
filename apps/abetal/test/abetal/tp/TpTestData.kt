package abetal.tp

import java.time.LocalDate
import models.*

fun MutableList<DetaljerLinje>.linje(
    behandlingId: BehandlingId,
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    klassekode: String = "TPTPAFT",
) {
    add(DetaljerLinje(behandlingId.id, fom, tom, null, beløp, klassekode))
}
