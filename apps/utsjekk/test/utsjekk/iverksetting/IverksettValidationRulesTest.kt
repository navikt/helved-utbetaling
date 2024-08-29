package utsjekk.iverksetting

import TestData
import io.ktor.http.HttpStatusCode
import no.nav.utsjekk.kontrakter.felles.Satstype
import no.nav.utsjekk.kontrakter.felles.StønadTypeDagpenger
import no.nav.utsjekk.kontrakter.felles.StønadTypeTiltakspenger
import no.nav.utsjekk.kontrakter.iverksett.Ferietillegg
import no.nav.utsjekk.kontrakter.iverksett.ForrigeIverksettingV2Dto
import no.nav.utsjekk.kontrakter.iverksett.StønadsdataDagpengerDto
import no.nav.utsjekk.kontrakter.iverksett.StønadsdataTiltakspengerV2Dto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.ApiError
import java.time.LocalDate
import java.util.UUID

class IverksettValidationRulesTest {
    @Test
    fun `skal få 400 hvis sakId er for lang`() {
        val iverksettDto = TestData.enIverksettDto(sakId = RandomOSURId.generate() + RandomOSURId.generate())

        assertApiFeil(HttpStatusCode.BadRequest) {
            sakIdTilfredsstillerLengdebegrensning(iverksettDto)
        }
    }

    @Test
    fun `skal få 400 hvis behandlingId er for lang`() {
        val iverksettDto = TestData.enIverksettDto(behandlingId = RandomOSURId.generate() + RandomOSURId.generate())

        assertApiFeil(HttpStatusCode.BadRequest) {
            behandlingIdTilfredsstillerLengdebegrensning(iverksettDto)
        }
    }

    @Test
    fun `skal få 400 hvis tom kommer før fom i utbetalingsperiode`() {
        val tmpIverksettDto = TestData.enIverksettDto()
        val iverksettDto =
            tmpIverksettDto.copy(
                vedtak =
                tmpIverksettDto.vedtak.copy(
                    utbetalinger =
                    listOf(
                        TestData.enUtbetalingDto(
                            beløp = 100u,
                            fom = LocalDate.of(2023, 5, 15),
                            tom = LocalDate.of(2023, 5, 5),
                        ),
                    ),
                ),
            )

        assertApiFeil(HttpStatusCode.BadRequest) {
            fraOgMedKommerFørTilOgMedIUtbetalingsperioder(iverksettDto)
        }
    }

    @Test
    fun `Utbetalingsperioder med lik stønadsdata som overlapper skal gi 400`() {
        val tmpIverksettDto = TestData.enIverksettDto()
        val iverksettDto =
            tmpIverksettDto.copy(
                vedtak =
                tmpIverksettDto.vedtak.copy(
                    utbetalinger =
                    listOf(
                        TestData.enUtbetalingDto(
                            beløp = 100u,
                            fom = LocalDate.of(2023, 5, 15),
                            tom = LocalDate.of(2023, 5, 30),
                        ),
                        TestData.enUtbetalingDto(
                            beløp = 100u,
                            fom = LocalDate.of(2023, 5, 20),
                            tom = LocalDate.of(2023, 6, 3),
                        ),
                    ),
                ),
            )

        assertApiFeil(HttpStatusCode.BadRequest) {
            utbetalingsperioderMedLikStønadsdataOverlapperIkkeITid(iverksettDto)
        }
    }

