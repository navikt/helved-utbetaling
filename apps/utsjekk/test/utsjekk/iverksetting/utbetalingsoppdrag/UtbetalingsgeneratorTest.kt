package utsjekk.iverksetting.utbetalingsoppdrag

import AndelId
import TestData.domain.andelData
import TestData.domain.andelMedPeriodeId
import TestData.domain.behandlingsinformasjon
import TestData.domain.beregnetUtbetalingsoppdrag
import TestData.domain.utbetalingsperiode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Int.feb: LocalDate get() = LocalDate.of(2021, 2, this)
private val Int.mar: LocalDate get() = LocalDate.of(2021, 3, this)
private val Int.apr: LocalDate get() = LocalDate.of(2021, 4, this)
private val Int.may: LocalDate get() = LocalDate.of(2021, 5, this)

/**
 * Tester det samme som to av scenarioene i bdd (behaviour driven development),
 * men med eksplisitt oppbygging av utbetalingsoppdrag og forventet resultat.
 */
class UtbetalingsgeneratorTest {

    @AfterEach
    fun reset() {
        AndelId.reset()
    }

    @Test
    fun `kan legge til ny periode`() {
        val sakId = SakId(RandomOSURId.generate())

        val behId1 = BehandlingId(RandomOSURId.generate())
        val andel1 = andelData(1.feb, 31.mar, 700)
        val oppdrag1 = Utbetalingsgenerator.lagUtbetalingsoppdrag(
            behandlingsinformasjon = behandlingsinformasjon(fagsakId = sakId, behandlingId = behId1),
            requested = listOf(andel1),
            existing = emptyList(),
            lastExistingByKjede = emptyMap(),
        )
        val expected1 = beregnetUtbetalingsoppdrag(
            sakId = sakId,
            erFørsteUtbetalingPåSak = true,
            andeler = listOf(andelMedPeriodeId("0", 0, null)),
            personident = oppdrag1.utbetalingsoppdrag.aktør,
            utbetalingsperiode(behId1, 1.feb, 31.mar, 700, 0, null, oppdrag1.utbetalingsoppdrag.aktør)
        )
        assertEquals(expected1, oppdrag1)

        val behId2 = BehandlingId(RandomOSURId.generate())
        val andel1_v2 = andelData(1.feb, 31.mar, 700) // trenger ny andelId
        val andel2 = andelData(1.apr, 31.may, 900)    // skal ha andelId +1 i forhold til den nye kopien av andel1 (andel1_v2)
        val oppdrag2 = Utbetalingsgenerator.lagUtbetalingsoppdrag(
            behandlingsinformasjon = behandlingsinformasjon(fagsakId = sakId, behandlingId = behId2),
            requested = listOf(andel1_v2, andel2),
            existing = listOf(oppdrag1.copyPeriodeIdTo(andel1)),
            lastExistingByKjede = listOf(oppdrag1.copyPeriodeIdTo(andel1))
                .uten0beløp()
                .groupBy { andel -> andel.stønadsdata.tilKjedenøkkel() }
                .mapValues { (_, andeler) -> andeler.latestByPeriodeId() }
        )
        val expected2 = beregnetUtbetalingsoppdrag(
            sakId = sakId,
            erFørsteUtbetalingPåSak = false,
            andeler = listOf(andelMedPeriodeId("1", 0, null), andelMedPeriodeId("2", 1, 0)),
            personident = oppdrag2.utbetalingsoppdrag.aktør,
            utbetalingsperiode(behId2, 1.apr, 31.may, 900, 1, 0, oppdrag2.utbetalingsoppdrag.aktør)
        )
        assertEquals(expected2, oppdrag2)
    }

