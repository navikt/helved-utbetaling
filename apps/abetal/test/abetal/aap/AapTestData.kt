package abetal.aap

import java.time.LocalDate
import models.*

fun MutableList<DetaljerLinje>.linje(
    behandlingId: BehandlingId,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    utbetaltBeløp: UInt = sats,
    klassekode: String = "AAPOR",
) {
    add(DetaljerLinje(behandlingId.id, fom, tom, sats, utbetaltBeløp, klassekode))
}
