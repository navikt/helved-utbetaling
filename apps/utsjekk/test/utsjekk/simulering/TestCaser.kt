package utsjekk.simulering

import TestData.domain.postering
import utsjekk.iverksetting.RandomOSURId
import utsjekk.iverksetting.SakId
import java.time.LocalDate

object TestCaser {
    val sakId = SakId(RandomOSURId.generate())

    fun nyUtbetaling(fom: LocalDate, tom: LocalDate, beløp: Int) =
        listOf(
            postering(
                type = domain.PosteringType.YTELSE,
                fom = fom,
                tom = tom,
                beløp = beløp,
                sakId = sakId,
            ),
        )

    fun etterbetaling(fom: LocalDate, tom: LocalDate, gammeltBeløp: Int, nyttBeløp: Int) =
        listOf(
            postering(
                type = domain.PosteringType.YTELSE,
                fom = fom,
                tom = tom,
                beløp = nyttBeløp,
                sakId = sakId,
            ),
            postering(
                type = domain.PosteringType.YTELSE,
                fom = fom,
                tom = tom,
                beløp = -gammeltBeløp,
                sakId = sakId,
            ),
        )

    fun feilutbetaling(fom: LocalDate, tom: LocalDate, gammeltBeløp: Int, nyttBeløp: Int): List<domain.Postering> {
        val feilutbetaltBeløp = gammeltBeløp - nyttBeløp
        return listOf(
            postering(
                type = domain.PosteringType.YTELSE,
                fom = fom,
                tom = tom,
                beløp = feilutbetaltBeløp,
                sakId = sakId,
            ),
            postering(
                type = domain.PosteringType.YTELSE,
                fom = fom,
                tom = tom,
                beløp = nyttBeløp,
                sakId = sakId,
            ),
            postering(
                type = domain.PosteringType.FEILUTBETALING,
                klassekode = KLASSEKODE_FEILUTBETALING,
                fom = fom,
                tom = tom,
                beløp = feilutbetaltBeløp,
                sakId = sakId,
            ),
            postering(
                type = domain.PosteringType.MOTPOSTERING,
                fom = fom,
                tom = tom,
                beløp = -feilutbetaltBeløp,
                sakId = sakId,
            ),
            postering(
                type = domain.PosteringType.YTELSE,
                fom = fom,
                tom = tom,
                beløp = -gammeltBeløp,
                sakId = sakId,
            ),
        )
    }

    fun justeringInnenforSammeMåned(dato: LocalDate, gammeltBeløp: Int, nyttBeløp: Int) =
        listOf(
            domain.Periode(
                posteringer = listOf(
                    postering(
                        type = domain.PosteringType.FEILUTBETALING,
                        klassekode = KLASSEKODE_JUSTERING,
                        fom = dato,
                        tom = dato,
                        beløp = -gammeltBeløp,
                        sakId = sakId,
                    ),
                    postering(
                        type = domain.PosteringType.YTELSE,
                        fom = dato,
                        tom = dato,
                        beløp = nyttBeløp,
                        sakId = sakId,
                    ),
                ),
                fom = dato,
                tom = dato
            ),
            domain.Periode(
                posteringer = listOf(
                    postering(
                        type = domain.PosteringType.FEILUTBETALING,
                        klassekode = KLASSEKODE_JUSTERING,
                        fom = dato.plusDays(1),
                        tom = dato.plusDays(1),
                        beløp = gammeltBeløp,
                        sakId = sakId,
                    ),
                    postering(
                        type = domain.PosteringType.YTELSE,
                        fom = dato.plusDays(1),
                        tom = dato.plusDays(1),
                        beløp = -gammeltBeløp,
                        sakId = sakId,
                    ),
                ),
                fom = dato.plusDays(1),
                tom = dato.plusDays(1)
            )
        )

    fun justeringPåUlikeMåneder(dato: LocalDate, gammeltBeløp: Int, nyttBeløp: Int, nyttBeløpMåned2: Int): List<domain.Periode> {
        val justering = gammeltBeløp - nyttBeløp
        return listOf(
            domain.Periode(
                posteringer = listOf(
                    postering(
                        type = domain.PosteringType.FEILUTBETALING,
                        klassekode = KLASSEKODE_JUSTERING,
                        fom = dato,
                        tom = dato,
                        beløp = justering,
                        sakId = sakId,
                    ),
                    postering(
                        type = domain.PosteringType.YTELSE,
                        fom = dato,
                        tom = dato,
                        beløp = nyttBeløp,
                        sakId = sakId,
                    ),
                    postering(
                        type = domain.PosteringType.YTELSE,
                        fom = dato,
                        tom = dato,
                        beløp = -gammeltBeløp,
                        sakId = sakId,
                    ),
                ),
                fom = dato,
                tom = dato
            ),
            domain.Periode(
                posteringer = listOf(
                    postering(
                        type = domain.PosteringType.FEILUTBETALING,
                        klassekode = KLASSEKODE_JUSTERING,
                        fom = dato.plusMonths(1),
                        tom = dato.plusMonths(1),
                        beløp = -justering,
                        sakId = sakId,
                    ),
                    postering(
                        type = domain.PosteringType.YTELSE,
                        fom = dato.plusMonths(1),
                        tom = dato.plusMonths(1),
                        beløp = nyttBeløpMåned2,
                        sakId = sakId,
                    ),
                ),
                fom = dato.plusMonths(1),
                tom = dato.plusMonths(1)
            )
        )
    }
}
