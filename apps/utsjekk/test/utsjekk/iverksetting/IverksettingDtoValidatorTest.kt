package utsjekk.iverksetting

import TestData
import models.kontrakter.felles.*
import models.kontrakter.iverksett.Ferietillegg
import models.kontrakter.iverksett.ForrigeIverksettingV2Dto
import models.kontrakter.iverksett.StønadsdataDagpengerDto
import models.kontrakter.iverksett.StønadsdataTiltakspengerV2Dto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import utsjekk.ApiError
import java.time.LocalDate
import java.util.*

class IverksettingDtoValidatorTest {
    @Test
    fun `skal få 400 hvis sakId er for lang`() {
        val iverksettDto = TestData.dto.iverksetting(sakId = RandomOSURId.generate() + RandomOSURId.generate())

        val ex = assertThrows<ApiError> {
            sakIdTilfredsstillerLengdebegrensning(iverksettDto)
        }
        assertEquals("lengde må være [1 <= ${GyldigSakId.MAKSLENGDE}]", ex.msg)
        assertEquals("sakId", ex.field)
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun `skal få 400 hvis behandlingId er for lang`() {
        val iverksettDto = TestData.dto.iverksetting(behandlingId = RandomOSURId.generate() + RandomOSURId.generate())

        val ex = assertThrows<ApiError> {
            behandlingIdTilfredsstillerLengdebegrensning(iverksettDto)
        }
        assertEquals("lengde må være [1 <= ${GyldigBehandlingId.MAKSLENGDE}]", ex.msg)
        assertEquals("behandlingId", ex.field)
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun `skal få 400 hvis tom kommer før fom i utbetalingsperiode`() {
        val tmpIverksettDto = TestData.dto.iverksetting()
        val iverksettDto =
            tmpIverksettDto.copy(
                vedtak =
                tmpIverksettDto.vedtak.copy(
                    utbetalinger =
                    listOf(
                        TestData.dto.utbetaling(
                            beløp = 100u,
                            fom = LocalDate.of(2023, 5, 15),
                            tom = LocalDate.of(2023, 5, 5),
                        ),
                    ),
                ),
            )

        val ex = assertThrows<ApiError> {
            fraOgMedKommerFørTilOgMedIUtbetalingsperioder(iverksettDto)
        }
        assertEquals("fom må være før eller lik tom", ex.msg)
        assertEquals("fraOgMedDato/tilOgMedDato", ex.field)
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun `Utbetalingsperioder med lik stønadsdata som overlapper skal gi 400`() {
        val tmpIverksettDto = TestData.dto.iverksetting()
        val iverksettDto =
            tmpIverksettDto.copy(
                vedtak =
                tmpIverksettDto.vedtak.copy(
                    utbetalinger =
                    listOf(
                        TestData.dto.utbetaling(
                            beløp = 100u,
                            fom = LocalDate.of(2023, 5, 15),
                            tom = LocalDate.of(2023, 5, 30),
                        ),
                        TestData.dto.utbetaling(
                            beløp = 100u,
                            fom = LocalDate.of(2023, 5, 20),
                            tom = LocalDate.of(2023, 6, 3),
                        ),
                    ),
                ),
            )

        val ex = assertThrows<ApiError> {
            utbetalingsperioderMedLikStønadsdataOverlapperIkkeITid(iverksettDto)
        }
        assertEquals("Utbetalinger inneholder perioder som overlapper i tid", ex.msg)
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun `Utbetalingsperioder med ulik stønadsdata som overlapper skal ikke gi ApiFeil`() {
        val tmpIverksettDto = TestData.dto.iverksetting()
        val iverksettDto =
            tmpIverksettDto.copy(
                vedtak =
                tmpIverksettDto.vedtak.copy(
                    utbetalinger =
                    listOf(
                        TestData.dto.utbetaling(
                            beløp = 100u,
                            fom = LocalDate.of(2023, 5, 15),
                            tom = LocalDate.of(2023, 5, 30),
                            stønadsdata =
                            StønadsdataTiltakspengerV2Dto(
                                stønadstype = StønadTypeTiltakspenger.JOBBKLUBB,
                                brukersNavKontor = "4401",
                                meldekortId = "M1",
                            ),
                        ),
                        TestData.dto.utbetaling(
                            beløp = 100u,
                            fom = LocalDate.of(2023, 5, 20),
                            tom = LocalDate.of(2023, 6, 3),
                            stønadsdata =
                            StønadsdataTiltakspengerV2Dto(
                                stønadstype = StønadTypeTiltakspenger.JOBBKLUBB,
                                barnetillegg = true,
                                brukersNavKontor = "4401",
                                meldekortId = "M1",
                            ),
                        ),
                    ),
                ),
            )

        org.junit.jupiter.api.assertDoesNotThrow {
            utbetalingsperioderMedLikStønadsdataOverlapperIkkeITid(iverksettDto)
        }
    }

    @Test
    fun `Utbetalingsperioder med lik stønadsdata som overlapper skal gi ApiFeil`() {
        val tmpIverksettDto = TestData.dto.iverksetting()
        val enUtbetalingsperiode =
            TestData.dto.utbetaling(
                beløp = 100u,
                fom = LocalDate.of(2023, 5, 15),
                tom = LocalDate.of(2023, 5, 30),
                stønadsdata =
                StønadsdataTiltakspengerV2Dto(
                    stønadstype = StønadTypeTiltakspenger.JOBBKLUBB,
                    brukersNavKontor = "4401",
                    meldekortId = "M1",
                ),
            )
        val iverksettDto =
            tmpIverksettDto.copy(
                vedtak =
                tmpIverksettDto.vedtak.copy(utbetalinger = listOf(enUtbetalingsperiode, enUtbetalingsperiode)),
            )

        val ex = assertThrows<ApiError> {
            utbetalingsperioderMedLikStønadsdataOverlapperIkkeITid(iverksettDto)
        }
        assertEquals("Utbetalinger inneholder perioder som overlapper i tid", ex.msg)
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun `Ferietillegg til avdød for stønadstype EØS skal gi 400`() {
        val iverksettDto = TestData.dto.iverksetting(
            vedtak = TestData.dto.vedtaksdetaljer(
                utbetalinger = listOf(
                    TestData.dto.utbetaling(
                        stønadsdata = StønadsdataDagpengerDto(
                            stønadstype = StønadTypeDagpenger.DAGPENGER_EØS,
                            ferietillegg = Ferietillegg.AVDØD,
                            meldekortId = "M1",
                            fastsattDagsats = 1000u,
                        ),
                    )
                )
            ),
        )

        val ex = assertThrows<ApiError> {
            ingenUtbetalingsperioderHarStønadstypeEØSOgFerietilleggTilAvdød(iverksettDto)
        }
        assertEquals(
            "Ferietillegg til avdød er ikke tillatt for stønadstypen ${StønadTypeDagpenger.DAGPENGER_EØS}",
            ex.msg
        )
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun `månedssatser må ha hele månedsperioder`() {
        val dto = TestData.dto.iverksetting(
            vedtak = TestData.dto.vedtaksdetaljer(
                utbetalinger = listOf(
                    TestData.dto.utbetaling(
                        satstype = Satstype.MÅNEDLIG,
                        fom = LocalDate.of(2023, 1, 1),
                        tom = LocalDate.of(2023, 1, 10),
                    )
                )
            )
        )

        val ex = assertThrows<ApiError> {
            utbetalingsperioderSamsvarerMedSatstype(dto)
        }
        assertEquals("Det finnes utbetalinger med månedssats der periodene ikke samsvarer med hele måneder", ex.msg)
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun `iverksettingId må være satt for forrige om den er satt for nåværende`() {
        val dto = TestData.dto.iverksetting(
            iverksettingId = UUID.randomUUID().toString(),
            forrigeIverksetting = ForrigeIverksettingV2Dto(
                behandlingId = RandomOSURId.generate()
            )
        )

        val ex = assertThrows<ApiError> {
            iverksettingIdSkalEntenIkkeVæreSattEllerVæreSattForNåværendeOgForrige(dto)
        }

        assertEquals("IverksettingId er satt for nåværende iverksetting, men ikke forrige iverksetting", ex.msg)
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun `iverksettingId må være satt for nåværende om den er satt for forrige`() {
        val dto = TestData.dto.iverksetting(
            forrigeIverksetting = ForrigeIverksettingV2Dto(
                behandlingId = RandomOSURId.generate(),
                iverksettingId = UUID.randomUUID().toString(),
            )
        )

        val ex = assertThrows<ApiError> {
            iverksettingIdSkalEntenIkkeVæreSattEllerVæreSattForNåværendeOgForrige(dto)
        }

        assertEquals("IverksettingId er satt for forrige iverksetting, men ikke nåværende iverksetting", ex.msg)
        assertEquals(400, ex.statusCode)
    }
}
