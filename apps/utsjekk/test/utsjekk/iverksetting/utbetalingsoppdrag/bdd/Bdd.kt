package utsjekk.iverksetting.utbetalingsoppdrag.bdd

import AndelId
import models.kontrakter.felles.BrukersNavKontor
import models.kontrakter.felles.Fagsystem
import models.kontrakter.felles.StønadTypeDagpenger
import models.kontrakter.felles.StønadTypeTiltakspenger
import models.kontrakter.oppdrag.Utbetalingsoppdrag
import models.kontrakter.oppdrag.Utbetalingsperiode
import org.junit.jupiter.api.Assertions.assertEquals
import utsjekk.iverksetting.*
import utsjekk.iverksetting.utbetalingsoppdrag.Utbetalingsgenerator
import utsjekk.iverksetting.utbetalingsoppdrag.bdd.ValideringUtil.assertSjekkBehandlingIder
import java.time.LocalDate

val FAGSAK_ID = RandomOSURId.generate()

object Bdd {
    private var behandlingsinformasjon = mutableMapOf<BehandlingId, Behandlingsinformasjon>()
    private var andelerPerBehandlingId = mapOf<BehandlingId, List<AndelData>>()
    private var beregnetUtbetalingsoppdrag = mutableMapOf<BehandlingId, BeregnetUtbetalingsoppdrag>()

    fun reset() {
        behandlingsinformasjon.clear()
        beregnetUtbetalingsoppdrag.clear()
        AndelId.reset()
    }

    //    @Gitt("følgende behandlingsinformasjon")
    fun <T : WithBehandlingId> følgendeBehandlinger(dataTable: DataTable, parser: (Iterator<String>) -> T) {
        opprettBehandlingsinformasjon(dataTable, parser)
    }

    //    @Gitt("følgende tilkjente ytelser")
    fun <T : WithBehandlingId> følgendeTilkjenteYtelser(
        dataTable: DataTable,
        input: (Iterator<String>) -> T,
        toAndelData: (T) -> AndelData?
    ) {
        genererBehandlingsinformasjonForDeSomMangler(dataTable, input)
        andelerPerBehandlingId = dataTable.input
            .into(input)
            .groupBy { it.behandlingId }
            .mapValues { (_, v) -> v.mapNotNull(toAndelData) }

        if (
            andelerPerBehandlingId
                .flatMap { it.value }
                .any { it.periodeId != null || it.forrigePeriodeId != null }
        ) {
            error("Kildebehandling/periodeId/forrigePeriodeId skal ikke settes på input, denne settes fra utbetalingsgeneratorn")
        }
    }

    //    @Når("beregner utbetalingsoppdrag")
    fun beregnUtbetalignsoppdrag() {
        andelerPerBehandlingId.entries.fold(emptyList<Pair<BehandlingId, List<AndelData>>>()) { acc, andelPåBehandlingId ->
            val behandlingId = andelPåBehandlingId.key
            try {
                val beregnUtbetalingsoppdrag = beregnUtbetalingsoppdrag(acc, andelPåBehandlingId)
                beregnetUtbetalingsoppdrag[behandlingId] = beregnUtbetalingsoppdrag
                val oppdaterteAndeler = oppdaterAndelerMedPeriodeId(beregnUtbetalingsoppdrag, andelPåBehandlingId)

                acc + (behandlingId to oppdaterteAndeler)
            } catch (e: Throwable) {
                error("Feilet beregning av oppdrag for behandling=$behandlingId")
            }
        }
    }

    //    @Når("beregner utbetalingsoppdrag kjøres kastes exception")
    fun `lagTilkjentYtelseMedUtbetalingsoppdrag kjøres kastes exception`(dataTable: DataTable) {
//        val throwable = assertThrows<Throwable> { `beregner utbetalingsoppdrag`() }
//        dataTable.asMaps().let { rader ->
//            if (rader.size > 1) {
//                error("Kan maks inneholde en rad")
//            }
//            rader.firstOrNull()!!.let { rad ->
//                assertEquals(rad["Exception"], throwable::class.java.simpleName)
//                assertTrue(throwable.message!!.contains(rad["Melding"]!!))
//            }
//        }
    }

    //    @Så("forvent følgende utbetalingsoppdrag")
    fun <T : WithBehandlingId> forventFølgendeUtbetalingsoppdrag(
        dataTable: DataTable,
        expected: (Iterator<String>) -> T,
        forventetUtbetalingsoppdrag: (List<T>) -> ForventetUtbetalingsoppdrag,
    ) {
        validerForventetUtbetalingsoppdrag(dataTable, beregnetUtbetalingsoppdrag, expected, forventetUtbetalingsoppdrag)
        assertSjekkBehandlingIder(dataTable, beregnetUtbetalingsoppdrag, expected)
    }

