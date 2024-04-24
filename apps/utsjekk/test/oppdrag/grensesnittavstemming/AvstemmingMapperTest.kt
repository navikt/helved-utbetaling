package oppdrag.grensesnittavstemming

import libs.xml.XMLMapper
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.Resource
import oppdrag.etUtbetalingsoppdrag
import oppdrag.iverksetting.tilstand.OppdragLager
import oppdrag.somOppdragLager
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals

internal class AvstemmingMapperTest {

    @Test
    fun `test mapping av tom liste`() {
        val mapper =
            AvstemmingMapper(
                oppdragsliste = emptyList(),
                fagsystem = Fagsystem.DAGPENGER,
                fom = LocalDateTime.now(),
                tom = LocalDateTime.now(),
            )
        val meldinger = mapper.lagAvstemmingsmeldinger()

        assertEquals(0, meldinger.size)
    }

    @Test
    fun `mapping til grensesnittavstemming`() {
        val avstemmingstidspunkt = LocalDateTime.now().minusDays(1).withHour(13)
        val fom = avstemmingstidspunkt.toLocalDate().atStartOfDay()
        val tom = avstemmingstidspunkt.toLocalDate().atTime(LocalTime.MAX)
        val utbetalingsoppdrag =
            etUtbetalingsoppdrag(
                avstemmingstidspunkt = avstemmingstidspunkt,
                fagsystem = Fagsystem.DAGPENGER,
            )
        val oppdragLager = utbetalingsoppdrag.somOppdragLager
        val mapper =
            AvstemmingMapper(listOf(oppdragLager), Fagsystem.DAGPENGER, fom, tom)
        val meldinger = mapper.lagAvstemmingsmeldinger()

        assertEquals(3, meldinger.size)
        assertAksjon(fom, tom, AksjonType.START, meldinger.first().aksjon)
        assertAksjon(fom, tom, AksjonType.DATA, meldinger[1].aksjon)
        assertAksjon(fom, tom, AksjonType.AVSL, meldinger.last().aksjon)

        assertDetaljData(oppdragLager, DetaljType.MANG, meldinger[1].detalj.first())
        assertTotalData(
            forventetAntall = 1,
            forventetTotalbeløp = BigDecimal(100),
            actual = meldinger[1].total,
        )
        assertPeriodeData(utbetalingsoppdrag, meldinger[1].periode)
        assertGrunnlagsdata(
            forventetManglerAntall = 1,
            forventetManglerBeløp = BigDecimal(100),
            actual = meldinger[1].grunnlag,
        )
    }

    @Test
    fun `mapping til grensesnittavstemming med feilede oppdrag`() {
        val avstemmingstidspunkt = LocalDateTime.now().minusDays(1).withHour(13)
        val fom = avstemmingstidspunkt.toLocalDate().atStartOfDay()
        val tom = avstemmingstidspunkt.toLocalDate().atTime(LocalTime.MAX)
        val utbetalingsoppdrag =
            etUtbetalingsoppdrag(
                avstemmingstidspunkt = avstemmingstidspunkt,
                fagsystem = Fagsystem.DAGPENGER,
            )
        val oppdragLager = utbetalingsoppdrag.somOppdragLager
        val oppdragLager2 =
            utbetalingsoppdrag.somOppdragLager.copy(
                kvitteringsmelding = avvistKvitteringsmelding(),
                status = OppdragStatus.KVITTERT_FUNKSJONELL_FEIL,
            )
        val mapper =
            AvstemmingMapper(listOf(oppdragLager, oppdragLager2), Fagsystem.DAGPENGER, fom, tom)
        val meldinger = mapper.lagAvstemmingsmeldinger()

        assertEquals(3, meldinger.size)
        assertAksjon(fom, tom, AksjonType.START, meldinger.first().aksjon)
        assertAksjon(fom, tom, AksjonType.DATA, meldinger[1].aksjon)
        assertAksjon(fom, tom, AksjonType.AVSL, meldinger.last().aksjon)

        assertEquals(2, meldinger[1].detalj.size)
        assertDetaljData(oppdragLager, DetaljType.MANG, meldinger[1].detalj.first())
        assertDetaljData(oppdragLager2, DetaljType.AVVI, meldinger[1].detalj.last())
        assertTotalData(2, BigDecimal(200), meldinger[1].total)
        assertPeriodeData(utbetalingsoppdrag, meldinger[1].periode)
        assertGrunnlagsdata(
            forventetManglerAntall = 1,
            forventetManglerBeløp = BigDecimal(100),
            forventetAvvistAntall = 1,
            forventetAvvistBeløp = BigDecimal(100),
            actual = meldinger[1].grunnlag,
        )
    }

