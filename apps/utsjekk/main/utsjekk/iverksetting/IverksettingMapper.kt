package utsjekk.iverksetting

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.utsjekk.kontrakter.felles.BrukersNavKontor
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.Personident
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.iverksett.*

fun Iverksetting.Companion.from(dto: IverksettV2Dto, fagsystem: Fagsystem): Iverksetting {
    return Iverksetting(
        fagsak = dto.toFagsak(fagsystem),
        søker = dto.personident.toSøker(),
        behandling = dto.toBehandling(),
        vedtak = dto.vedtak.toDomain()
    )
}

private fun IverksettV2Dto.toFagsak(fagsystem: Fagsystem): Fagsakdetaljer =
    Fagsakdetaljer(
        fagsakId = SakId(sakId),
        fagsystem = fagsystem
    )

private fun Personident.toSøker(): Søker = Søker(personident = verdi)

private fun IverksettV2Dto.toBehandling(): Behandlingsdetaljer =
    Behandlingsdetaljer(
        behandlingId = BehandlingId(behandlingId),
        forrigeBehandlingId = forrigeIverksetting?.behandlingId?.let(::BehandlingId),
        iverksettingId = iverksettingId?.let(::IverksettingId),
        forrigeIverksettingId = forrigeIverksetting?.iverksettingId?.let(::IverksettingId)
    )

private fun VedtaksdetaljerV2Dto.toDomain() =
    Vedtaksdetaljer(
        vedtakstidspunkt = vedtakstidspunkt,
        saksbehandlerId = saksbehandlerId,
        beslutterId = beslutterId,
        tilkjentYtelse = utbetalinger.toTilkjentYtelse()
    )

fun List<UtbetalingV2Dto>.toTilkjentYtelse(): TilkjentYtelse {
    val andeler = this.map(AndelTilkjentYtelse::from)

    return when (andeler.size) {
        0 -> TilkjentYtelse(andelerTilkjentYtelse = emptyList())
        else -> TilkjentYtelse(andelerTilkjentYtelse = andeler)
    }
}

fun AndelTilkjentYtelse.Companion.from(dto: UtbetalingV2Dto): AndelTilkjentYtelse {
    return AndelTilkjentYtelse(
        beløp = dto.beløp.toInt(),
        satstype = dto.satstype,
        periode = Periode(dto.fraOgMedDato, dto.tilOgMedDato),
        stønadsdata = Stønadsdata.from(dto.stønadsdata)
    )
}

fun Stønadsdata.Companion.from(dto: StønadsdataDto): Stønadsdata {
    return when (dto) {
        is StønadsdataAAPDto -> StønadsdataAAP(stønadstype = dto.stønadstype)
        is StønadsdataDagpengerDto -> StønadsdataDagpenger(
            stønadstype = dto.stønadstype,
            ferietillegg = dto.ferietillegg,
            meldekortId = dto.meldekortId,
            fastsattDagsats = dto.fastsattDagsats,
        )

        is StønadsdataTiltakspengerV2Dto -> StønadsdataTiltakspenger(
            stønadstype = dto.stønadstype,
            barnetillegg = dto.barnetillegg,
            brukersNavKontor = BrukersNavKontor(enhet = dto.brukersNavKontor),
            meldekortId = dto.meldekortId,
        )

        is StønadsdataTilleggsstønaderDto -> StønadsdataTilleggsstønader(
            stønadstype = dto.stønadstype,
            brukersNavKontor = dto.brukersNavKontor?.let(::BrukersNavKontor)
        )
    }
}

fun TilkjentYtelse.toJson(): String = objectMapper.writeValueAsString(this)
fun TilkjentYtelse.Mapper.from(json: String): TilkjentYtelse = objectMapper.readValue(json)

fun OppdragResultat.toJson(): String = objectMapper.writeValueAsString(this)
fun OppdragResultat.Mapper.from(json: String): OppdragResultat = objectMapper.readValue(json)