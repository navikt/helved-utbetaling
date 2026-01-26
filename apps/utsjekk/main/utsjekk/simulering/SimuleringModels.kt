package utsjekk.simulering

import models.kontrakter.felles.Fagsystem
import models.kontrakter.felles.Personident
import models.kontrakter.iverksett.ForrigeIverksettingV2Dto
import models.kontrakter.iverksett.UtbetalingV2Dto
import models.kontrakter.oppdrag.Utbetalingsoppdrag
import utsjekk.iverksetting.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

object domain {
    data class Simulering(
        val behandlingsinformasjon: Behandlingsinformasjon,
        val nyTilkjentYtelse: TilkjentYtelse,
        val forrigeIverksetting: ForrigeIverksetting?,
    ) {
        companion object {
            fun from(dto: api.SimuleringRequest, fs: Fagsystem) = Simulering(
                behandlingsinformasjon = Behandlingsinformasjon(
                    saksbehandlerId = dto.saksbehandlerId,
                    beslutterId = dto.saksbehandlerId,
                    fagsakId = SakId(dto.sakId),
                    fagsystem = fs,
                    behandlingId = BehandlingId(dto.behandlingId),
                    personident = dto.personident.verdi,
                    vedtaksdato = LocalDate.now(),
                    brukersNavKontor = null,
                    iverksettingId = null,
                ),
                nyTilkjentYtelse = dto.utbetalinger.toTilkjentYtelse(),
                forrigeIverksetting = dto.forrigeIverksetting?.let {
                    domain.ForrigeIverksetting(
                        behandlingId = BehandlingId(it.behandlingId),
                        iverksettingId = it.iverksettingId?.let(::IverksettingId),
                    )
                },
            )
        }
    }

    data class SimuleringDetaljer(
        val gjelderId: String,
        val datoBeregnet: LocalDate,
        val totalBeløp: Int,
        val perioder: List<domain.Periode>,
    ) {
        companion object {
            fun from(dto: client.SimuleringResponse, fs: Fagsystem) = SimuleringDetaljer(
                gjelderId = dto.gjelderId,
                datoBeregnet = dto.datoBeregnet,
                totalBeløp = dto.totalBelop,
                perioder = dto.perioder.map { p ->
                    domain.Periode(
                        fom = p.fom,
                        tom = p.tom,
                        posteringer = p.utbetalinger
                            .filter { it.fagområde.hasFagsystem(fs) }
                            .flatMap {  utbetaling ->
                                utbetaling.detaljer.map { postering ->
                                    domain.Postering(
                                        fagområde = domain.Fagområde.from(utbetaling.fagområde),
                                        sakId = SakId(utbetaling.fagSystemId),
                                        fom = postering.faktiskFom,
                                        tom = postering.faktiskTom,
                                        beløp = postering.belop,
                                        klassekode = postering.klassekode,
                                        type = domain.PosteringType.from(postering.type)
                                    )
                                }
                            }
                    )
                }
            )
        }
    }

    data class ForrigeIverksetting(
        val behandlingId: BehandlingId,
        val iverksettingId: IverksettingId?,
    )

    data class Periode(
        val fom: LocalDate,
        val tom: LocalDate,
        val posteringer: List<domain.Postering>,
    )

    data class Postering(
        val fagområde: Fagområde,
        val sakId: SakId,
        val fom: LocalDate,
        val tom: LocalDate,
        val beløp: Int,
        val type: PosteringType,
        val klassekode: String,
    )

    enum class PosteringType {
        YTELSE,
        FEILUTBETALING,
        FORSKUDSSKATT,
        JUSTERING,
        TREKK,
        MOTPOSTERING,
        ;

        companion object {
            fun from(dto: client.PosteringType) = when (dto) {
                client.PosteringType.YTEL -> domain.PosteringType.YTELSE
                client.PosteringType.FEIL -> domain.PosteringType.FEILUTBETALING
                client.PosteringType.SKAT -> domain.PosteringType.FORSKUDSSKATT
                client.PosteringType.JUST -> domain.PosteringType.JUSTERING
                client.PosteringType.TREK -> domain.PosteringType.TREKK
                client.PosteringType.MOTP -> domain.PosteringType.MOTPOSTERING
            }
        }
    }