    @Test
    fun `fom og tom blir satt riktig ved grensesnittavstemming`() {
        val førsteAvstemmingstidspunkt = LocalDateTime.now().minusDays(1).withHour(13)
        val andreAvstemmingstidspunkt = LocalDateTime.now().minusDays(1).withHour(15)
        val avstemmingFom = førsteAvstemmingstidspunkt.toLocalDate().atStartOfDay()
        val avstemmingTom = andreAvstemmingstidspunkt.toLocalDate().atTime(LocalTime.MAX)
        val baOppdragLager1 =
            etUtbetalingsoppdrag(
                avstemmingstidspunkt = førsteAvstemmingstidspunkt,
                fagsystem = Fagsystem.DAGPENGER,
            ).somOppdragLager
        val baOppdragLager2 =
            etUtbetalingsoppdrag(
                andreAvstemmingstidspunkt,
                Fagsystem.DAGPENGER,
            ).somOppdragLager
        val mapper =
            AvstemmingMapper(
                listOf(baOppdragLager1, baOppdragLager2),
                Fagsystem.DAGPENGER,
                avstemmingFom,
                avstemmingTom,
            )
        val meldinger = mapper.lagAvstemmingsmeldinger()
        assertEquals(3, meldinger.size)
        assertEquals(avstemmingFom.format(timeFormatter), meldinger.first().aksjon.nokkelFom)
        assertEquals(avstemmingTom.format(timeFormatter), meldinger.first().aksjon.nokkelTom)
    }

    private fun assertAksjon(
        avstemmingFom: LocalDateTime,
        avstemmingTom: LocalDateTime,
        expected: AksjonType,
        actual: Aksjonsdata,
    ) {
        assertEquals(expected, actual.aksjonType)
        assertEquals(KildeType.AVLEV, actual.kildeType)
        assertEquals(AvstemmingType.GRSN, actual.avstemmingType)
        assertEquals(Fagsystem.DAGPENGER.kode, actual.avleverendeKomponentKode)
        assertEquals("OS", actual.mottakendeKomponentKode)
        assertEquals(Fagsystem.DAGPENGER.kode, actual.underkomponentKode)
        assertEquals(avstemmingFom.format(timeFormatter), actual.nokkelFom)
        assertEquals(avstemmingTom.format(timeFormatter), actual.nokkelTom)
        assertEquals(Fagsystem.DAGPENGER.kode, actual.brukerId)
    }

    private fun assertDetaljData(
        oppdragLager: OppdragLager,
        forventetDetaljtype: DetaljType,
        actual: Detaljdata,
    ) {
        assertEquals(oppdragLager.utbetalingsoppdrag.aktør, actual.offnr)
        assertEquals(oppdragLager.utbetalingsoppdrag.saksnummer, actual.avleverendeTransaksjonNokkel)
        assertEquals(oppdragLager.utbetalingsoppdrag.avstemmingstidspunkt.format(timeFormatter), actual.tidspunkt)
        assertEquals(forventetDetaljtype, actual.detaljType)
        assertEquals(oppdragLager.kvitteringsmelding?.kodeMelding, actual.meldingKode)
        assertEquals(oppdragLager.kvitteringsmelding?.alvorlighetsgrad, actual.alvorlighetsgrad)
        assertEquals(oppdragLager.kvitteringsmelding?.beskrMelding, actual.tekstMelding)
    }

    private fun assertTotalData(
        forventetAntall: Int,
        forventetTotalbeløp: BigDecimal,
        actual: Totaldata,
    ) {
        assertEquals(forventetAntall, actual.totalAntall)
        assertEquals(forventetTotalbeløp, actual.totalBelop)
        assertEquals(Fortegn.T, actual.fortegn)
    }

    private fun assertPeriodeData(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        actual: Periodedata,
    ) {
        assertEquals(
            utbetalingsoppdrag.avstemmingstidspunkt.format(DateTimeFormatter.ofPattern("yyyyMMddHH")),
            actual.datoAvstemtFom,
        )
        assertEquals(
            utbetalingsoppdrag.avstemmingstidspunkt.format(DateTimeFormatter.ofPattern("yyyyMMddHH")),
            actual.datoAvstemtTom,
        )
    }

    private fun assertGrunnlagsdata(
        forventetManglerAntall: Int = 0,
        forventetManglerBeløp: BigDecimal = BigDecimal.ZERO,
        forventetAvvistAntall: Int = 0,
        forventetAvvistBeløp: BigDecimal = BigDecimal.ZERO,
        forventetGodkjentAntall: Int = 0,
        forventetGodkjentBeløp: BigDecimal = BigDecimal.ZERO,
        actual: Grunnlagsdata,
    ) {
        assertEquals(forventetManglerAntall, actual.manglerAntall)
        assertEquals(forventetManglerBeløp, actual.manglerBelop)
        assertEquals(Fortegn.T, actual.manglerFortegn)

        assertEquals(forventetGodkjentAntall, actual.godkjentAntall)
        assertEquals(forventetGodkjentBeløp, actual.godkjentBelop)
        assertEquals(Fortegn.T, actual.godkjentFortegn)

        assertEquals(forventetAvvistAntall, actual.avvistAntall)
        assertEquals(forventetAvvistBeløp, actual.avvistBelop)
        assertEquals(Fortegn.T, actual.avvistFortegn)
    }

    private val mapper = XMLMapper<Oppdrag>()
    private fun avvistKvitteringsmelding() = mapper.readValue(Resource.read("/kvittering-avvist.xml")).mmel

    companion object {
        private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
    }
}
