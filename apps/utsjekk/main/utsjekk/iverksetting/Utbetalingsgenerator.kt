package utsjekk.iverksetting

import models.badRequest
import models.kontrakter.Satstype
import utsjekk.iverksetting.AndelValidator.validerAndeler
import utsjekk.iverksetting.BeståendeAndelerBeregner.finnBeståendeAndeler
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

object Utbetalingsgenerator {
    /**
     * Generer utbetalingsoppdrag som sendes til oppdrag
     *
     * @param sisteAndelPerKjede må sende inn siste andelen per kjede for å peke/opphøre riktig forrige andel
     * Siste andelen er første andelen med høyeste periodeId, per ident/type, dvs hvis man har avkortet en periode,
     * og fått et nytt tom, så skal man bruke den opprinnelige perioden for det periodeId'et
     * ex
     * SELECT * FROM (SELECT aty.id,
     *        row_number() OVER (PARTITION BY aty.type, aty.fk_aktoer_id ORDER BY aty.periode_offset DESC, x.opprettet_tid ASC) rn
     * FROM andel_tilkjent_ytelse aty) WHERE rn = 1
     *
     * [sisteAndelPerKjede] brukes også for å utlede om utbetalingsoppdraget settes til NY eller ENDR
     *
     * @return [BeregnetUtbetalingsoppdrag] som inneholder både utbetalingsoppdraget og [BeregnetUtbetalingsoppdrag.andeler]
     * som inneholder periodeId/forrigePeriodeId for å kunne oppdatere andeler i basen
     */
    fun lagUtbetalingsoppdrag(
        behandlingsinformasjon: Behandlingsinformasjon,
        requested: List<AndelData>,
        existing: List<AndelData>,
        lastExistingByKjede: Map<Kjedenøkkel, AndelData>,
    ): BeregnetUtbetalingsoppdrag {
        validerAndeler(existing, requested)
        val nyeAndelerGruppert = requested.groupByKjedenøkkel()
        val existingKjeder = existing.groupByKjedenøkkel()

        return lagUtbetalingsoppdrag(
            requested = nyeAndelerGruppert,
            existingKjeder = existingKjeder,
            lastExistingByKjede = lastExistingByKjede,
            behandlingsinformasjon = behandlingsinformasjon,
        )
    }

    private fun lagUtbetalingsoppdrag(
        requested: Map<Kjedenøkkel, List<AndelData>>,
        existingKjeder: Map<Kjedenøkkel, List<AndelData>>,
        lastExistingByKjede: Map<Kjedenøkkel, AndelData>,
        behandlingsinformasjon: Behandlingsinformasjon,
    ): BeregnetUtbetalingsoppdrag {
        val nyeKjeder = lagNyeKjeder(requested, existingKjeder, lastExistingByKjede)

        val utbetalingsoppdrag =
            Utbetalingsoppdrag(
                saksbehandlerId = behandlingsinformasjon.saksbehandlerId,
                beslutterId = behandlingsinformasjon.beslutterId,
                erFørsteUtbetalingPåSak = erFørsteUtbetalingPåSak(lastExistingByKjede),
                saksnummer = behandlingsinformasjon.fagsakId.id,
                fagsystem = behandlingsinformasjon.fagsystem,
                aktør = behandlingsinformasjon.personident,
                brukersNavKontor = behandlingsinformasjon.brukersNavKontor?.enhet,
                utbetalingsperiode = utbetalingsperioder(behandlingsinformasjon, nyeKjeder),
                iverksettingId = behandlingsinformasjon.iverksettingId?.id,
            )

        return BeregnetUtbetalingsoppdrag(
            utbetalingsoppdrag,
            lagAndelerMedPeriodeId(nyeKjeder),
        )
    }

    private fun lagNyeKjeder(
        requestedKjeder: Map<Kjedenøkkel, List<AndelData>>,
        existingKjeder: Map<Kjedenøkkel, List<AndelData>>,
        lastExistingByKjede: Map<Kjedenøkkel, AndelData>,
    ): List<ResultatForKjede> {
        val alleKjedenøkler = requestedKjeder.keys + existingKjeder.keys
        var sistePeriodeId = lastExistingByKjede.values.mapNotNull { it.periodeId }.maxOrNull() ?: -1
        return alleKjedenøkler.map { kjedenøkkel ->
            val existing = existingKjeder[kjedenøkkel] ?: emptyList()
            val nyeAndeler = requestedKjeder[kjedenøkkel] ?: emptyList()
            val lastExisting = lastExistingByKjede[kjedenøkkel]
            val opphørsdato = finnOpphørsdato(existing, nyeAndeler)

            val nyKjede =
                beregnNyKjede(
                    existing.uten0beløp(),
                    nyeAndeler.uten0beløp(),
                    lastExisting,
                    sistePeriodeId,
                    opphørsdato,
                )
            sistePeriodeId = nyKjede.sistePeriodeId
            nyKjede
        }
    }