    enum class Fagområde {
        AAP,
        TILLEGGSSTØNADER,
        TILLEGGSSTØNADER_ARENA,
        TILLEGGSSTØNADER_ARENA_MANUELL_POSTERING,
        DAGPENGER,
        DAGPENGER_MANUELL_POSTERING,
        DAGPENGER_ARENA,
        DAGPENGER_ARENA_MANUELL_POSTERING,
        TILTAKSPENGER,
        TILTAKSPENGER_ARENA,
        TILTAKSPENGER_ARENA_MANUELL_POSTERING,
        HISTORISK,
        ;

        companion object {
            fun from(dto: client.Fagområde) = when (dto) {
                client.Fagområde.AAP -> domain.Fagområde.AAP
                client.Fagområde.TILLST -> domain.Fagområde.TILLEGGSSTØNADER
                client.Fagområde.TSTARENA -> domain.Fagområde.TILLEGGSSTØNADER_ARENA
                client.Fagområde.MTSTAREN -> domain.Fagområde.TILLEGGSSTØNADER_ARENA_MANUELL_POSTERING
                client.Fagområde.DP -> domain.Fagområde.DAGPENGER
                client.Fagområde.MDP -> domain.Fagområde.DAGPENGER_MANUELL_POSTERING
                client.Fagområde.DPARENA -> domain.Fagområde.DAGPENGER_ARENA
                client.Fagområde.MDPARENA -> domain.Fagområde.DAGPENGER_ARENA_MANUELL_POSTERING
                client.Fagområde.TILTPENG -> domain.Fagområde.TILTAKSPENGER
                client.Fagområde.TPARENA -> domain.Fagområde.TILTAKSPENGER_ARENA
                client.Fagområde.MTPARENA -> domain.Fagområde.TILTAKSPENGER_ARENA_MANUELL_POSTERING
                client.Fagområde.HELSREF -> domain.Fagområde.HISTORISK
            }
        }
    }
}


object client {
    data class SimuleringResponse(
        val gjelderId: String,
        val datoBeregnet: LocalDate,
        val totalBelop: Int,
        val perioder: List<SimulertPeriode>,
    )

    data class SimulertPeriode(
        val fom: LocalDate,
        val tom: LocalDate,
        val utbetalinger: List<Utbetaling>,
    )

    // Dette er det samme som et stoppnivå i SOAP
    data class Utbetaling(
        val fagområde: Fagområde,
        val fagSystemId: String,
        val utbetalesTilId: String,
        val forfall: LocalDate,
        val feilkonto: Boolean,
        val detaljer: List<PosteringDto>,
    ) 

    // Tilsvarer én rad i regnskapet
    data class PosteringDto(
        val type: PosteringType,
        val faktiskFom: LocalDate,
        val faktiskTom: LocalDate,
        val belop: Int,
        val sats: Double,
        val satstype: String?,
        val klassekode: String,
        val trekkVedtakId: Long?,
        val refunderesOrgNr: String?,
    )

    enum class PosteringType { 
        YTEL,
        FEIL,
        SKAT,
        JUST,
        TREK,
        MOTP; 
    }

    enum class Satstype { 
        DAG,
        DAG7,
        MND,
        ENG; 

        companion object {
            fun from(st: models.kontrakter.felles.Satstype) = when (st) {
                models.kontrakter.felles. Satstype.DAGLIG -> client.Satstype.DAG
                models.kontrakter.felles. Satstype.DAGLIG_INKL_HELG -> client.Satstype.DAG7
                models.kontrakter.felles. Satstype.MÅNEDLIG -> client.Satstype.MND
                models.kontrakter.felles. Satstype.ENGANGS -> client.Satstype.ENG
            }
        }
    }

    enum class Fagområde { 
        TILLST,
        TSTARENA,
        MTSTAREN,
        DP,
        MDP,
        DPARENA,
        MDPARENA,
        TILTPENG,
        TPARENA,
        MTPARENA,
        AAP,
        HELSREF; 