    //    @Så("forvent følgende andeler med periodeId")
    fun <T : WithBehandlingId> forventFølgendeAndelerMedPeriodeId(
        dataTable: DataTable,
        expected: (Iterator<String>) -> T,
        toAndelMedPeriodeId: (T) -> AndelMedPeriodeId,
    ) {
        val parsed = dataTable.expected2.into(expected)
        val groupByBehandlingId = parsed.groupBy { it.behandlingId }

        groupByBehandlingId.forEach { (behandlingId, rader) ->
            val beregnedeAndeler = beregnetUtbetalingsoppdrag.getValue(behandlingId).andeler
            val forventedeAndeler = rader.map { rad -> toAndelMedPeriodeId(rad) }
            assertEquals(forventedeAndeler, beregnedeAndeler)
        }

        val ikkeTommeUtbetalingsoppdrag =
            beregnetUtbetalingsoppdrag.values.map { it.andeler }.filter { it.isNotEmpty() }
        assertEquals(groupByBehandlingId.size, ikkeTommeUtbetalingsoppdrag.size)
    }

    private fun <T : WithBehandlingId> opprettBehandlingsinformasjon(
        dataTable: DataTable,
        parser: (Iterator<String>) -> T,
    ) {
        val parsed = dataTable.input.into(parser)
        parsed.groupBy { it.behandlingId }.map { (behandlingId, _) ->
            behandlingsinformasjon[behandlingId] = lagBehandlingsinformasjon(behandlingId = behandlingId)
        }
    }

    private fun <T : WithBehandlingId> genererBehandlingsinformasjonForDeSomMangler(
        dataTable: DataTable,
        parser: (Iterator<String>) -> T,
    ) {
        val parsed = dataTable.input.into(parser)
        parsed.groupBy { it.behandlingId }.forEach { (behandlingId, _) ->
            if (!behandlingsinformasjon.containsKey(behandlingId)) {
                behandlingsinformasjon[behandlingId] =
                    lagBehandlingsinformasjon(behandlingId)
            }
        }
    }

    private fun lagBehandlingsinformasjon(behandlingId: BehandlingId) =
        Behandlingsinformasjon(
            fagsakId = SakId(FAGSAK_ID),
            fagsystem = Fagsystem.DAGPENGER,
            saksbehandlerId = "saksbehandlerId",
            beslutterId = "beslutterId",
            behandlingId = behandlingId,
            personident = "1",
            vedtaksdato = LocalDate.now(),
            iverksettingId = null,
        )

    private fun beregnUtbetalingsoppdrag(
        acc: List<Pair<BehandlingId, List<AndelData>>>,
        andeler: Map.Entry<BehandlingId, List<AndelData>>,
    ): BeregnetUtbetalingsoppdrag {
        val forrigeKjeder = acc.lastOrNull()?.second ?: emptyList()
        val behandlingId = andeler.key
        val sisteOffsetPerIdent = gjeldendeForrigeOffsetForKjede(acc)
        val behandlingsinformasjon1 = behandlingsinformasjon.getValue(behandlingId)

        return Utbetalingsgenerator.lagUtbetalingsoppdrag(
            behandlingsinformasjon = behandlingsinformasjon1,
            requested = andeler.value,
            existing = forrigeKjeder,
            lastExistingByKjede = sisteOffsetPerIdent,
        )
    }

    /**
     * Når vi henter forrige offset for en kjede så må vi hente max periodeId, men den første hendelsen av den typen
     * Dette då vi i noen tilfeller opphører en peride, som beholder den samme periodeId'n
     */
    private fun gjeldendeForrigeOffsetForKjede(forrigeKjeder: List<Pair<BehandlingId, List<AndelData>>>) =
        forrigeKjeder
            .flatMap { it.second }
            .uten0beløp()
            .groupBy { it.stønadsdata.tilKjedenøkkel() }
            .mapValues { (_, value) ->
                value.sortedWith(compareByDescending<AndelData> { it.periodeId!! }.thenByDescending { it.tom }).first()
            }

    private fun oppdaterAndelerMedPeriodeId(
        beregnUtbetalingsoppdrag: BeregnetUtbetalingsoppdrag,
        andelPåBehandlingId: Map.Entry<BehandlingId, List<AndelData>>,
    ): List<AndelData> {
        val andelerPerId = beregnUtbetalingsoppdrag.andeler.associateBy { it.id }

        return andelPåBehandlingId.value.map {
            if (it.beløp == 0) {
                it
            } else {
                val andelMedPeriodeId = andelerPerId[it.id]!!
                it.copy(
                    periodeId = andelMedPeriodeId.periodeId,
                    forrigePeriodeId = andelMedPeriodeId.forrigePeriodeId,
                )
            }
        }
    }