    /**
     * For å unngå unøvendig 0-sjekk senere, så sjekkes det for om man
     * må opphøre alle andeler pga nye 0-andeler som har startdato før forrige første periode
     */
    private fun finnOpphørsdato(
        existing: List<AndelData>,
        nyeAndeler: List<AndelData>,
    ): LocalDate? {
        val forrigeFørsteAndel = existing.firstOrNull()
        val nyFørsteAndel = nyeAndeler.firstOrNull()
        if (
            forrigeFørsteAndel != null &&
            nyFørsteAndel != null &&
            nyFørsteAndel.beløp == 0 &&
            nyFørsteAndel.fom < forrigeFørsteAndel.fom
        ) {
            return nyFørsteAndel.fom
        }
        return null
    }

    private fun utbetalingsperioder(
        behandlingsinformasjon: Behandlingsinformasjon,
        nyeKjeder: List<ResultatForKjede>,
    ): List<Utbetalingsperiode> {
        val opphørsperioder = lagOpphørsperioder(behandlingsinformasjon, nyeKjeder.mapNotNull { it.opphørsandel })
        val nyePerioder = lagNyePerioder(behandlingsinformasjon, nyeKjeder.flatMap { it.nyeAndeler })
        return opphørsperioder + nyePerioder
    }

    private fun lagAndelerMedPeriodeId(nyeKjeder: List<ResultatForKjede>): List<AndelMedPeriodeId> =
        nyeKjeder.flatMap { nyKjede ->
            nyKjede.beståendeAndeler.map { AndelMedPeriodeId(it) } +
                nyKjede.nyeAndeler.map {
                    AndelMedPeriodeId(it)
                }
        }

    private fun erFørsteUtbetalingPåSak(sisteAndelMedPeriodeId: Map<Kjedenøkkel, AndelData>) = sisteAndelMedPeriodeId.isEmpty()

    private fun beregnNyKjede(
        existing: List<AndelData>,
        requested: List<AndelData>,
        lastExisting: AndelData?,
        periodeId: Long,
        opphørsdato: LocalDate?,
    ): ResultatForKjede {
        val beståendeAndeler = finnBeståendeAndeler(existing, requested, opphørsdato)
        val nyeAndeler = requested.subList(beståendeAndeler.andeler.size, requested.size)

        val (nyeAndelerMedPeriodeId, gjeldendePeriodeId) = nyeAndelerMedPeriodeId(nyeAndeler, periodeId, lastExisting)
        return ResultatForKjede(
            beståendeAndeler = beståendeAndeler.andeler,
            nyeAndeler = nyeAndelerMedPeriodeId,
            opphørsandel =
                beståendeAndeler.opphørFra?.let {
                    Pair(lastExisting ?: error("Må ha siste andel for å kunne opphøre"), it)
                },
            sistePeriodeId = gjeldendePeriodeId,
        )
    }

    private fun nyeAndelerMedPeriodeId(
        nyeAndeler: List<AndelData>,
        periodeId: Long,
        lastExisting: AndelData?,
    ): Pair<List<AndelData>, Long> {
        var gjeldendePeriodeId = periodeId
        var forrigePeriodeId = lastExisting?.periodeId
        val nyeAndelerMedPeriodeId = nyeAndeler.map { andelData ->
            gjeldendePeriodeId += 1
            val nyAndel = andelData.copy(periodeId = gjeldendePeriodeId, forrigePeriodeId = forrigePeriodeId)
            forrigePeriodeId = gjeldendePeriodeId
            nyAndel
        }
        return Pair(nyeAndelerMedPeriodeId, gjeldendePeriodeId)
    }

    private fun List<AndelData>.groupByKjedenøkkel(): Map<Kjedenøkkel, List<AndelData>> =
        groupBy { it.stønadsdata.tilKjedenøkkel() }.mapValues { andel -> andel.value.sortedBy { it.fom } }

    private fun lagOpphørsperioder(
        behandlingsinformasjon: Behandlingsinformasjon,
        andeler: List<Pair<AndelData, LocalDate>>,
    ): List<Utbetalingsperiode> {
        val utbetalingsperiodeMal =
            UtbetalingsperiodeMal(
                behandlingsinformasjon = behandlingsinformasjon,
                erEndringPåEksisterendePeriode = true,
            )

        return andeler.map {
            utbetalingsperiodeMal.lagPeriodeFraAndel(it.first, opphørKjedeFom = it.second)
        }
    }

