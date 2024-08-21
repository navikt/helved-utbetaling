import no.nav.utsjekk.kontrakter.felles.BrukersNavKontor
import no.nav.utsjekk.kontrakter.felles.Personident
import no.nav.utsjekk.kontrakter.felles.StønadTypeDagpenger
import no.nav.utsjekk.kontrakter.iverksett.*
import utsjekk.iverksetting.RandomOSURId
import java.time.LocalDate
import java.time.LocalDateTime

object TestData {

    fun enIverksettDto(): IverksettDto = IverksettDto(
        behandlingId = RandomOSURId.generate(),
        sakId = RandomOSURId.generate(),
        personident = Personident("15507600333"),
        vedtak = VedtaksdetaljerDto(
            vedtakstidspunkt = LocalDateTime.of(2021, 5, 12, 0, 0),
            saksbehandlerId = "A12345",
            beslutterId = "B23456",
            brukersNavKontor = BrukersNavKontor("1234"),
            utbetalinger = listOf(
                enUtbetalingDto(
                    stønadsdata = enDagpengerStønadsdata(StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR, null),
                    beløp = 500,
                    fom = LocalDate.of(2021, 1, 1),
                    tom = LocalDate.of(2021, 12, 31),
                ),
            ),
        ),
    )

    fun enUtbetalingDto(stønadsdata: StønadsdataDto, beløp: Int, fom: LocalDate, tom: LocalDate): UtbetalingDto {
        return UtbetalingDto(beløp, fom, tom, stønadsdata)
    }

    fun enDagpengerStønadsdata(
        type: StønadTypeDagpenger,
        ferietillegg: Ferietillegg?,
    ): StønadsdataDagpengerDto {
        return StønadsdataDagpengerDto(type, ferietillegg)
    }
}
