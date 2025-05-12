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
    fun `1 kjede, opprett 1 periode`() {
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

    /** Opprette ny utbetaling, én stønadstype
     *
     * 1:    ╭─────────────────╮╭─────────────────╮╭─────────────────╮
     *       │ TILSYN_BARN_AAP ││ TILSYN_BARN_AAP ││ TILSYN_BARN_AAP │
     *       │ 100,-           ││ 100,-           ││ 100,-           │
     *       ╰─────────────────╯╰─────────────────╯╰─────────────────╯
     * Res:  ╭─────────────────╮╭─────────────────╮╭─────────────────╮
     *       │ NY              ││ NY              ││ NY              │
     *       │ TILSYN_BARN_AAP ││ TILSYN_BARN_AAP ││ TILSYN_BARN_AAP │
     *       │ 100,-           ││ 100,-           ││ 100,-           │
     *       ╰─────────────────╯╰─────────────────╯╰─────────────────╯
     */
    @Test
    fun `1 kjede, opprett 3 periode`() {
        val utbetaling = TestData.domain.utbetalingV2(
            kjeder = mapOf(
                "TILSYN_BARN_AAP" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 100u),
                        TestData.domain.utbetalingsperiode(2.jan, 2.jan, 100u),
                        TestData.domain.utbetalingsperiode(3.jan, 3.jan, 100u),
                    ),
                )
            )
        )
        val oppdrag = OppdragService.opprett(utbetaling, false)

        assertEquals(3, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s.forEach { oppdrag ->
            assertEquals("DAG", oppdrag.typeSats)
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_AAP.klassekode, oppdrag.kodeKlassifik)
            assertEquals(BigDecimal.valueOf(100), oppdrag.sats)
        }
    }

    /** Forleng utbetaling med én ny periode, én kjede
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
    fun `1 kjede, forleng med 1 periode`() {
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
    fun `2 kjeder, opprett 1 periode per kjede`() {
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
    fun `2 kjeder, forlenger 1 kjede med 1 periode`() {
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

    /** Forleng to kjeder
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
     *       ╭──────────────────────────────╮╭──────────────────────────────╮
     *       │ TILSYN_BARN_ENSLIG_FORSØRGER ││ TILSYN_BARN_ENSLIG_FORSØRGER │
     *       │ 207,-                        ││ 207,-                        │
     *       ╰──────────────────────────────╯╰──────────────────────────────╯
     * Res:                                  ╭──────────────────────────────╮
     *                                       │ NY                           │
     *                                       │ TILSYN_BARN_AAP              │
     *                                       │ 620,-                        │
     *                                       ╰──────────────────────────────╯
     *                                       ╭──────────────────────────────╮
     *                                       │ NY                           │
     *                                       │ TILSYN_BARN_ENSLIG_FORSØRGER │
     *                                       │ 207,-                        │
     *                                       ╰──────────────────────────────╯
     */
    @Test
    fun `2 kjeder, forlenger 2 kjede med 1 periode`() {
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
                    ),
                    lastPeriodeId = "AAP#0")
            )
        )
        val new = TestData.domain.utbetalingV2(
            kjeder = mapOf(
                "TILSYN_BARN_AAP" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 620u),
                        TestData.domain.utbetalingsperiode(2.jan, 2.jan, 620u),
                    )
                ),
                "TILSYN_BARN_ENSLIG_FORSØRGER" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 207u),
                        TestData.domain.utbetalingsperiode(2.jan, 2.jan, 207u),
                    )
                )
            )
        )
        val oppdrag = OppdragService.update(new, old)

        assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].also { oppdrag ->
            assertEquals("DAG", oppdrag.typeSats)
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_AAP.klassekode, oppdrag.kodeKlassifik)
            assertEquals(BigDecimal.valueOf(620), oppdrag.sats)
            assertEquals(2.jan, oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(2.jan, oppdrag.datoVedtakTom.toLocalDate())
            assertEquals("AAP#1", oppdrag.refDelytelseId)
        }
        oppdrag.oppdrag110.oppdragsLinje150s[1].also { oppdrag ->
            assertEquals("DAG", oppdrag.typeSats)
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER.klassekode, oppdrag.kodeKlassifik)
            assertEquals(BigDecimal.valueOf(207), oppdrag.sats)
            assertEquals(2.jan, oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(2.jan, oppdrag.datoVedtakTom.toLocalDate())
            assertEquals("AAP#0", oppdrag.refDelytelseId)
        }
    }

    /** Forkort to kjeder i slutten
     *
     * 1:    ╭──────────────────────────────╮╭──────────────────────────────╮
     *       │ TILSYN_BARN_AAP              ││ TILSYN_BARN_AAP              │
     *       │ 620,-                        ││ 620,-                        │
     *       ╰──────────────────────────────╯╰──────────────────────────────╯
     *       ╭──────────────────────────────╮╭──────────────────────────────╮
     *       │ TILSYN_BARN_ENSLIG_FORSØRGER ││ TILSYN_BARN_ENSLIG_FORSØRGER │
     *       │ 207,-                        ││ 207,-                        │
     *       ╰──────────────────────────────╯╰──────────────────────────────╯
     * 2:    ╭──────────────────────────────╮
     *       │ TILSYN_BARN_AAP              │
     *       │ 620,-                        │
     *       ╰──────────────────────────────╯
     *       ╭──────────────────────────────╮
     *       │ TILSYN_BARN_ENSLIG_FORSØRGER │
     *       │ 207,-                        │
     *       ╰──────────────────────────────╯
     * Res:                                  ╭──────────────────────────────╮
     *                                       │ OPPHØR                       │
     *                                       │ TILSYN_BARN_AAP              │
     *                                       ╰──────────────────────────────╯
     *                                       ╭──────────────────────────────╮
     *                                       │ OPPHØR                       │
     *                                       │ TILSYN_BARN_ENSLIG_FORSØRGER │
     *                                       ╰──────────────────────────────╯
     */
    @Test
    fun `2 kjeder, forkort 2 kjeder i slutten`() {
        val old = TestData.domain.utbetalingV2(
            kjeder = mapOf(
                "TILSYN_BARN_AAP" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 620u),
                        TestData.domain.utbetalingsperiode(2.jan, 2.jan, 620u),
                    ),
                    lastPeriodeId = "AAP#1"
                ),
                "TILSYN_BARN_ENSLIG_FORSØRGER" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 207u),
                        TestData.domain.utbetalingsperiode(2.jan, 2.jan, 207u),
                    ),
                    lastPeriodeId = "AAP#0")
            )
        )
        val new = TestData.domain.utbetalingV2(
            kjeder = mapOf(
                "TILSYN_BARN_AAP" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 620u),
                    )
                ),
                "TILSYN_BARN_ENSLIG_FORSØRGER" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 207u),
                    )
                )
            )
        )
        val oppdrag = OppdragService.update(new, old)

        assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].also { oppdrag ->
            assertEquals("ENDR", oppdrag.kodeEndringLinje)
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_AAP.klassekode, oppdrag.kodeKlassifik)
            assertEquals(2.jan, oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(2.jan, oppdrag.datoVedtakTom.toLocalDate())
            assertEquals("AAP#1", oppdrag.delytelseId)
            assertEquals("OPPH", oppdrag.kodeStatusLinje.name)
        }
        oppdrag.oppdrag110.oppdragsLinje150s[1].also { oppdrag ->
            assertEquals("ENDR", oppdrag.kodeEndringLinje)
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER.klassekode, oppdrag.kodeKlassifik)
            assertEquals(2.jan, oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(2.jan, oppdrag.datoVedtakTom.toLocalDate())
            assertEquals("AAP#0", oppdrag.delytelseId)
            assertEquals("OPPH", oppdrag.kodeStatusLinje.name)
        }
    }

    /** Forkort to kjeder i starten
     *
     * 1:    ╭──────────────────────────────╮╭──────────────────────────────╮
     *       │ TILSYN_BARN_AAP              ││ TILSYN_BARN_AAP              │
     *       │ 620,-                        ││ 620,-                        │
     *       ╰──────────────────────────────╯╰──────────────────────────────╯
     *       ╭──────────────────────────────╮╭──────────────────────────────╮
     *       │ TILSYN_BARN_ENSLIG_FORSØRGER ││ TILSYN_BARN_ENSLIG_FORSØRGER │
     *       │ 207,-                        ││ 207,-                        │
     *       ╰──────────────────────────────╯╰──────────────────────────────╯
     * 2:    ╭──────────────────────────────╮
     *       │ TILSYN_BARN_AAP              │
     *       │ 620,-                        │
     *       ╰──────────────────────────────╯
     *       ╭──────────────────────────────╮
     *       │ TILSYN_BARN_ENSLIG_FORSØRGER │
     *       │ 207,-                        │
     *       ╰──────────────────────────────╯
     * Res:                                  ╭──────────────────────────────╮
     *                                       │ OPPHØR                       │
     *                                       │ TILSYN_BARN_AAP              │
     *                                       ╰──────────────────────────────╯
     *                                       ╭──────────────────────────────╮
     *                                       │ OPPHØR                       │
     *                                       │ TILSYN_BARN_ENSLIG_FORSØRGER │
     *                                       ╰──────────────────────────────╯
     */
    @Test
    fun `2 kjeder, forkort 2 kjeder i starten`() {
        val old = TestData.domain.utbetalingV2(
            kjeder = mapOf(
                "TILSYN_BARN_AAP" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 620u),
                        TestData.domain.utbetalingsperiode(2.jan, 2.jan, 620u),
                    ),
                    lastPeriodeId = "AAP#1"
                ),
                "TILSYN_BARN_ENSLIG_FORSØRGER" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 207u),
                        TestData.domain.utbetalingsperiode(2.jan, 2.jan, 207u),
                    ),
                    lastPeriodeId = "AAP#0")
            )
        )
        val new = TestData.domain.utbetalingV2(
            kjeder = mapOf(
                "TILSYN_BARN_AAP" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(2.jan, 2.jan, 333u),
                    )
                ),
                "TILSYN_BARN_ENSLIG_FORSØRGER" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(2.jan, 2.jan, 111u),
                    )
                )
            )
        )
        val oppdrag = OppdragService.update(new, old)

        assertEquals(4, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].also { oppdrag ->
            assertEquals("ENDR", oppdrag.kodeEndringLinje)
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_AAP.klassekode, oppdrag.kodeKlassifik)
            assertEquals(1.jan, oppdrag.datoStatusFom.toLocalDate())
            assertEquals("AAP#1", oppdrag.delytelseId)
            assertEquals("OPPH", oppdrag.kodeStatusLinje.name)
        }
        oppdrag.oppdrag110.oppdragsLinje150s[1].also { oppdrag ->
            assertEquals("NY", oppdrag.kodeEndringLinje)
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_AAP.klassekode, oppdrag.kodeKlassifik)
            assertEquals(2.jan, oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(2.jan, oppdrag.datoVedtakTom.toLocalDate())
            assertEquals(333, oppdrag.sats.toInt())
            assertEquals("AAP#1", oppdrag.refDelytelseId)
        }
        oppdrag.oppdrag110.oppdragsLinje150s[2].also { oppdrag ->
            assertEquals("ENDR", oppdrag.kodeEndringLinje)
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER.klassekode, oppdrag.kodeKlassifik)
            assertEquals(1.jan, oppdrag.datoStatusFom.toLocalDate())
            assertEquals("AAP#0", oppdrag.delytelseId)
            assertEquals("OPPH", oppdrag.kodeStatusLinje.name)
        }
        oppdrag.oppdrag110.oppdragsLinje150s[3].also { oppdrag ->
            assertEquals("NY", oppdrag.kodeEndringLinje)
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER.klassekode, oppdrag.kodeKlassifik)
            assertEquals(2.jan, oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(2.jan, oppdrag.datoVedtakTom.toLocalDate())
            assertEquals(111, oppdrag.sats.toInt())
            assertEquals("AAP#0", oppdrag.refDelytelseId)
        }
    }

    /** Forkort to kjeder i starten
     *
     * 1:    ╭──────────────────────────────╮╭──────────────────────────────╮╭──────────────────────────────╮╭──────────────────────────────╮╭──────────────────────────────╮
     *       │ TILSYN_BARN_AAP              ││ TILSYN_BARN_AAP              ││ TILSYN_BARN_AAP              ││ TILSYN_BARN_AAP              ││ TILSYN_BARN_AAP              │
     *       │ 100,-                        ││ 200,-                        ││ 300,-                        ││ 400,-                        ││ 500,-                        │
     *       ╰──────────────────────────────╯╰──────────────────────────────╯╰──────────────────────────────╯╰──────────────────────────────╯╰──────────────────────────────╯
     * Res:  ╭──────────────────────────────╮╭──────────────────────────────╮╭──────────────────────────────╮╭──────────────────────────────╮╭──────────────────────────────╮
     *       │ NY                           ││ NY                           ││ NY                           ││ NY                           ││ NY                           │
     *       │ TILSYN_BARN_AAP              ││ TILSYN_BARN_AAP              ││ TILSYN_BARN_AAP              ││ TILSYN_BARN_AAP              ││ TILSYN_BARN_AAP              │
     *       ╰──────────────────────────────╯╰──────────────────────────────╯╰──────────────────────────────╯╰──────────────────────────────╯╰──────────────────────────────╯
     */
    @Test
    fun `opprett 1 lang kjede`() {
        val utbetaling = TestData.domain.utbetalingV2(
            kjeder = mapOf(
                "TILSYN_BARN_AAP" to TestData.domain.utbetalingsperioder(
                    satstype = Satstype.VIRKEDAG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
                    perioder = listOf(
                        TestData.domain.utbetalingsperiode(1.jan, 1.jan, 100u),
                        TestData.domain.utbetalingsperiode(2.jan, 2.jan, 200u),
                        TestData.domain.utbetalingsperiode(3.jan, 3.jan, 300u),
                        TestData.domain.utbetalingsperiode(4.jan, 4.jan, 400u),
                        TestData.domain.utbetalingsperiode(5.jan, 5.jan, 500u),
                    ),
                ),
            )
        )
        val oppdrag = OppdragService.opprett(utbetaling, true)

        assertEquals(5, oppdrag.oppdrag110.oppdragsLinje150s.size)
        val førsteId = oppdrag.oppdrag110.oppdragsLinje150s[0].let { oppdrag ->
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_AAP.klassekode, oppdrag.kodeKlassifik)
            assertEquals(1.jan, oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(1.jan, oppdrag.datoVedtakTom.toLocalDate())
            assertEquals(100, oppdrag.sats.toInt())
            oppdrag.delytelseId
        }

        val andreId = oppdrag.oppdrag110.oppdragsLinje150s.find { it.refDelytelseId == førsteId }!!.let { oppdrag ->
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_AAP.klassekode, oppdrag.kodeKlassifik)
            assertEquals(2.jan, oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(2.jan, oppdrag.datoVedtakTom.toLocalDate())
            assertEquals(200, oppdrag.sats.toInt())
            oppdrag.delytelseId
        }

        val tredjeId = oppdrag.oppdrag110.oppdragsLinje150s.find { it.refDelytelseId == andreId }!!.let { oppdrag ->
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_AAP.klassekode, oppdrag.kodeKlassifik)
            assertEquals(3.jan, oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(3.jan, oppdrag.datoVedtakTom.toLocalDate())
            assertEquals(300, oppdrag.sats.toInt())
            oppdrag.delytelseId
        }

        val fjerdeId = oppdrag.oppdrag110.oppdragsLinje150s.find { it.refDelytelseId == tredjeId }!!.let { oppdrag ->
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_AAP.klassekode, oppdrag.kodeKlassifik)
            assertEquals(4.jan, oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(4.jan, oppdrag.datoVedtakTom.toLocalDate())
            assertEquals(400, oppdrag.sats.toInt())
            oppdrag.delytelseId
        }

        oppdrag.oppdrag110.oppdragsLinje150s.find { it.refDelytelseId == fjerdeId }!!.also { oppdrag ->
            assertEquals(StønadTypeTilleggsstønader.TILSYN_BARN_AAP.klassekode, oppdrag.kodeKlassifik)
            assertEquals(5.jan, oppdrag.datoVedtakFom.toLocalDate())
            assertEquals(5.jan, oppdrag.datoVedtakTom.toLocalDate())
            assertEquals(500, oppdrag.sats.toInt())
        }
    }
}

fun XMLGregorianCalendar.toLocalDate(): LocalDate = LocalDate.of(year, month, day)