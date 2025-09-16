package utsjekk.iverksetting.utbetalingsoppdrag

import models.kontrakter.oppdrag.Utbetalingsoppdrag
import models.kontrakter.oppdrag.Utbetalingsperiode
import utsjekk.iverksetting.*
import utsjekk.iverksetting.utbetalingsoppdrag.AndelValidator.validerAndeler
import utsjekk.iverksetting.utbetalingsoppdrag.BeståendeAndelerBeregner.finnBeståendeAndeler
import java.time.LocalDate

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
