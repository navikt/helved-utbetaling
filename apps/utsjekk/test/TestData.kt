import no.nav.utsjekk.kontrakter.felles.Personident
import no.nav.utsjekk.kontrakter.felles.Satstype
import no.nav.utsjekk.kontrakter.felles.StønadTypeDagpenger
import no.nav.utsjekk.kontrakter.iverksett.Ferietillegg
import no.nav.utsjekk.kontrakter.iverksett.IverksettV2Dto
import no.nav.utsjekk.kontrakter.iverksett.StønadsdataDagpengerDto
import no.nav.utsjekk.kontrakter.iverksett.StønadsdataDto
import no.nav.utsjekk.kontrakter.iverksett.UtbetalingV2Dto
import no.nav.utsjekk.kontrakter.iverksett.VedtaksdetaljerV2Dto
import utsjekk.iverksetting.RandomOSURId
import java.time.LocalDate
import java.time.LocalDateTime

object TestData {

    fun enIverksettDto(): IverksettV2Dto = IverksettV2Dto(
        behandlingId = RandomOSURId.generate(),
        sakId = RandomOSURId.generate(),
        personident = Personident("15507600333"),
        vedtak = VedtaksdetaljerV2Dto(
            vedtakstidspunkt = LocalDateTime.of(2021, 5, 12, 0, 0),
            saksbehandlerId = "A12345",
            beslutterId = "B23456",
            utbetalinger = listOf(
                enUtbetalingDto(
                    beløp = 500u,
                    satstype = Satstype.DAGLIG,
                    fom = LocalDate.of(2021, 1, 1),
                    tom = LocalDate.of(2021, 12, 31),
                    stønadsdata = enDagpengerStønadsdata(StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR, null),
                ),
            ),
        ),
    )

    fun enUtbetalingDto(
        beløp: UInt,
        satstype: Satstype,
        fom: LocalDate,
        tom: LocalDate,
        stønadsdata: StønadsdataDto
    ) =
        UtbetalingV2Dto(
            beløp = beløp,
            satstype = satstype,
            fraOgMedDato = fom,
            tilOgMedDato = tom,
            stønadsdata = stønadsdata
        )

    fun enDagpengerStønadsdata(
        type: StønadTypeDagpenger,
        ferietillegg: Ferietillegg?,
    ) = StønadsdataDagpengerDto(type, ferietillegg)
}