    @Test
    fun `Utbetalingsperioder med ulik stønadsdata som overlapper skal ikke gi ApiFeil`() {
        val tmpIverksettDto = TestData.enIverksettDto()
        val iverksettDto =
            tmpIverksettDto.copy(
                vedtak =
                tmpIverksettDto.vedtak.copy(
                    utbetalinger =
                    listOf(
                        TestData.enUtbetalingDto(
                            beløp = 100u,
                            fom = LocalDate.of(2023, 5, 15),
                            tom = LocalDate.of(2023, 5, 30),
                            stønadsdata =
                            StønadsdataTiltakspengerV2Dto(
                                stønadstype = StønadTypeTiltakspenger.JOBBKLUBB,
                                brukersNavKontor = "4401",
                            ),
                        ),
                        TestData.enUtbetalingDto(
                            beløp = 100u,
                            fom = LocalDate.of(2023, 5, 20),
                            tom = LocalDate.of(2023, 6, 3),
                            stønadsdata =
                            StønadsdataTiltakspengerV2Dto(
                                stønadstype = StønadTypeTiltakspenger.JOBBKLUBB,
                                barnetillegg = true,
                                brukersNavKontor = "4401",
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
        val tmpIverksettDto = TestData.enIverksettDto()
        val enUtbetalingsperiode =
            TestData.enUtbetalingDto(
                beløp = 100u,
                fom = LocalDate.of(2023, 5, 15),
                tom = LocalDate.of(2023, 5, 30),
                stønadsdata =
                StønadsdataTiltakspengerV2Dto(
                    stønadstype = StønadTypeTiltakspenger.JOBBKLUBB,
                    brukersNavKontor = "4401",
                ),
            )
        val iverksettDto =
            tmpIverksettDto.copy(
                vedtak =
                tmpIverksettDto.vedtak.copy(utbetalinger = listOf(enUtbetalingsperiode, enUtbetalingsperiode)),
            )

        assertApiFeil(HttpStatusCode.BadRequest) {
            utbetalingsperioderMedLikStønadsdataOverlapperIkkeITid(iverksettDto)
        }
    }

    @Test
    fun `Ferietillegg til avdød for stønadstype EØS skal gi 400`() {
        val iverksettDto = TestData.enIverksettDto(
            vedtak = TestData.enVedtaksdetaljer(
                utbetalinger = listOf(
                    TestData.enUtbetalingDto(
                        stønadsdata = StønadsdataDagpengerDto(
                            stønadstype = StønadTypeDagpenger.DAGPENGER_EØS,
                            ferietillegg = Ferietillegg.AVDØD
                        ),
                    )
                )
            ),
        )

        assertApiFeil(HttpStatusCode.BadRequest) {
            ingenUtbetalingsperioderHarStønadstypeEØSOgFerietilleggTilAvdød(iverksettDto)
        }
    }

    @Test
    fun `månedssatser må ha hele månedsperioder`() {
        val dto = TestData.enIverksettDto(
            vedtak = TestData.enVedtaksdetaljer(
                utbetalinger = listOf(
                    TestData.enUtbetalingDto(
                        satstype = Satstype.MÅNEDLIG,
                        fom = LocalDate.of(2023, 1, 1),
                        tom = LocalDate.of(2023, 1, 10),
                    )
                )
            )
        )

        assertApiFeil(HttpStatusCode.BadRequest) {
            utbetalingsperioderSamsvarerMedSatstype(dto)
        }
    }

    @Test
    fun `iverksettingId må være satt for forrige om den er satt for nåværende`() {
        val dto = TestData.enIverksettDto(
            iverksettingId = UUID.randomUUID().toString(),
            forrigeIverksetting = ForrigeIverksettingV2Dto(
                behandlingId = RandomOSURId.generate()
            )
        )

        assertApiFeil(HttpStatusCode.BadRequest) {
            iverksettingIdSkalEntenIkkeVæreSattEllerVæreSattForNåværendeOgForrige(dto)
        }
    }

    @Test
    fun `iverksettingId må være satt for nåværende om den er satt for forrige`() {
        val dto = TestData.enIverksettDto(
            forrigeIverksetting = ForrigeIverksettingV2Dto(
                behandlingId = RandomOSURId.generate(),
                iverksettingId = UUID.randomUUID().toString(),
            )
        )

        assertApiFeil(HttpStatusCode.BadRequest) {
            iverksettingIdSkalEntenIkkeVæreSattEllerVæreSattForNåværendeOgForrige(dto)
        }
    }
}


fun assertApiFeil(
    httpStatus: HttpStatusCode,
    block: () -> Any,
) {
    try {
        block()
        error("Forventet ApiFeil, men fikk det ikke")
    } catch (e: ApiError) {
        assertEquals(httpStatus, e.statusCode)
    }
}
