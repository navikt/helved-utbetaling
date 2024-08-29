import no.nav.utsjekk.kontrakter.felles.Personident
import no.nav.utsjekk.kontrakter.felles.Satstype
import no.nav.utsjekk.kontrakter.felles.StønadTypeDagpenger
import no.nav.utsjekk.kontrakter.felles.StønadTypeTilleggsstønader
import no.nav.utsjekk.kontrakter.iverksett.Ferietillegg
import no.nav.utsjekk.kontrakter.iverksett.ForrigeIverksettingV2Dto
import no.nav.utsjekk.kontrakter.iverksett.IverksettV2Dto
import no.nav.utsjekk.kontrakter.iverksett.StønadsdataDagpengerDto
import no.nav.utsjekk.kontrakter.iverksett.StønadsdataDto
import no.nav.utsjekk.kontrakter.iverksett.StønadsdataTilleggsstønaderDto
import no.nav.utsjekk.kontrakter.iverksett.UtbetalingV2Dto
import no.nav.utsjekk.kontrakter.iverksett.VedtaksdetaljerV2Dto
import utsjekk.iverksetting.RandomOSURId
import java.time.LocalDate
import java.time.LocalDateTime

object TestData {

    fun enIverksettDto(
        behandlingId: String = RandomOSURId.generate(),
        sakId: String = RandomOSURId.generate(),
        iverksettingId: String? = null,
        personident: Personident = Personident("15507600333"),
        vedtak: VedtaksdetaljerV2Dto = enVedtaksdetaljer(),
        forrigeIverksetting: ForrigeIverksettingV2Dto? = null,
    ) = IverksettV2Dto(
        behandlingId = behandlingId,
        sakId = sakId,
        iverksettingId = iverksettingId,
        personident = personident,
        vedtak = vedtak,
        forrigeIverksetting = forrigeIverksetting,
    )

    fun enVedtaksdetaljer(
        vedtakstidspunkt: LocalDateTime = LocalDateTime.of(2021, 5, 12, 0, 0),
        saksbehandlerId: String = "A12345",
        beslutterId: String = "B23456",
        utbetalinger: List<UtbetalingV2Dto> = listOf(enUtbetalingDto()),
    ) = VedtaksdetaljerV2Dto(
        vedtakstidspunkt = vedtakstidspunkt,
        saksbehandlerId = saksbehandlerId,
        beslutterId = beslutterId,
        utbetalinger = utbetalinger,
    )

    fun enUtbetalingDto(
        beløp: UInt = 500u,
        satstype: Satstype = Satstype.DAGLIG,
        fom: LocalDate = LocalDate.of(2021, 1, 1),
        tom: LocalDate = LocalDate.of(2021, 12, 31),
        stønadsdata: StønadsdataDto = enDagpengerStønadsdata()
    ) =
        UtbetalingV2Dto(
            beløp = beløp,
            satstype = satstype,
            fraOgMedDato = fom,
            tilOgMedDato = tom,
            stønadsdata = stønadsdata
        )

    fun enDagpengerStønadsdata(
        type: StønadTypeDagpenger = StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR,
        ferietillegg: Ferietillegg? = null,
    ) = StønadsdataDagpengerDto(type, ferietillegg)

    fun enTilleggsstønaderStønadsdata(
        type: StønadTypeTilleggsstønader = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
        brukersNavKontor: String? = null,
    ) = StønadsdataTilleggsstønaderDto(type, brukersNavKontor)
}