    private fun lagNyePerioder(
        behandlingsinformasjon: Behandlingsinformasjon,
        andeler: List<AndelData>,
    ): List<Utbetalingsperiode> {
        val utbetalingsperiodeMal = UtbetalingsperiodeMal(behandlingsinformasjon = behandlingsinformasjon)
        return andeler.map { utbetalingsperiodeMal.lagPeriodeFraAndel(it) }
    }
}

/**
 * Lager mal for generering av utbetalingsperioder med tilpasset setting av verdier basert på parametre
 *
 * @param[vedtak] for vedtakdato og opphørsdato hvis satt
 * @param[erEndringPåEksisterendePeriode] ved true vil oppdrag sette asksjonskode ENDR på linje og ikke referere bakover
 * @return mal med tilpasset lagPeriodeFraAndel
 */
internal data class UtbetalingsperiodeMal(
    val behandlingsinformasjon: Behandlingsinformasjon,
    val erEndringPåEksisterendePeriode: Boolean = false,
) {
    /**
     * Lager utbetalingsperioder som legges på utbetalingsoppdrag. En utbetalingsperiode tilsvarer linjer hos økonomi
     *
     * Denne metoden brukes også til simulering og på dette tidspunktet er ikke vedtaksdatoen satt.
     * Derfor defaulter vi til now() når vedtaksdato mangler.
     *
     * @param[andel] andel som skal mappes til periode
     * @param[periodeIdOffset] brukes til å synce våre linjer med det som ligger hos økonomi
     * @param[forrigePeriodeIdOffset] peker til forrige i kjeden. Kun relevant når IKKE erEndringPåEksisterendePeriode
     * @param[opphørKjedeFom] fom-dato fra tidligste periode i kjede med endring
     * @return Periode til utbetalingsoppdrag
     */
    fun lagPeriodeFraAndel(
        andel: AndelData,
        opphørKjedeFom: LocalDate? = null,
    ): Utbetalingsperiode =
        Utbetalingsperiode(
            erEndringPåEksisterendePeriode = erEndringPåEksisterendePeriode,
            opphør =
                if (erEndringPåEksisterendePeriode) {
                    val opphørDatoFom =
                        opphørKjedeFom
                            ?: error("Mangler opphørsdato for kjede")
                    Opphør(opphørDatoFom)
                } else {
                    null
                },
            forrigePeriodeId = andel.forrigePeriodeId,
            periodeId = andel.periodeId ?: error("Mangler periodeId på andel=${andel.id}"),
            vedtaksdato = behandlingsinformasjon.vedtaksdato,
            klassifisering = andel.stønadsdata.tilKlassifisering(),
            fom = andel.fom,
            tom = andel.tom,
            sats = BigDecimal(andel.beløp),
            satstype = andel.satstype,
            utbetalesTil = behandlingsinformasjon.personident,
            behandlingId = behandlingsinformasjon.behandlingId.id,
            fastsattDagsats =
                when (andel.stønadsdata) {
                    is StønadsdataAAP -> andel.stønadsdata.fastsattDagsats?.let {BigDecimal(it.toInt()) }
                    is StønadsdataDagpenger -> BigDecimal(andel.stønadsdata.fastsattDagsats.toInt())
                    else -> null
                },
        )
}

private sealed interface BeståendeAndelResultat
private object NyAndelSkriverOver : BeståendeAndelResultat
private class Opphørsdato(val opphør: LocalDate) : BeståendeAndelResultat
private class AvkortAndel(val andel: AndelData, val opphør: LocalDate? = null) : BeståendeAndelResultat
internal data class BeståendeAndeler(val andeler: List<AndelData>, val opphørFra: LocalDate? = null)

internal object BeståendeAndelerBeregner {

    fun finnBeståendeAndeler(
        existing: List<AndelData>,
        requested: List<AndelData>,
        opphørsdato: LocalDate?,
    ): BeståendeAndeler {
        if (opphørsdato != null) return BeståendeAndeler(emptyList(), opphørsdato)
        val existingEnriched = existing.copyIdFrom(requested)
        val indexOnFirstDiff = existing.getIndexOnFirstDiff(requested) 
            ?: return BeståendeAndeler(existingEnriched, null) // normalt sett er det ny(e) andel(er) i kjeden som skal legges til  

        return when (val opphørsdato = finnBeståendeAndelOgOpphør(indexOnFirstDiff, existingEnriched, requested)) {
            is Opphørsdato -> BeståendeAndeler(existingEnriched.subList(0, indexOnFirstDiff), opphørsdato.opphør)
            is NyAndelSkriverOver -> BeståendeAndeler(existingEnriched.subList(0, indexOnFirstDiff))
            is AvkortAndel -> BeståendeAndeler(existingEnriched.subList(0, indexOnFirstDiff) + opphørsdato.andel, opphørsdato.opphør)
        }
    }