    @Test
    fun `periode er idempotent`() {
        val sakId = SakId(RandomOSURId.generate())
        val behId1 = BehandlingId(RandomOSURId.generate())
        val behId2 = BehandlingId(RandomOSURId.generate())

        val andel1 = andelData(1.feb, 31.mar, 700)
        val andel2 = andelData(1.feb, 31.mar, 700)

        val oppdrag1 = Utbetalingsgenerator.lagUtbetalingsoppdrag(
            behandlingsinformasjon = behandlingsinformasjon(fagsakId = sakId, behandlingId = behId1),
            requested = listOf(andel1),
            existing = emptyList(),
            lastExistingByKjede = emptyMap(),
        )

        val oppdrag2 = Utbetalingsgenerator.lagUtbetalingsoppdrag(
            behandlingsinformasjon = behandlingsinformasjon(fagsakId = sakId, behandlingId = behId2),
            requested = listOf(andel2),
            existing = listOf(oppdrag1.copyPeriodeIdTo(andel1)),
            lastExistingByKjede = listOf(oppdrag1.copyPeriodeIdTo(andel1))
                .uten0beløp()
                .groupBy { andel -> andel.stønadsdata.tilKjedenøkkel() }
                .mapValues { (_, andeler) -> andeler.latestByPeriodeId() },
        )

        val expected1 = beregnetUtbetalingsoppdrag(
            sakId = sakId,
            erFørsteUtbetalingPåSak = true,
            andeler = listOf(andelMedPeriodeId("0", 0, null)),
            personident = oppdrag1.utbetalingsoppdrag.aktør,
            utbetalingsperiode(behId1, 1.feb, 31.mar, 700, 0, null, oppdrag1.utbetalingsoppdrag.aktør),
        )

        val expected2 = beregnetUtbetalingsoppdrag(
            sakId = sakId,
            erFørsteUtbetalingPåSak = false,
            andeler = listOf(andelMedPeriodeId("1", 0, null)),
            personident = oppdrag2.utbetalingsoppdrag.aktør
        )

        assertEquals(expected1, oppdrag1)
        assertEquals(expected2, oppdrag2)
    }
}

/**
 * Find latest andel by `periodeId`.
 * When duplicate latest `periodeId's` are found (e.g. opphør) select the latest by `tom` (til og med).
 */
private fun List<AndelData>.latestByPeriodeId(): AndelData =
    groupBy { it.periodeId }
        .maxBy { (periodeId, _) -> requireNotNull(periodeId) { "BeregnetUtbetalingsoppdrag.copyPeriodeIdTo(AndelData) should be called first." } }
        .value
        .maxBy { it.tom }

/**
 * Utbetalingsgenerator will set `periodeId` and `forrigePeriodeId`.
 * When chaining (kjeding) we should use the IDs previously generated.
 * Copy them from the previous `oppdrag`
 */
private fun BeregnetUtbetalingsoppdrag.copyPeriodeIdTo(andel: AndelData): AndelData =
    when (andel.beløp) {
        0 -> andel
        else -> andel.copy(
            periodeId = andeler.firstOrNull()?.periodeId,
            forrigePeriodeId = andeler.firstOrNull()?.forrigePeriodeId
        )
    }

private fun BeregnetUtbetalingsoppdrag.copyAvstemmingstidspunktFrom(other: BeregnetUtbetalingsoppdrag): BeregnetUtbetalingsoppdrag =
    copy(
        utbetalingsoppdrag = utbetalingsoppdrag.copy(
            avstemmingstidspunkt = other.utbetalingsoppdrag.avstemmingstidspunkt
        )
    )

private fun assertEquals(expected: BeregnetUtbetalingsoppdrag, actual: BeregnetUtbetalingsoppdrag) {
    // TODO: fungerer ikke hvis testen utføres i et sekundbytte
    val expectedAvstemming = expected.utbetalingsoppdrag.avstemmingstidspunkt.truncatedTo(ChronoUnit.SECONDS)
    val actualAvstemming = actual.utbetalingsoppdrag.avstemmingstidspunkt.truncatedTo(ChronoUnit.SECONDS)
    assertEquals(expectedAvstemming, actualAvstemming)
    val expected = expected.copyAvstemmingstidspunktFrom(actual)
    Assertions.assertEquals(expected, actual)
}