    private fun <T : WithBehandlingId> validerForventetUtbetalingsoppdrag(
        dataTable: DataTable,
        beregnetUtbetalingsoppdrag: MutableMap<BehandlingId, BeregnetUtbetalingsoppdrag>,
        expected: (Iterator<String>) -> T,
        forventetUtbetalingsoppdrag: (List<T>) -> ForventetUtbetalingsoppdrag,
    ) {
        val forventedeUtbetalingsoppdrag = dataTable.expected.into(expected)
            .groupBy { it.behandlingId }
            .map { (_, rows) -> forventetUtbetalingsoppdrag(rows) }

        forventedeUtbetalingsoppdrag.forEach { forventet ->
            val utbetalingsoppdrag = beregnetUtbetalingsoppdrag[forventet.behandlingId]
                ?: error("Mangler utbetalingsoppdrag for ${forventet.behandlingId}")

            assertUtbetalingsoppdrag(forventet, utbetalingsoppdrag.utbetalingsoppdrag)
        }
    }

    private fun assertUtbetalingsoppdrag(
        forventetUtbetalingsoppdrag: ForventetUtbetalingsoppdrag,
        utbetalingsoppdrag: Utbetalingsoppdrag,
    ) {
        assertEquals(forventetUtbetalingsoppdrag.erFørsteUtbetalingPåSak, utbetalingsoppdrag.erFørsteUtbetalingPåSak)
        forventetUtbetalingsoppdrag.utbetalingsperiode.forEachIndexed { index, forventetUtbetalingsperiode ->
            val utbetalingsperiode = utbetalingsoppdrag.utbetalingsperiode[index]
            assertUtbetalingsperiode(utbetalingsperiode, forventetUtbetalingsperiode)
        }

        assertEquals(forventetUtbetalingsoppdrag.utbetalingsperiode.size, utbetalingsoppdrag.utbetalingsperiode.size)
    }
}

private fun assertUtbetalingsperiode(
    utbetalingsperiode: Utbetalingsperiode,
    forventetUtbetalingsperiode: ForventetUtbetalingsperiode,
) {
    val forventetStønadsdata =
        if (forventetUtbetalingsperiode.ytelse is StønadTypeDagpenger) {
            StønadsdataDagpenger(stønadstype = forventetUtbetalingsperiode.ytelse, meldekortId = "M1", fastsattDagsats = 1000u)
        } else {
            StønadsdataTiltakspenger(
                stønadstype = forventetUtbetalingsperiode.ytelse as StønadTypeTiltakspenger,
                barnetillegg = false,
                brukersNavKontor = BrukersNavKontor("4400"),
                meldekortId = "M1",
            )
        }

    assertEquals(
        forventetUtbetalingsperiode.erEndringPåEksisterendePeriode,
        utbetalingsperiode.erEndringPåEksisterendePeriode,
    )
    assertEquals(forventetStønadsdata.tilKlassifisering(), utbetalingsperiode.klassifisering)
    assertEquals(forventetUtbetalingsperiode.periodeId, utbetalingsperiode.periodeId)
    assertEquals(forventetUtbetalingsperiode.forrigePeriodeId, utbetalingsperiode.forrigePeriodeId)
    assertEquals(forventetUtbetalingsperiode.sats, utbetalingsperiode.sats.toInt())
    assertEquals(forventetUtbetalingsperiode.satstype, utbetalingsperiode.satstype)
    assertEquals(forventetUtbetalingsperiode.fom, utbetalingsperiode.fom)
    assertEquals(forventetUtbetalingsperiode.tom, utbetalingsperiode.tom)
    assertEquals(forventetUtbetalingsperiode.opphør, utbetalingsperiode.opphør?.fom)
}

object ValideringUtil {
    fun <T : WithBehandlingId> assertSjekkBehandlingIder(
        dataTable: DataTable,
        utbetalingsoppdrag: MutableMap<BehandlingId, BeregnetUtbetalingsoppdrag>,
        parser: (Iterator<String>) -> T
    ) {
        val eksisterendeBehandlingId = utbetalingsoppdrag
            .filter { it.value.utbetalingsoppdrag.utbetalingsperiode.isNotEmpty() }
            .keys

        val forventedeBehandlingId = dataTable.expected.into(parser)
            .map { it.behandlingId }
            .toSet()

        val ukontrollerteBehandlingId = eksisterendeBehandlingId.filterNot { forventedeBehandlingId.contains(it) }

        if (ukontrollerteBehandlingId.isNotEmpty()) {
            error("Har ikke kontrollert behandlingene:$ukontrollerteBehandlingId")
        }
    }
}
