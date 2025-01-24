package utsjekk.utbetaling

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UtbetalingsperioderTest {

    /** Scenario 4 Endre beløp på en utbetaling
     *
     * 1:    ╭────────────────────────────────╮
     *       │              100,-             │
     *       ╰────────────────────────────────╯
     * 2:    ╭────────────────────────────────╮
     *       │              200,-             │
     *       ╰────────────────────────────────╯
     * Res:  ╭────────────────────────────────╮
     *       │               NY               │
     *       ╰────────────────────────────────╯
     */
    @Test
    fun `endre beløp på en utbetaling`() {
        val existing =
            Utbetaling.dagpenger(1.jan, listOf(Utbetalingsperiode.dagpenger(1.jan, 10.jan, 100u, Satstype.DAG)))
        val new =
            Utbetaling.dagpenger(2.jan, listOf(Utbetalingsperiode.dagpenger(1.jan, 10.jan, 200u, Satstype.DAG)))

        val perioder = Utbetalingsperioder.utled(existing, new)

        assertEquals(1, perioder.size)

        perioder.first().also {
            assertEquals(200u, it.sats)
            assertEquals(false, it.erEndringPåEksisterendePeriode)
            assertEquals(1.jan, it.fom)
            assertEquals(10.jan, it.tom)
        }
    }

    /** Scenario 5 Endre fom på en utbetaling
     *
     * 1:    ╭────────────────────────────────╮
     *       │              100,-             │
     *       ╰────────────────────────────────╯
     * 2:                    ╭────────────────╮
     *                       │      100,-     │
     *                       ╰────────────────╯
     * Res:  ^
     *       ╰ OPPHØR
     *                       ╭────────────────╮
     *                       │       NY       │
     *                       ╰────────────────╯
     */
    @Test
    fun `forkorte fom`() {
        val existing =
            Utbetaling.dagpenger(1.jan, listOf(Utbetalingsperiode.dagpenger(1.jan, 10.jan, 100u, Satstype.DAG)))
        val new =
            Utbetaling.dagpenger(2.jan, listOf(Utbetalingsperiode.dagpenger(5.jan, 10.jan, 100u, Satstype.DAG)))

        val perioder = Utbetalingsperioder.utled(existing, new)

        assertEquals(2, perioder.size)

        perioder.first().also {
            assertEquals(1.jan, it.opphør?.fom)
        }

        perioder.last().also {
            assertEquals(100u, it.sats)
            assertEquals(false, it.erEndringPåEksisterendePeriode)
            assertEquals(5.jan, it.fom)
            assertEquals(10.jan, it.tom)
        }
    }

    /** 
     * 1:   ╭────────────────╮╭──────────────╮
     *      │      100,-     ││     200,-    │
     *      ╰────────────────╯╰──────────────╯
     * 2:   ╭────────────────╮╭──────────────╮╭──────────────╮                                                                                                    
     *      │      100,-     ││     200,-    ││     300,-    │                                                                                                    
     *      ╰────────────────╯╰──────────────╯╰──────────────╯                                                                                                    
     * Res:                                   ╭──────────────╮
     *                                        │     300,-    │
     *                                        ╰──────────────╯
     */
    @Test
    fun `legge til en ekstra periode`() {
        val existing = Utbetaling.dagpenger(
            1.jan, 
            listOf(
                Utbetalingsperiode.dagpenger(1.jan, 5.jan, 100u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(6.jan, 10.jan, 200u, Satstype.DAG),
            )
        )
        val new = Utbetaling.dagpenger(
            2.jan, 
            listOf(
                Utbetalingsperiode.dagpenger(1.jan, 5.jan, 100u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(6.jan, 10.jan, 200u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(11.jan, 15.jan, 300u, Satstype.DAG),
            )
        )
        val perioder = Utbetalingsperioder.utled(existing, new)

        assertEquals(1, perioder.size)

        perioder.last().also {
            assertEquals(300u, it.sats)
            assertEquals(false, it.erEndringPåEksisterendePeriode)
            assertEquals(11.jan, it.fom)
            assertEquals(15.jan, it.tom)
        }
    }

    /** Scenario 6 Endre tom på en utbetaling
     *
     * 1:    ╭────────────────────────────────╮
     *       │              100,-             │
     *       ╰────────────────────────────────╯
     * 2:    ╭────────────────╮
     *       │      100,-     │
     *       ╰────────────────╯
     * Res:                   ^
     *                        ╰ OPPHØR
     */
    @Test
    fun `forkorte tom`() {
        val existing = Utbetaling.dagpenger(1.jan, listOf(Utbetalingsperiode.dagpenger(1.jan, 10.jan, 100u, Satstype.DAG)))
        val new = Utbetaling.dagpenger(2.jan, listOf(Utbetalingsperiode.dagpenger(1.jan, 5.jan, 100u, Satstype.DAG)))

        val perioder = Utbetalingsperioder.utled(existing, new)

        assertEquals(1, perioder.size)

        perioder.first().also {
            assertEquals(6.jan, it.opphør?.fom)
        }
    }

    /** Scenario 6 Endre tom på en utbetaling (med fler perioder)
     *
     * 1:    ╭────────────────╮╭──────────────╮
     *       │      100,-     ││     200,-    │
     *       ╰────────────────╯╰──────────────╯
     * 2:    ╭────────────────╮
     *       │      100,-     │
     *       ╰────────────────╯
     * Res:                   ^
     *                        ╰ OPPHØR
     */
    @Test
    fun `forkorte tom hvor existing har fler perioder enn ny`() {
        val existing = Utbetaling.dagpenger(
            1.jan, 
            listOf(
                Utbetalingsperiode.dagpenger(1.jan, 5.jan, 100u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(6.jan, 10.jan, 200u, Satstype.DAG),
            )
        )
        val new = Utbetaling.dagpenger(2.jan, listOf(Utbetalingsperiode.dagpenger(1.jan, 5.jan, 100u, Satstype.DAG)))
        val perioder = Utbetalingsperioder.utled(existing, new)

        assertEquals(1, perioder.size)

        perioder.first().also {
            assertEquals(6.jan, it.opphør?.fom)
        }
    }

    /** Scenario 6-1 Endre tom på første periode i en utbetaling
     *
     * 1:    ╭────────────────╮╭──────────────╮
     *       │      100,-     ││     200,-    │
     *       ╰────────────────╯╰──────────────╯
     * 2:    ╭────────╮        ╭──────────────╮
     *       │  100,- │        │     200,-    │
     *       ╰────────╯        ╰──────────────╯
     * Res:           ^
     *                ╰ OPPHØR
     *                         ╭──────────────╮
     *                         │     200,-    │
     *                         ╰──────────────╯
     */
    @Test
    fun `forkorte tom på første periode`() {
        val existing =
            Utbetaling.dagpenger(1.jan, listOf(
                Utbetalingsperiode.dagpenger(1.jan, 10.jan, 100u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(11.jan, 20.jan, 200u, Satstype.DAG),
            ))
        val new =
            Utbetaling.dagpenger(2.jan, listOf(
                Utbetalingsperiode.dagpenger(1.jan, 5.jan, 100u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(11.jan, 20.jan, 200u, Satstype.DAG),
            ))

        val perioder = Utbetalingsperioder.utled(existing, new)

        assertEquals(2, perioder.size)

        perioder.first().also {
            assertEquals(6.jan, it.opphør?.fom)
        }

        perioder.last().also {
            assertEquals(200u, it.sats)
            assertEquals(false, it.erEndringPåEksisterendePeriode)
            assertEquals(11.jan, it.fom)
            assertEquals(20.jan, it.tom)
        }
    }


    /** Scenario 6-2 Endre fom på periode midt i en utbetaling
     *
     * 1:    ╭────────╮╭────────────────╮╭──────────────╮
     *       │  100,- ││      200,-     ││     100,-    │
     *       ╰────────╯╰────────────────╯╰──────────────╯
     * 2:    ╭────────╮        ╭────────╮╭──────────────╮
     *       │  100,- │        │  200,- ││     100,-    │
     *       ╰────────╯        ╰────────╯╰──────────────╯
     * Res:           ^
     *                ╰ OPPHØR
     *                         ╭────────╮╭──────────────╮
     *                         │  200,- ││     100,-    │
     *                         ╰────────╯╰──────────────╯
     */
    @Test
    fun `forkorte fom på periode midt i utbetalingen`() {
        val existing =
            Utbetaling.dagpenger(1.jan, listOf(
                Utbetalingsperiode.dagpenger(1.jan, 5.jan, 100u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(6.jan, 15.jan, 200u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(16.jan, 25.jan, 100u, Satstype.DAG),
            ))
        val new =
            Utbetaling.dagpenger(2.jan, listOf(
                Utbetalingsperiode.dagpenger(1.jan, 5.jan, 100u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(10.jan, 15.jan, 200u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(16.jan, 25.jan, 100u, Satstype.DAG),
            ))

        val perioder = Utbetalingsperioder.utled(existing, new)

        assertEquals(3, perioder.size)

        perioder.first().also {
            assertEquals(6.jan, it.opphør?.fom)
        }

        perioder[1].also {
            assertEquals(200u, it.sats)
            assertEquals(false, it.erEndringPåEksisterendePeriode)
            assertEquals(10.jan, it.fom)
            assertEquals(15.jan, it.tom)
        }

        perioder.last().also {
            assertEquals(100u, it.sats)
            assertEquals(false, it.erEndringPåEksisterendePeriode)
            assertEquals(16.jan, it.fom)
            assertEquals(25.jan, it.tom)
        }
    }

    /** Scenario 6-3 Endre tom på en utbetaling
     *
     * 1:    ╭────────────────────────────────╮
     *       │              100,-             │
     *       ╰────────────────────────────────╯
     * 2:    ╭────────────────────────────────────────────────╮
     *       │                      100,-                     │
     *       ╰────────────────────────────────────────────────╯
     * Res:  ╭────────────────────────────────────────────────╮
     *       │                      100,-                     │
     *       ╰────────────────────────────────────────────────╯
     */
    @Test
    fun `forlenge tom`() {
        val existing =
            Utbetaling.dagpenger(1.jan, listOf(Utbetalingsperiode.dagpenger(1.jan, 10.jan, 100u, Satstype.ENGANGS)))
        val new =
            Utbetaling.dagpenger(2.jan, listOf(Utbetalingsperiode.dagpenger(1.jan, 15.jan, 100u, Satstype.ENGANGS)))

        val perioder = Utbetalingsperioder.utled(existing, new)

        assertEquals(1, perioder.size)

        perioder.first().also {
            assertEquals(1.jan, it.fom)
            assertEquals(15.jan, it.tom)
            assertEquals(100u, it.sats)
        }
    }

    /** Scenario 7-1 Endre beløp i starten av en utbetaling
     *
     * 1:    ╭────────────────────────────────╮
     *       │              100,-             │
     *       ╰────────────────────────────────╯
     * 2:    ╭────────────────╮╭──────────────╮
     *       │      200,-     ││     100,-    │
     *       ╰────────────────╯╰──────────────╯
     * Res:  ╭────────────────╮╭──────────────╮
     *       │        NY      ││      NY      │
     *       ╰────────────────╯╰──────────────╯
     */
    @Test
    fun `endre beløp i starten av en utbetaling`() {
        val existing =
            Utbetaling.dagpenger(1.jan, listOf(
                Utbetalingsperiode.dagpenger(1.jan, 10.jan, 100u, Satstype.DAG),
            ))
        val new =
            Utbetaling.dagpenger(2.jan, listOf(
                Utbetalingsperiode.dagpenger(1.jan, 5.jan, 200u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(6.jan, 10.jan, 100u, Satstype.DAG),
            ))

        val perioder = Utbetalingsperioder.utled(existing, new)

        assertEquals(2, perioder.size)

        perioder.first().also {
            assertEquals(200u, it.sats)
            assertEquals(false, it.erEndringPåEksisterendePeriode)
            assertEquals(1.jan, it.fom)
            assertEquals(5.jan, it.tom)
        }

        perioder.last().also {
            assertEquals(100u, it.sats)
            assertEquals(false, it.erEndringPåEksisterendePeriode)
            assertEquals(6.jan, it.fom)
            assertEquals(10.jan, it.tom)
        }
    }

    /** Scenario 7-2 Endre beløp i slutten av en utbetaling
     *
     * 1:    ╭────────────────────────────────╮
     *       │              100,-             │
     *       ╰────────────────────────────────╯
     * 2:    ╭────────────────╮╭──────────────╮
     *       │      100,-     ││     200,-    │
     *       ╰────────────────╯╰──────────────╯
     * Res:                    ╭──────────────╮
     *                         │      NY      │
     *                         ╰──────────────╯
     */
    @Test
    fun `endre beløp i slutten av en utbetaling`() {
        val existing =
            Utbetaling.dagpenger(1.jan, listOf(
                Utbetalingsperiode.dagpenger(1.jan, 10.jan, 100u, Satstype.DAG),
            ))
        val new =
            Utbetaling.dagpenger(2.jan, listOf(
                Utbetalingsperiode.dagpenger(1.jan, 5.jan, 100u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(6.jan, 10.jan, 200u, Satstype.DAG),
            ))

        val perioder = Utbetalingsperioder.utled(existing, new)

        assertEquals(1, perioder.size)

        perioder.first().also {
            assertEquals(200u, it.sats)
            assertEquals(false, it.erEndringPåEksisterendePeriode)
            assertEquals(6.jan, it.fom)
            assertEquals(10.jan, it.tom)
        }
    }

    /** Scenario 7-3 Endre beløp i midten av en utbetaling
     *
     * 1:    ╭────────────────────────────────╮
     *       │              100,-             │
     *       ╰────────────────────────────────╯
     * 2:    ╭──────────╮╭──────────╮╭────────╮
     *       │   100,-  ││   200,-  ││  100,- │
     *       ╰──────────╯╰──────────╯╰────────╯
     * Res:              ╭──────────╮╭────────╮
     *                   │    NY    ││   NY   │
     *                   ╰──────────╯╰────────╯
     */
    @Test
    fun `endre beløp i midten av en utbetaling`() {
        val existing =
            Utbetaling.dagpenger(1.jan, listOf(
                Utbetalingsperiode.dagpenger(1.jan, 10.jan, 100u, Satstype.DAG),
            ))
        val new =
            Utbetaling.dagpenger(2.jan, listOf(
                Utbetalingsperiode.dagpenger(1.jan, 3.jan, 100u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(4.jan, 6.jan, 200u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(7.jan, 10.jan, 100u, Satstype.DAG),
            ))

        val perioder = Utbetalingsperioder.utled(existing, new)

        assertEquals(2, perioder.size)

        perioder.first().also {
            assertEquals(200u, it.sats)
            assertEquals(false, it.erEndringPåEksisterendePeriode)
            assertEquals(4.jan, it.fom)
            assertEquals(6.jan, it.tom)
        }

        perioder.last().also {
            assertEquals(100u, it.sats)
            assertEquals(false, it.erEndringPåEksisterendePeriode)
            assertEquals(7.jan, it.fom)
            assertEquals(10.jan, it.tom)
        }
    }

    /** Scenario 7-3 Opphold i midten av en utbetaling
     *
     * 1:    ╭────────────────────────────────╮
     *       │              100,-             │
     *       ╰────────────────────────────────╯
     * 2:    ╭──────────╮            ╭────────╮
     *       │   100,-  │            │  100,- │
     *       ╰──────────╯            ╰────────╯
     * Res:             ^
     *                  ╰ OPPHØR
     *                               ╭────────╮
     *                               │   NY   │
     *                               ╰────────╯
     */
    @Test
    fun `opphold i midten av en utbetaling`() {
        val existing =
            Utbetaling.dagpenger(1.jan, listOf(
                Utbetalingsperiode.dagpenger(1.jan, 10.jan, 100u, Satstype.DAG),
            ))
        val new =
            Utbetaling.dagpenger(2.jan, listOf(
                Utbetalingsperiode.dagpenger(1.jan, 3.jan, 100u, Satstype.DAG),
                Utbetalingsperiode.dagpenger(7.jan, 10.jan, 100u, Satstype.DAG),
            ))

        val perioder = Utbetalingsperioder.utled(existing, new)

        assertEquals(2, perioder.size)

        perioder.first().also {
            assertEquals(4.jan, it.opphør?.fom)
        }

        perioder.last().also {
            assertEquals(100u, it.sats)
            assertEquals(false, it.erEndringPåEksisterendePeriode)
            assertEquals(7.jan, it.fom)
            assertEquals(10.jan, it.tom)
        }
    }
}