        companion object {
            fun from(fs: models.kontrakter.felles.Fagsystem) = when (fs) {
                Fagsystem.AAP -> client.Fagområde.AAP
                Fagsystem.DAGPENGER -> client.Fagområde.DP
                Fagsystem.TILTAKSPENGER -> client.Fagområde.TILTPENG
                Fagsystem.TILLEGGSSTØNADER -> client.Fagområde.TILLST
            }
        }

        fun hasFagsystem(fs: models.kontrakter.felles.Fagsystem): Boolean {
            val fagområde = domain.Fagområde.from(this)
            return when (fs) {
                models.kontrakter.felles.Fagsystem.DAGPENGER -> fagområde in listOf(
                    domain.Fagområde.DAGPENGER,
                    domain.Fagområde.DAGPENGER_MANUELL_POSTERING,
                    domain.Fagområde.DAGPENGER_ARENA,
                    domain.Fagområde.DAGPENGER_ARENA_MANUELL_POSTERING,
                )
                models.kontrakter.felles.Fagsystem.TILTAKSPENGER -> fagområde in listOf(
                    domain.Fagområde.TILTAKSPENGER,
                    domain.Fagområde.TILTAKSPENGER_ARENA,
                    domain.Fagområde.TILTAKSPENGER_ARENA_MANUELL_POSTERING,
                )
                models.kontrakter.felles.Fagsystem.TILLEGGSSTØNADER -> fagområde in listOf(
                    domain.Fagområde.TILLEGGSSTØNADER,
                    domain.Fagområde.TILLEGGSSTØNADER_ARENA,
                    domain.Fagområde.TILLEGGSSTØNADER_ARENA_MANUELL_POSTERING,
                )
                models.kontrakter.felles.Fagsystem.AAP -> fagområde in listOf(domain.Fagområde.AAP)
            }
        }
    }

    data class SimuleringRequest(
        val fagområde: Fagområde,
        val sakId: String,
        val personident: Personident,
        val erFørsteUtbetalingPåSak: Boolean,
        val saksbehandler: String,
        val utbetalingsperioder: List<Utbetalingsperiode>,
    ) {
        companion object {
            fun from(domain: Utbetalingsoppdrag) = client.SimuleringRequest(
                sakId = domain.saksnummer,
                fagområde = client.Fagområde.from(domain.fagsystem),
                personident = Personident(domain.aktør),
                erFørsteUtbetalingPåSak = domain.erFørsteUtbetalingPåSak,
                saksbehandler = domain.saksbehandlerId,
                utbetalingsperioder = domain.utbetalingsperiode.map { client.Utbetalingsperiode.from(it) },
            )
        }
    }

    data class Utbetalingsperiode(
        val periodeId: String,
        val forrigePeriodeId: String?,
        val erEndringPåEksisterendePeriode: Boolean,
        val klassekode: String,
        val fom: LocalDate,
        val tom: LocalDate,
        val sats: Int,
        val satstype: Satstype,
        val opphør: Opphør?,
        val utbetalesTil: String,
        val fastsattDagsats: BigDecimal?,
    ) {
        companion object {
            fun from(domain: models.kontrakter.oppdrag.Utbetalingsperiode) = client.Utbetalingsperiode(
                periodeId = domain.periodeId.toString(),
                forrigePeriodeId = domain.forrigePeriodeId?.toString(),
                erEndringPåEksisterendePeriode = domain.erEndringPåEksisterendePeriode,
                klassekode = domain.klassifisering,
                fom = domain.fom,
                tom = domain.tom,
                sats = domain.sats.toInt(),
                satstype = client.Satstype.from(domain.satstype),
                opphør = domain.opphør?.let { client.Opphør(it.fom) },
                utbetalesTil = domain.utbetalesTil,
                fastsattDagsats = domain.fastsattDagsats,
            )
        }
    }

    data class Opphør(val fom: LocalDate)
}

object api {
    data class SimuleringRequest(
        val sakId: String,
        val behandlingId: String,
        val personident: Personident,
        val saksbehandlerId: String,
        val utbetalinger: List<UtbetalingV2Dto>,
        val forrigeIverksetting: ForrigeIverksettingV2Dto? = null,
    )

