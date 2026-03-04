package abetal.ts

import java.time.LocalDate
import models.*

fun MutableList<DetaljerLinje>.linje(
    behandlingId: BehandlingId,
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    klassekode: String = "TSTBASISP2-OP",
) {
    add(DetaljerLinje(behandlingId.id, fom, tom, null, beløp, klassekode))
}