    private fun finnBeståendeAndelOgOpphør(
        indexOnFirstDiff: Int,
        existing: List<AndelData>,
        requested: List<AndelData>,
    ): BeståendeAndelResultat {
        val forrige = existing[indexOnFirstDiff]
        val ny = if (requested.size > indexOnFirstDiff) requested[indexOnFirstDiff] else null
        val nyNeste = if (requested.size > indexOnFirstDiff + 1) requested[indexOnFirstDiff + 1] else null
        return finnBeståendeAndelOgOpphør(ny, forrige, nyNeste)
    }

    private fun finnBeståendeAndelOgOpphør(
        ny: AndelData?,
        forrige: AndelData,
        nyNeste: AndelData?,
    ): BeståendeAndelResultat {
        if (ny == null || forrige.fom < ny.fom) return Opphørsdato(forrige.fom)
        if (forrige.fom > ny.fom || forrige.beløp != ny.beløp) {
            if (ny.beløp == 0) return Opphørsdato(ny.fom)
            return NyAndelSkriverOver
        }
        if (forrige.tom > ny.tom) {
            if (nyNeste != null && nyNeste.fom == ny.tom.plusDays(1) && nyNeste.beløp != 0) {
                return AvkortAndel(forrige.copy(tom = ny.tom), null)
            }
            return AvkortAndel(forrige.copy(tom = ny.tom), ny.tom.plusDays(1) )
        }
        return NyAndelSkriverOver
    }

    private fun List<AndelData>.copyIdFrom(other: List<AndelData>) = 
        this.mapIndexed { index, andel ->
            if (other.size <= index) andel // fallback
            else andel.copy(id = other[index].id) // ta ID fra requesten (random uuid)
        }

    private fun List<AndelData>.getIndexOnFirstDiff(other: List<AndelData>): Int? {
        this.forEachIndexed { index, andel ->
            if (other.size <= index) return index
            if (!andel.erLik(other[index])) return index
        }
        return null // alt er likt, other har fler elementer
    }

    private fun AndelData.erLik(other: AndelData): Boolean =
        this.fom == other.fom && this.tom == other.tom && this.beløp == other.beløp
}

internal object AndelValidator {
    fun validerAndeler(
        forrige: List<AndelData>,
        nye: List<AndelData>,
    ) {
        if ((forrige + nye).any { it.beløp == 0 }) {
            error("Andeler inneholder 0-beløp")
        }

        val alleIder = forrige.map { it.id } + nye.map { it.id }
        if (alleIder.size != alleIder.toSet().size) {
            error("Inneholder duplikat av id'er")
        }

        forrige
            .filter { it.periodeId == null }
            .forEach { error("Tidligere andel=${it.id} mangler periodeId") }

        nye
            .filter { it.periodeId != null || it.forrigePeriodeId != null }
            .forEach { error("Ny andel=${it.id} inneholder periodeId/forrigePeriodeId") }

        (forrige + nye).map { validerSatstype(it) }
    }

    private fun validerSatstype(andelData: AndelData) {
        when (andelData.satstype) {
            Satstype.MÅNEDLIG -> validerMånedssats(andelData)
            else -> return
        }
    }

    private fun validerMånedssats(andelData: AndelData) {
        if (andelData.fom.dayOfMonth != 1 || andelData.tom != andelData.tom.sisteDagIMåneden()) {
            badRequest("Utbetalinger med satstype ${andelData.satstype.name} må starte den første i måneden og slutte den siste i måneden")
        }
    }

    private fun LocalDate.sisteDagIMåneden(): LocalDate {
        val defaultZoneId = ZoneId.systemDefault()
        val calendar = Calendar.getInstance().apply {
            time = Date.from(this@sisteDagIMåneden.atStartOfDay(defaultZoneId).toInstant())
            set(Calendar.DAY_OF_MONTH, this.getActualMaximum(Calendar.DAY_OF_MONTH))
        }

        return LocalDate.ofInstant(calendar.toInstant(), defaultZoneId)
    }
}
