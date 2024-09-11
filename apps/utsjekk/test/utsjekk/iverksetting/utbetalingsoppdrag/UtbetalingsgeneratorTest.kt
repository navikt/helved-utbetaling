package utsjekk.iverksetting.utbetalingsoppdrag

import TestData
import no.nav.utsjekk.kontrakter.felles.Satstype
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsperiode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class UtbetalingsgeneratorTest {

    @Test
    fun `periode er idempotent`() {
        val sakId = SakId(RandomOSURId.generate())
        val behId1 = BehandlingId(RandomOSURId.generate())
        val behId2 = BehandlingId(RandomOSURId.generate())

        val førsteAndel = TestData.domain.andelData(
            fom = LocalDate.of(2021, 2, 1),
            tom = LocalDate.of(2021, 3, 31),
            beløp = 700,
        )

        assertTrue(førsteAndel.periodeId == null && førsteAndel.forrigePeriodeId == null)

        val oppdrag1 = Utbetalingsgenerator.lagUtbetalingsoppdrag(
            behandlingsinformasjon = TestData.domain.behandlingsinformasjon(
                fagsakId = sakId,
                behandlingId = behId1
            ),
            nyeAndeler = listOf(førsteAndel),
            forrigeAndeler = emptyList(),
            sisteAndelPerKjede = emptyMap(),
        )


        val forrigeAndel = oppdrag1.copyPeriodeIdTo(førsteAndel)
        val sisteAndelPerKjede = listOf(forrigeAndel)
            .uten0beløp()
            .groupBy { andel -> andel.stønadsdata.tilKjedenøkkel() }
            .mapValues { (_, andeler) -> andeler.latestByPeriodeId() }

        val nyAndel = TestData.domain.andelData(
            fom = LocalDate.of(2021, 2, 1),
            tom = LocalDate.of(2021, 3, 31),
            beløp = 700,
        )

        assertTrue(nyAndel.periodeId == null && nyAndel.forrigePeriodeId == null)

        val oppdrag2 = Utbetalingsgenerator.lagUtbetalingsoppdrag(
            behandlingsinformasjon = TestData.domain.behandlingsinformasjon(
                fagsakId = sakId,
                behandlingId = behId2,
            ),
            nyeAndeler = listOf(nyAndel),
            forrigeAndeler = listOf(forrigeAndel),
            sisteAndelPerKjede = sisteAndelPerKjede,
        )

        val expected1 = BeregnetUtbetalingsoppdrag(
            utbetalingsoppdrag = Utbetalingsoppdrag(
                erFørsteUtbetalingPåSak = true,
                fagsystem = TestData.DEFAULT_FAGSYSTEM,
                saksnummer = sakId.id,
                iverksettingId = null,
                aktør = TestData.DEFAULT_PERSONIDENT,
                saksbehandlerId = TestData.DEFAULT_SAKSBEHANDLER,
                beslutterId = TestData.DEFAULT_BESLUTTER,
                avstemmingstidspunkt = LocalDateTime.now(), // ca
                utbetalingsperiode = listOf(
                    Utbetalingsperiode(
                        erEndringPåEksisterendePeriode = false,
                        opphør = null,
                        periodeId = 0L,
                        forrigePeriodeId = null,
                        vedtaksdato = LocalDate.now(),
                        klassifisering = "DPORAS",
                        fom = LocalDate.of(2021, 2, 1),
                        tom = LocalDate.of(2021, 3, 31),
                        sats = 700.toBigDecimal(),
                        satstype = Satstype.DAGLIG,
                        utbetalesTil = TestData.DEFAULT_PERSONIDENT,
                        behandlingId = behId1.id,
                        utbetalingsgrad = null,
                    )
                ),
                brukersNavKontor = null,
            ),
            andeler = listOf(
                AndelMedPeriodeId(
                    id = "0",
                    periodeId = 0L,
                    forrigePeriodeId = null,
                )
            )
        )
        val expected2 = BeregnetUtbetalingsoppdrag(
            utbetalingsoppdrag = Utbetalingsoppdrag(
                erFørsteUtbetalingPåSak = false,
                fagsystem = TestData.DEFAULT_FAGSYSTEM,
                saksnummer = sakId.id,
                iverksettingId = null,
                aktør = TestData.DEFAULT_PERSONIDENT,
                saksbehandlerId = TestData.DEFAULT_SAKSBEHANDLER,
                beslutterId = TestData.DEFAULT_BESLUTTER,
                avstemmingstidspunkt = LocalDateTime.now(),
                utbetalingsperiode = emptyList(),
                brukersNavKontor = null,
            ),
            andeler = listOf(
                AndelMedPeriodeId(
                    id = "1",
                    periodeId = 0L,
                    forrigePeriodeId = null,
                )
            )
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
    val expectedAvstemming = expected.utbetalingsoppdrag.avstemmingstidspunkt.truncatedTo(ChronoUnit.SECONDS)
    val actualAvstemming = actual.utbetalingsoppdrag.avstemmingstidspunkt.truncatedTo(ChronoUnit.SECONDS)
    assertEquals(expectedAvstemming, actualAvstemming)
    val expected = expected.copyAvstemmingstidspunktFrom(actual)
    Assertions.assertEquals(expected, actual)
}
