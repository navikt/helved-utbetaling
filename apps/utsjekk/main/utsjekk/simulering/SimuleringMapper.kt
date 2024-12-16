package utsjekk.simulering

import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.Personident
import no.nav.utsjekk.kontrakter.felles.Satstype
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsperiode
import utsjekk.iverksetting.*
import java.time.LocalDate

fun Simulering.Mapper.from(dto: api.SimuleringRequest, fagsystem: Fagsystem) = Simulering(
    behandlingsinformasjon = Behandlingsinformasjon(
        saksbehandlerId = dto.saksbehandlerId,
        beslutterId = dto.saksbehandlerId,
        fagsakId = SakId(dto.sakId),
        fagsystem = fagsystem,
        behandlingId = BehandlingId(dto.behandlingId),
        personident = dto.personident.verdi,
        vedtaksdato = LocalDate.now(),
        brukersNavKontor = null,
        iverksettingId = null,
    ),
    nyTilkjentYtelse = dto.utbetalinger.toTilkjentYtelse(),
    forrigeIverksetting = dto.forrigeIverksetting?.let {
        ForrigeIverksetting(
            behandlingId = BehandlingId(it.behandlingId),
            iverksettingId = it.iverksettingId?.let(::IverksettingId),
        )
    },
)

fun SimuleringDetaljer.Mapper.from(dto: client.SimuleringResponse, fagsystem: Fagsystem) = SimuleringDetaljer(
    gjelderId = dto.gjelderId,
    datoBeregnet = dto.datoBeregnet,
    totalBeløp = dto.totalBelop,
    perioder = dto.perioder.map { p ->
        Periode(
            fom = p.fom,
            tom = p.tom,
            posteringer = p.utbetalinger.filter { fagsystem.inFagområde(it.fagområde) }.flatMap { it.into() },
        )
    },
)

fun client.SimuleringRequest.Mapper.from(domain: Utbetalingsoppdrag) = client.SimuleringRequest(
    sakId = domain.saksnummer,
    fagområde = domain.fagsystem.into(),
    personident = Personident(domain.aktør),
    erFørsteUtbetalingPåSak = domain.erFørsteUtbetalingPåSak,
    saksbehandler = domain.saksbehandlerId,
    utbetalingsperioder = domain.utbetalingsperiode.map(client.Utbetalingsperiode::from),
)

fun client.Utbetalingsperiode.Mapper.from(domain: Utbetalingsperiode) = client.Utbetalingsperiode(
    periodeId = domain.periodeId.toString(),
    forrigePeriodeId = domain.forrigePeriodeId?.toString(),
    erEndringPåEksisterendePeriode = domain.erEndringPåEksisterendePeriode,
    klassekode = domain.klassifisering,
    fom = domain.fom,
    tom = domain.tom,
    sats = domain.sats.toInt(),
    satstype = domain.satstype.into(),
    opphør = domain.opphør?.let { client.Opphør(it.fom) },
    utbetalesTil = domain.utbetalesTil,
    fastsattDagsats = domain.fastsattDagsats,
)

fun client.Utbetaling.into(): List<Postering> {
    return detaljer.map { postering ->
        Postering(
            fagområde = Fagområde.from(fagområde),
            sakId = SakId(fagSystemId),
            fom = postering.faktiskFom,
            tom = postering.faktiskTom,
            beløp = postering.belop,
            klassekode = postering.klassekode,
            type = PosteringType.from(postering.type)
        )
    }
}

fun PosteringType.Mapper.from(dto: client.PosteringType) = when (dto) {
    client.PosteringType.YTEL -> PosteringType.YTELSE
    client.PosteringType.FEIL -> PosteringType.FEILUTBETALING
    client.PosteringType.SKAT -> PosteringType.FORSKUDSSKATT
    client.PosteringType.JUST -> PosteringType.JUSTERING
    client.PosteringType.TREK -> PosteringType.TREKK
    client.PosteringType.MOTP -> PosteringType.MOTPOSTERING
}

fun Fagområde.Mapper.from(dto: client.Fagområde) = when (dto) {
    client.Fagområde.AAP -> Fagområde.AAP
    client.Fagområde.TILLST -> Fagområde.TILLEGGSSTØNADER
    client.Fagområde.TSTARENA -> Fagområde.TILLEGGSSTØNADER_ARENA
    client.Fagområde.MTSTAREN -> Fagområde.TILLEGGSSTØNADER_ARENA_MANUELL_POSTERING
    client.Fagområde.DP -> Fagområde.DAGPENGER
    client.Fagområde.MDP -> Fagområde.DAGPENGER_MANUELL_POSTERING
    client.Fagområde.DPARENA -> Fagområde.DAGPENGER_ARENA
    client.Fagområde.MDPARENA -> Fagområde.DAGPENGER_ARENA_MANUELL_POSTERING
    client.Fagområde.TILTPENG -> Fagområde.TILTAKSPENGER
    client.Fagområde.TPARENA -> Fagområde.TILTAKSPENGER_ARENA
    client.Fagområde.MTPARENA -> Fagområde.TILTAKSPENGER_ARENA_MANUELL_POSTERING
}

fun Fagsystem.into() = when (this) {
    Fagsystem.AAP -> client.Fagområde.AAP
    Fagsystem.DAGPENGER -> client.Fagområde.DP
    Fagsystem.TILTAKSPENGER -> client.Fagområde.TILTPENG
    Fagsystem.TILLEGGSSTØNADER -> client.Fagområde.TILLST
}

fun Satstype.into() = when (this) {
    Satstype.DAGLIG -> client.Satstype.DAG
    Satstype.DAGLIG_INKL_HELG -> client.Satstype.DAG7
    Satstype.MÅNEDLIG -> client.Satstype.MND
    Satstype.ENGANGS -> client.Satstype.ENG
}

fun Fagsystem.inFagområde(fagområde: client.Fagområde): Boolean {
    val fagområde = Fagområde.from(fagområde)
    return when (this) {
        Fagsystem.DAGPENGER -> fagområde in listOf(
            Fagområde.DAGPENGER,
            Fagområde.DAGPENGER_MANUELL_POSTERING,
            Fagområde.DAGPENGER_ARENA,
            Fagområde.DAGPENGER_ARENA_MANUELL_POSTERING,
        )

        Fagsystem.TILTAKSPENGER -> fagområde in listOf(
            Fagområde.TILTAKSPENGER,
            Fagområde.TILTAKSPENGER_ARENA,
            Fagområde.TILTAKSPENGER_ARENA_MANUELL_POSTERING,
        )

        Fagsystem.TILLEGGSSTØNADER -> fagområde in listOf(
            Fagområde.TILLEGGSSTØNADER,
            Fagområde.TILLEGGSSTØNADER_ARENA,
            Fagområde.TILLEGGSSTØNADER_ARENA_MANUELL_POSTERING,
        )

        Fagsystem.AAP -> fagområde in listOf(Fagområde.AAP)
    }
}