    data class SimuleringRespons(
        val oppsummeringer: List<OppsummeringForPeriode>,
        val detaljer: domain.SimuleringDetaljer,
    ) {
        companion object {

            /**
             * Se dokumentasjon: https://github.com/navikt/helved-utbetaling/blob/main/dokumentasjon/simulering.md
             */
            fun from(detaljer: domain.SimuleringDetaljer): SimuleringRespons {
                val oppsummeringer =
                    detaljer.perioder.slåSammenInnenforSammeMåned().map {
                        api.OppsummeringForPeriode(
                            fom = it.fom,
                            tom = it.tom,
                            tidligereUtbetalt = beregnTidligereUtbetalt(it.posteringer),
                            nyUtbetaling = beregnNyttBeløp(it.posteringer),
                            totalEtterbetaling = if (it.fom > LocalDate.now()) 0 else beregnEtterbetaling(it.posteringer),
                            totalFeilutbetaling = beregnFeilutbetaling(it.posteringer),
                        )
                    }
                return api.SimuleringRespons(oppsummeringer = oppsummeringer, detaljer = detaljer)
            }

        }
    }

    data class OppsummeringForPeriode(
        val fom: LocalDate,
        val tom: LocalDate,
        val tidligereUtbetalt: Int,
        val nyUtbetaling: Int,
        val totalEtterbetaling: Int,
        val totalFeilutbetaling: Int,
    )
}

private fun List<domain.Periode>.slåSammenInnenforSammeMåned(): List<domain.Periode> {
    val måneder = this.groupBy { YearMonth.of(it.fom.year, it.fom.month) }
    return måneder.values.map { perioder ->
        domain.Periode(
            perioder.minBy { it.fom }.fom,
            perioder.maxBy { it.tom }.tom,
            perioder.flatMap { it.posteringer })
    }
}

private fun beregnTidligereUtbetalt(posteringer: List<domain.Postering>): Int =
    abs(posteringer.summerBareNegativePosteringer(domain.PosteringType.YTELSE))

private fun beregnNyttBeløp(posteringer: List<domain.Postering>): Int =
    posteringer.summerBarePositivePosteringer(domain.PosteringType.YTELSE) - posteringer.summerBarePositivePosteringer(domain.PosteringType.FEILUTBETALING, KLASSEKODE_FEILUTBETALING)

private fun beregnEtterbetaling(posteringer: List<domain.Postering>): Int {
    val justeringer = posteringer.summerPosteringer(domain.PosteringType.FEILUTBETALING, KLASSEKODE_JUSTERING)
    val resultat = beregnNyttBeløp(posteringer) - beregnTidligereUtbetalt(posteringer)
    return if (justeringer < 0) {
        maxOf(resultat - abs(justeringer), 0)
    } else {
        maxOf(resultat, 0)
    }
}

private fun beregnFeilutbetaling(posteringer: List<domain.Postering>): Int =
    maxOf(0, posteringer.summerBarePositivePosteringer(domain.PosteringType.FEILUTBETALING, KLASSEKODE_FEILUTBETALING))

private fun List<domain.Postering>.summerBarePositivePosteringer(type: domain.PosteringType): Int =
    this.filter { it.beløp > 0 && it.type == type }.sumOf { it.beløp }

private fun List<domain.Postering>.summerBareNegativePosteringer(type: domain.PosteringType): Int =
    this.filter { it.beløp < 0 && it.type == type }.sumOf { it.beløp }

private fun List<domain.Postering>.summerBarePositivePosteringer(type: domain.PosteringType, klassekode: String): Int =
    this.filter { it.beløp > 0 && it.type == type && it.klassekode == klassekode }.sumOf { it.beløp }

private fun List<domain.Postering>.summerPosteringer(type: domain.PosteringType, klassekode: String): Int =
    this.filter { it.type == type && it.klassekode == klassekode }.sumOf { it.beløp }

const val KLASSEKODE_JUSTERING = "KL_KODE_JUST_ARBYT"
const val KLASSEKODE_FEILUTBETALING = "KL_KODE_FEIL_ARBYT"

