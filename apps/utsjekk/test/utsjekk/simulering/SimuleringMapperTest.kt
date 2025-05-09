package utsjekk.simulering

import TestData.domain.postering
import TestData.domain.simuleringDetaljer
import TestData.dto.client.postering
import TestData.dto.client.simuleringResponse
import TestData.dto.client.simulertPeriode
import TestData.dto.client.utbetaling
import models.kontrakter.felles.Fagsystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.RandomOSURId
import utsjekk.iverksetting.SakId
import java.time.LocalDate

class SimuleringMapperTest {
    private companion object {
        val sakId = SakId(RandomOSURId.generate())
        val Int.may: LocalDate get() = LocalDate.of(2024, 5, this)
    }

    @Test
    fun `skal lage simuleringsdetaljer for ett fagområde`() {
        val dto = simuleringResponse(
            datoBeregnet = 28.may,
            totalBeløp = 800,
            perioder = listOf(
                simulertPeriode(
                    fom = 1.may,
                    tom = 1.may,
                    utbetalinger = listOf(
                        utbetaling(
                            fagområde = client.Fagområde.TILLST,
                            sakId = sakId,
                            forfall = 28.may,
                            detaljer = listOf(
                                postering(
                                    fom = 1.may,
                                    tom = 1.may,
                                    beløp = 800,
                                    sats = 800.0,
                                )
                            )
                        )
                    )
                )
            )
        )
        val expected = simuleringDetaljer(
            personident = dto.gjelderId,
            datoBeregnet = 28.may,
            totalBeløp = 800,
            perioder = listOf(
                Periode(
                    fom = 1.may,
                    tom = 1.may,
                    posteringer = listOf(
                        postering(
                            fagområde = Fagområde.TILLEGGSSTØNADER,
                            fom = 1.may,
                            tom = 1.may,
                            sakId = sakId,
                            beløp = 800,
                            type = PosteringType.YTELSE,
                        )
                    )
                )
            )
        )
        val actual = SimuleringDetaljer.from(dto, Fagsystem.TILLEGGSSTØNADER)
        assertEquals(expected, actual)
    }

    @Test
    fun `skal lage simuleringsdetaljer for tilelggsstønader inkl arena`() {
        val dto = simuleringResponse(
            datoBeregnet = 28.may,
            totalBeløp = 1300,
            perioder = listOf(
                simulertPeriode(
                    fom = 1.may,
                    tom = 1.may,
                    utbetalinger = listOf(
                        utbetaling(
                            fagområde = client.Fagområde.TILLST,
                            sakId = sakId,
                            forfall = 28.may,
                            detaljer = listOf(
                                postering(
                                    fom = 1.may,
                                    tom = 1.may,
                                    beløp = 800,
                                    sats = 800.0,
                                )
                            )
                        ),
                        utbetaling(
                            fagområde = client.Fagområde.TSTARENA,
                            sakId = SakId("ARENA-ID"),
                            forfall = 15.may,
                            detaljer = listOf(
                                postering(
                                    fom = 15.may,
                                    tom = 15.may,
                                    beløp = 500,
                                    sats = 500.0,
                                )
                            )
                        )
                    )
                )
            )
        )
        val expected = simuleringDetaljer(
            personident = dto.gjelderId,
            datoBeregnet = 28.may,
            totalBeløp = 1300,
            perioder = listOf(
                Periode(
                    fom = 1.may,
                    tom = 1.may,
                    posteringer = listOf(
                        postering(
                            fagområde = Fagområde.TILLEGGSSTØNADER,
                            fom = 1.may,
                            tom = 1.may,
                            sakId = sakId,
                            beløp = 800,
                            type = PosteringType.YTELSE,
                        ),
                        postering(
                            fagområde = Fagområde.TILLEGGSSTØNADER_ARENA,
                            fom = 15.may,
                            tom = 15.may,
                            sakId = SakId("ARENA-ID"),
                            beløp = 500,
                            type = PosteringType.YTELSE,
                        )
                    )
                )
            )
        )
        val actual = SimuleringDetaljer.from(dto, Fagsystem.TILLEGGSSTØNADER)
        assertEquals(expected, actual)
    }

    @Test
    fun `skal lage simuleringsdetaljer for tilleggsstønader med fler ytelser`() {
        val dto = simuleringResponse(
            datoBeregnet = 28.may,
            totalBeløp = 1300,
            perioder = listOf(
                simulertPeriode(
                    fom = 1.may,
                    tom = 1.may,
                    utbetalinger = listOf(
                        utbetaling(
                            fagområde = client.Fagområde.TILLST,
                            sakId = sakId,
                            forfall = 28.may,
                            detaljer = listOf(
                                postering(
                                    fom = 1.may,
                                    tom = 1.may,
                                    beløp = 800,
                                    sats = 800.0,
                                )
                            )
                        ),
                        utbetaling(
                            fagområde = client.Fagområde.DP,
                            sakId = SakId("DAGPENGER-ID"),
                            forfall = 15.may,
                            detaljer = listOf(
                                postering(
                                    fom = 15.may,
                                    tom = 15.may,
                                    beløp = 500,
                                    sats = 500.0,
                                    klassekode = "DPORAS"
                                )
                            )
                        )
                    )
                )
            )
        )
        val expected = simuleringDetaljer(
            personident = dto.gjelderId,
            datoBeregnet = 28.may,
            totalBeløp = 1300,
            perioder = listOf(
                Periode(
                    fom = 1.may,
                    tom = 1.may,
                    posteringer = listOf(
                        postering(
                            fagområde = Fagområde.TILLEGGSSTØNADER,
                            fom = 1.may,
                            tom = 1.may,
                            sakId = sakId,
                            beløp = 800,
                            type = PosteringType.YTELSE,
                        ),
                    )
                )
            )
        )
        val actual = SimuleringDetaljer.from(dto, Fagsystem.TILLEGGSSTØNADER)
        assertEquals(expected, actual)
    }
}
