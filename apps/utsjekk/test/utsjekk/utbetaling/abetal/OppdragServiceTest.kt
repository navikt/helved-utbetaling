package utsjekk.utbetaling.abetal

import TestData
import org.junit.jupiter.api.Test
import utsjekk.utbetaling.*
import java.math.BigDecimal
import java.time.LocalDate
import javax.xml.datatype.XMLGregorianCalendar
import kotlin.test.assertEquals

class OppdragServiceTest {

    /** Opprette ny utbetaling, én stønadstype
     *
     * 1:    ╭─────────────────╮
     *       │ TILSYN_BARN_AAP │
     *       │ 100,-           │
     *       ╰─────────────────╯
     * Res:  ╭─────────────────╮
     *       │ NY              │
     *       │ TILSYN_BARN_AAP │
     *       │ 100,-           │
     *       ╰─────────────────╯
     */
    @Test
    fun `én stønad, oppretting`() {
        val utbetaling = TestData.domain.utbetalingV2(
            kjeder = mapOf(
                "TILSYN_BARN_AAP" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 100u)
                    ),
                )
            )
        )
        val oppdrag = OppdragService.opprett(utbetaling, false)

        assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s.first().also { oppdrag ->
            assertEquals("DAG", oppdrag.typeSats)
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_AAP.klassekode, oppdrag.kodeKlassifik)
            assertEquals(1.jan, oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(1.jan, oppdrag.datoVedtakTom.toLocalDate())
            assertEquals(BigDecimal.valueOf(100), oppdrag.sats)
        }
    }

    /** Oppdater utbetaling med én ny periode, én stønadstype
     *
     * 1:    ╭─────────────────╮
     *       │ TILSYN_BARN_AAP │
     *       │ 100,-           │
     *       ╰─────────────────╯
     * 2:    ╭─────────────────╮╭─────────────────╮
     *       │ TILSYN_BARN_AAP ││ TILSYN_BARN_AAP │
     *       │ 100,-           ││ 100,-           │
     *       ╰─────────────────╯╰─────────────────╯
     * Res:                     ╭─────────────────╮
     *                          │ NY              │
     *                          │ TILSYN_BARN_AAP │
     *                          │ 100,-           │
     *                          ╰─────────────────╯
     */
    @Test
    fun `én stønad, oppdatering`() {
        val old = TestData.domain.utbetalingV2(
            kjeder = mapOf(
                "TILSYN_BARN_AAP" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 100u)
                    ),
                    lastPeriodeId = "AAP#1"
                )
            )
        )
        val new = TestData.domain.utbetalingV2(
            kjeder = mapOf(
                "TILSYN_BARN_AAP" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 100u),
                        TestData.domain.utbetalingsperiode(2.jan, 2.jan, 100u)
                    ),
                )
            )
        )
        val oppdrag = OppdragService.update(new, old)

        assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)

        oppdrag.oppdrag110.oppdragsLinje150s.first().also { oppdrag ->
            assertEquals("DAG", oppdrag.typeSats)
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_AAP.klassekode, oppdrag.kodeKlassifik)
            assertEquals(BigDecimal.valueOf(100), oppdrag.sats)
            assertEquals(2.jan, oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(2.jan, oppdrag.datoVedtakTom.toLocalDate())
            assertEquals("AAP#1", oppdrag.refDelytelseId)
        }
    }

    /** Opprette ny utbetaling, to stønadstyper
     *
     * 1:    ╭──────────────────────────────╮
     *       │ TILSYN_BARN_AAP              │
     *       │ 620,-                        │
     *       ╰──────────────────────────────╯
     *       ╭──────────────────────────────╮
     *       │ TILSYN_BARN_ENSLIG_FORSØRGER │
     *       │ 207,-                        │
     *       ╰──────────────────────────────╯
     * Res:  ╭──────────────────────────────╮
     *       │ NY                           │
     *       │ TILSYN_BARN_AAP              │
     *       │ 620,-                        │
     *       ╰──────────────────────────────╯
     *       ╭──────────────────────────────╮
     *       │ NY                           │
     *       │ TILSYN_BARN_ENSLIG_FORSØRGER │
     *       │ 207,-                        │
     *       ╰──────────────────────────────╯
     */
    @Test
    fun `to stønader, oppretting`() {
        val utbetaling = TestData.domain.utbetalingV2(
            kjeder = mapOf(
                "TILSYN_BARN_AAP" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(2024.nov(11), 2024.nov(11), 620u)
                    )
                ),
                "TILSYN_BARN_ENSLIG_FORSØRGER" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(2025.jan(7), 2025.jan(7), 207u)
                    )
                )
            )
        )
        val oppdrag = OppdragService.opprett(utbetaling, false)

        assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)

        oppdrag.oppdrag110.oppdragsLinje150s[0].also { oppdrag ->
            assertEquals("DAG", oppdrag.typeSats)
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_AAP.klassekode, oppdrag.kodeKlassifik)
            assertEquals(BigDecimal.valueOf(620), oppdrag.sats)
            assertEquals(2024.nov(11), oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(2024.nov(11), oppdrag.datoVedtakTom.toLocalDate())
        }

        oppdrag.oppdrag110.oppdragsLinje150s[1].also { oppdrag ->
            assertEquals("DAG", oppdrag.typeSats)
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER.klassekode, oppdrag.kodeKlassifik)
            assertEquals(BigDecimal.valueOf(207), oppdrag.sats)
            assertEquals(2025.jan(7), oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(2025.jan(7), oppdrag.datoVedtakTom.toLocalDate())
        }
    }

    /** Opprette ny utbetaling, to stønadstyper
     *
     * 1:    ╭──────────────────────────────╮
     *       │ TILSYN_BARN_AAP              │
     *       │ 620,-                        │
     *       ╰──────────────────────────────╯
     *       ╭──────────────────────────────╮
     *       │ TILSYN_BARN_ENSLIG_FORSØRGER │
     *       │ 207,-                        │
     *       ╰──────────────────────────────╯
     * 2:    ╭──────────────────────────────╮╭──────────────────────────────╮
     *       │ TILSYN_BARN_AAP              ││ TILSYN_BARN_AAP              │
     *       │ 620,-                        ││ 620,-                        │
     *       ╰──────────────────────────────╯╰──────────────────────────────╯
     *       ╭──────────────────────────────╮
     *       │ TILSYN_BARN_ENSLIG_FORSØRGER │
     *       │ 207,-                        │
     *       ╰──────────────────────────────╯
     * Res:                                  ╭──────────────────────────────╮
     *                                       │ NY                           │
     *                                       │ TILSYN_BARN_AAP              │
     *                                       │ 620,-                        │
     *                                       ╰──────────────────────────────╯
     */
    @Test
    fun `to kjeder, forlenger én kjede`() {
        val old = TestData.domain.utbetalingV2(
            kjeder = mapOf(
                "TILSYN_BARN_AAP" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 620u)
                    ),
                    lastPeriodeId = "AAP#1"
                ),
                "TILSYN_BARN_ENSLIG_FORSØRGER" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 207u)
                    )
                )
            )
        )
        val new = TestData.domain.utbetalingV2(
            kjeder = mapOf(
                "TILSYN_BARN_AAP" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 620u),
                        TestData.domain.utbetalingsperiode(2.jan, 2.jan, 620u)
                    )
                ),
                "TILSYN_BARN_ENSLIG_FORSØRGER" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 207u)
                    )
                )
            )
        )
        val oppdrag = OppdragService.update(new, old)

        assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)

        oppdrag.oppdrag110.oppdragsLinje150s[0].also { oppdrag ->
            assertEquals("DAG", oppdrag.typeSats)
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_AAP.klassekode, oppdrag.kodeKlassifik)
            assertEquals(BigDecimal.valueOf(620), oppdrag.sats)
            assertEquals(2.jan, oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(2.jan, oppdrag.datoVedtakTom.toLocalDate())
            assertEquals("AAP#1", oppdrag.refDelytelseId)
        }
    }
}

fun XMLGregorianCalendar.toLocalDate(): LocalDate = LocalDate.of(year, month, day)