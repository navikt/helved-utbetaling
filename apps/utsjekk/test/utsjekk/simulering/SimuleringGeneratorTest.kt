package utsjekk.simulering

import TestData.domain.postering
import TestData.domain.simuleringDetaljer
import TestData.dto.api.oppsummeringForPeriode
import TestData.dto.api.simuleringResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.RandomOSURId
import utsjekk.iverksetting.SakId
import java.time.LocalDate

class SimuleringGeneratorTest {
    private val Int.mar: LocalDate get() = LocalDate.of(2024, 3, this)
    private val Int.apr: LocalDate get() = LocalDate.of(2024, 4, this)
    private val Int.may: LocalDate get() = LocalDate.of(2024, 5, this)

    @Test
    fun `skal lage oppsummering for ny utbetaling`() {
        val fremtiden = LocalDate.now().plusMonths(1)
        val sakId = SakId(RandomOSURId.generate())
        val simuleringDetaljer = simuleringDetaljer(
            datoBeregnet = 28.may,
            totalBeløp = 800,
            perioder = listOf(Periode(fremtiden, fremtiden, nyutbetaling(sakId, fremtiden, fremtiden)))
        )

        val expected = simuleringResponse(
            detaljer = simuleringDetaljer,
            oppsummeringer = listOf(
                oppsummeringForPeriode(
                    fom = fremtiden,
                    tom = fremtiden,
                    tidligereUtbetalt = 0,
                    nyUtbetaling = 800,
                    totalEtterbetaling = 0,
                    totalFeilutbetaling = 0
                )
            ),
        )

        val actual = OppsummeringGenerator.lagOppsummering(simuleringDetaljer)
        assertEquals(expected, actual)
    }

    @Test
    fun `skal lage oppsummering for etterbetaling`() {
        val sakId = SakId(RandomOSURId.generate())
        val detaljer = simuleringDetaljer(
            datoBeregnet = 28.may,
            totalBeløp = 100,
            perioder = listOf(Periode(1.may, 1.may, etterbetaling(sakId, 1.may, 1.may)))
        )

        val expected = simuleringResponse(
            detaljer = detaljer,
            oppsummeringer = listOf(
                oppsummeringForPeriode(
                    1.may,
                    1.may,
                    tidligereUtbetalt = 700,
                    nyUtbetaling = 800,
                    totalEtterbetaling = 100,
                    totalFeilutbetaling = 0,
                )
            )
        )

        val actual = OppsummeringGenerator.lagOppsummering(detaljer)
        assertEquals(expected, actual)
    }

    @Test
    fun `skal lage oppsummering for feilutbetaling`() {
        val sakId = SakId(RandomOSURId.generate())
        val detaljer = simuleringDetaljer(
            datoBeregnet = 28.may,
            totalBeløp = 0,
            perioder = listOf(Periode(1.may, 1.may, feilutbetaling(sakId, 1.may, 1.may)))
        )
        val expected = simuleringResponse(
            detaljer = detaljer,
            oppsummeringer = listOf(
                oppsummeringForPeriode(
                    fom = 1.may,
                    tom = 1.may,
                    tidligereUtbetalt = 700,
                    nyUtbetaling = 600,
                    totalEtterbetaling = 0,
                    totalFeilutbetaling = 100,
                )
            )
        )
        val actual = OppsummeringGenerator.lagOppsummering(detaljer)
        assertEquals(expected, actual)
    }

    @Test
    fun `skal lage oppsummering for fler perioder`() {
        val fremtiden = LocalDate.now().plusMonths(2)
        val sakId = SakId(RandomOSURId.generate())
        val detaljer = simuleringDetaljer(
            datoBeregnet = 28.may,
            totalBeløp = 900,
            perioder = listOf(
                Periode(1.mar, 1.mar, feilutbetaling(sakId, 1.mar, 1.mar)),
                Periode(1.apr, 1.apr, etterbetaling(sakId, 1.apr, 1.apr)),
                Periode(fremtiden, fremtiden, nyutbetaling(sakId, fremtiden, fremtiden)),
            )
        )
        val expected = simuleringResponse(
            detaljer = detaljer,
            oppsummeringer = listOf(
                oppsummeringForPeriode(
                    fom = 1.mar,
                    tom = 1.mar,
                    tidligereUtbetalt = 700,
                    nyUtbetaling = 600,
                    totalEtterbetaling = 0,
                    totalFeilutbetaling = 100,
                ),
                oppsummeringForPeriode(
                    fom = 1.apr,
                    tom = 1.apr,
                    tidligereUtbetalt = 700,
                    nyUtbetaling = 800,
                    totalEtterbetaling = 100,
                    totalFeilutbetaling = 0,
                ),
                oppsummeringForPeriode(
                    fom = fremtiden,
                    tom = fremtiden,
                    tidligereUtbetalt = 0,
                    nyUtbetaling = 800,
                    totalEtterbetaling = 0,
                    totalFeilutbetaling = 0,
                )
            )
        )
        val actual = OppsummeringGenerator.lagOppsummering(detaljer)
        assertEquals(expected, actual)
    }
}

private fun nyutbetaling(sakId: SakId, fom: LocalDate, tom: LocalDate) =
    listOf(
        postering(
            type = PosteringType.YTELSE,
            fom = fom,
            tom = tom,
            beløp = 800,
            sakId = sakId,
        ),
    )

private fun etterbetaling(sakId: SakId, fom: LocalDate, tom: LocalDate) =
    listOf(
        postering(
            type = PosteringType.YTELSE,
            fom = fom,
            tom = tom,
            beløp = 800,
            sakId = sakId,
        ),
        postering(
            type = PosteringType.YTELSE,
            fom = fom,
            tom = tom,
            beløp = -700,
            sakId = sakId,
        ),
    )

private fun feilutbetaling(sakId: SakId, fom: LocalDate, tom: LocalDate) =
    listOf(
        postering(
            type = PosteringType.YTELSE,
            fom = fom,
            tom = tom,
            beløp = 100,
            sakId = sakId,
        ),
        postering(
            type = PosteringType.YTELSE,
            fom = fom,
            tom = tom,
            beløp = 600,
            sakId = sakId,
        ),
        postering(
            type = PosteringType.FEILUTBETALING,
            fom = fom,
            tom = tom,
            beløp = 100,
            sakId = sakId,
        ),
        postering(
            type = PosteringType.MOTPOSTERING,
            fom = fom,
            tom = tom,
            beløp = -100,
            sakId = sakId,
        ),
        postering(
            type = PosteringType.YTELSE,
            fom = fom,
            tom = tom,
            beløp = -700,
            sakId = sakId,
        ),
    )
