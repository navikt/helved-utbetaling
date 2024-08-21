package utsjekk.iverksetting

import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.Personident
import no.nav.utsjekk.kontrakter.iverksett.*

fun Iverksetting.Companion.from(dto: IverksettDto): Iverksetting {
    return Iverksetting(
        fagsak = dto.toFagsak(),
        søker = dto.personident.toSøker(),
        behandling = dto.toBehandling(),
        vedtak = dto.vedtak.toDomain()
    )
}

private fun IverksettDto.toFagsak(): Fagsakdetaljer =
    Fagsakdetaljer(
        fagsakId = sakId,
        fagsystem = Fagsystem.DAGPENGER // TODO: utled
    )

private fun Personident.toSøker(): Søker = Søker(personident = verdi)

private fun IverksettDto.toBehandling(): Behandlingsdetaljer =
    Behandlingsdetaljer(
        behandlingId = behandlingId,
        forrigeBehandlingId = forrigeIverksetting?.behandlingId
    )

private fun VedtaksdetaljerDto.toDomain() =
    Vedtaksdetaljer(
        vedtakstidspunkt = vedtakstidspunkt,
        saksbehandlerId = saksbehandlerId,
        beslutterId = beslutterId,
        brukersNavKontor = brukersNavKontor,
        tilkjentYtelse = utbetalinger.toTilkjentYtelse()
    )

private fun List<UtbetalingDto>.toTilkjentYtelse(): TilkjentYtelse {
    val andeler = this.map(AndelTilkjentYtelse::from)

    return when(andeler.size) {
        0 -> TilkjentYtelse(andelerTilkjentYtelse = emptyList())
        else -> TilkjentYtelse(andelerTilkjentYtelse = andeler)
    }
}

fun AndelTilkjentYtelse.Companion.from(dto: UtbetalingDto): AndelTilkjentYtelse {
    return AndelTilkjentYtelse(
        beløp = dto.beløpPerDag,
        periode = Periode(dto.fraOgMedDato, dto.tilOgMedDato),
        stønadsdata = Stønadsdata.from(dto.stønadsdata)
    )
}

fun Stønadsdata.Companion.from(dto: StønadsdataDto): Stønadsdata {
    return when (dto) {
        is StønadsdataDagpengerDto -> StønadsdataDagpenger(dto.stønadstype, dto.ferietillegg)
        is StønadsdataTiltakspengerDto -> StønadsdataTiltakspenger(dto.stønadstype, dto.barnetillegg)
    }
}