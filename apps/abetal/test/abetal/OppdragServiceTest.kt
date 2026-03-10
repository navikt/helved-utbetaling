package abetal

import abetal.utbetaling
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import models.*
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import org.junit.jupiter.api.Test

class OppdragServiceTest {


    /** Endre beløp på en utbetaling
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
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(3.jun, 14.jun, 100u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(3.jun, 14.jun, 200u)
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
        assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(prev.lastPeriodeId.toString(), it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(3.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(14.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(200, it.sats.toLong())
        }
    }

    /** Endre fom på en utbetaling
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
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(3.jun, 14.jun, 100u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(7.jun, 14.jun, 100u)
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
        assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
            assertEquals(3.jun, it.datoStatusFom.toLocalDate())
            assertEquals("ENDR", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(3.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(14.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(100, it.sats.toLong())
        }
        oppdrag.oppdrag110.oppdragsLinje150s[1].let {
            assertEquals(oppdrag.oppdrag110.oppdragsLinje150s[0].delytelseId, it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(7.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(14.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(100, it.sats.toLong())
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
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 7.jun, 100u) 
            periode(8.jun, 14.jun, 200u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 7.jun, 100u)  
            periode(8.jun, 14.jun, 200u)  
            periode(15.jun, 21.jun, 300u)
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
        assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(prev.lastPeriodeId.toString(), it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(300, it.sats.toLong())
        }

    }

    /** Endre tom på en utbetaling
     *
     * 1:    ╭────────────────────────────────╮
     *       │              100,-             │
     *       ╰────────────────────────────────╯
     * 2:    ╭────────────────╮
     *       │      100,-     │
     *       ╰────────────────╯
     * Res:                    ^
     *                         ╰ OPPHØR
     */
    @Test
    fun `forkorte tom`() {
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 14.jun, 100u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 7.jun, 100u)
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
        assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
            assertEquals(8.jun, it.datoStatusFom.toLocalDate())
            assertEquals("ENDR", it.kodeEndringLinje)
            // assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(1.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(14.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(100, it.sats.toLong())
        }
    }

    /** Endre tom på en utbetaling (med fler perioder)
     *
     * 1:    ╭────────────────╮╭──────────────╮
     *       │      100,-     ││     200,-    │
     *       ╰────────────────╯╰──────────────╯
     * 2:    ╭────────────────╮
     *       │      100,-     │
     *       ╰────────────────╯
     * Res:                    ^
     *                         ╰ OPPHØR
     */
    // TODO: blir lastPeriodeId riktig i dette scenarioet?
    @Test
    fun `forkorte tom hvor existing har fler perioder enn ny`() {
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 7.jun, 100u)  
            periode(8.jun, 14.jun, 200u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 7.jun, 100u)
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
        assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
            assertEquals(8.jun, it.datoStatusFom.toLocalDate())
            assertEquals("ENDR", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(8.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(14.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(200, it.sats.toLong())
        }

    }

    /** Endre tom på første periode i en utbetaling
     *
     * 1:    ╭────────────────╮╭──────────────╮
     *       │      100,-     ││     200,-    │
     *       ╰────────────────╯╰──────────────╯
     * 2:    ╭────────╮        ╭──────────────╮
     *       │  100,- │        │     200,-    │
     *       ╰────────╯        ╰──────────────╯
     * Res:            ^
     *                 ╰ OPPHØR
     *                         ╭──────────────╮
     *                         │     200,-    │
     *                         ╰──────────────╯
     */
    @Test
    fun `forkorte tom på første periode`() {
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 14.jun, 100u)  
            periode(15.jun, 28.jun, 200u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 7.jun, 100u)  
            periode(15.jun, 28.jun, 200u)
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)

        assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
            assertEquals(8.jun, it.datoStatusFom.toLocalDate())
            assertEquals("ENDR", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(15.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(28.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(200, it.sats.toLong())
        }
        oppdrag.oppdrag110.oppdragsLinje150s[1].let {
            assertEquals(prev.lastPeriodeId.toString(), it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(15.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(28.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(200, it.sats.toLong())
        }
    }

    /** Endre fom på periode midt i en utbetaling
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
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 14.jun, 100u)  
            periode(15.jun, 28.jun, 200u)  
            periode(29.jun, 7.jul, 100u) 
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 14.jun, 100u)  
            periode(21.jun, 28.jun, 200u)  
            periode(29.jun, 7.jul, 100u) 
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
        assertEquals(3, oppdrag.oppdrag110.oppdragsLinje150s.size)

        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
            assertEquals(15.jun, it.datoStatusFom.toLocalDate())
            assertEquals("ENDR", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(29.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(7.jul, it.datoVedtakTom.toLocalDate())
            assertEquals(100, it.sats.toLong())
        }
        oppdrag.oppdrag110.oppdragsLinje150s[1].let {
            assertEquals(oppdrag.oppdrag110.oppdragsLinje150s[0].delytelseId, it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(21.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(28.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(200, it.sats.toLong())
        }
        oppdrag.oppdrag110.oppdragsLinje150s[2].let {
            assertEquals(oppdrag.oppdrag110.oppdragsLinje150s[1].delytelseId, it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(29.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(7.jul, it.datoVedtakTom.toLocalDate())
            assertEquals(100, it.sats.toLong())
        }
    }

    /** Endre tom på en utbetaling
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
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(3.jun, 14.jun, 100u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(3.jun, 28.jun, 100u)
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)

        assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(prev.lastPeriodeId.toString(), it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(3.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(28.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(100, it.sats.toLong())
        }
    }

    /** Endre beløp i starten av en utbetaling
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
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 28.jun, 100u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 14.jun, 200u)  
            periode(15.jun, 28.jun, 100u)
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)

        assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(prev.lastPeriodeId.toString(), it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(1.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(14.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(200, it.sats.toLong())
        }
        oppdrag.oppdrag110.oppdragsLinje150s[1].let {
            assertEquals(oppdrag.oppdrag110.oppdragsLinje150s[0].delytelseId, it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(15.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(28.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(100, it.sats.toLong())
        }
    }

    /** Endre beløp i slutten av en utbetaling
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
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 28.jun, 100u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 14.jun, 100u)  
            periode(15.jun, 28.jun, 200u)
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
        assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(prev.lastPeriodeId.toString(), it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(15.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(28.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(200, it.sats.toLong())
        }
    }

    /** Endre beløp i midten av en utbetaling
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
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 28.jun, 100u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 9.jun, 100u) 
            periode(10.jun, 19.jun, 200u) 
            periode(20.jun, 28.jun, 100u)
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
        assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(prev.lastPeriodeId.toString(), it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(10.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(19.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(200, it.sats.toLong())
        }
        oppdrag.oppdrag110.oppdragsLinje150s[1].let {
            assertEquals(oppdrag.oppdrag110.oppdragsLinje150s[0].delytelseId, it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(20.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(28.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(100, it.sats.toLong())
        }
    }

    /** Opphold i midten av en utbetaling
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
     *                               │  100,- │
     *                               ╰────────╯
     */
    @Test
    fun `opphold i midten av en utbetaling`() {
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 28.jun, 100u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jun, 14.jun, 100u) 
            periode(21.jun, 28.jun, 100u)
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
        assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
            assertEquals(15.jun, it.datoStatusFom.toLocalDate())
            assertEquals("ENDR", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(1.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(28.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(100, it.sats.toLong())
        }
        oppdrag.oppdrag110.oppdragsLinje150s[1].let {
            assertEquals(oppdrag.oppdrag110.oppdragsLinje150s[0].delytelseId, it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(21.jun, it.datoVedtakFom.toLocalDate())
            assertEquals(28.jun, it.datoVedtakTom.toLocalDate())
            assertEquals(100, it.sats.toLong())
        }
    }

    /** 
     * 1:   ╭──────╮     ╭──────╮
     *      │ 100,-│     │ 200,-│
     *      ╰──────╯     ╰──────╯
     * 1:   ╭──────╮     ╭──────╮      ╭──────╮
     *      │ 100,-│     │ 200,-│      │ 300,-│
     *      ╰──────╯     ╰──────╯      ╰──────╯
     * Res:                            ╭──────╮
     *                                 │ 300,-│
     *                                 ╰──────╯
     */
    @Test
    fun `legg til ny periode med tomrom`() {
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(3.jun, 3.jun, 100u)  
            periode(1.jul, 1.jul, 200u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(3.jun, 3.jun, 100u)  
            periode(1.jul, 1.jul, 200u) 
            periode(1.aug, 1.aug, 300u)
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
        assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(prev.lastPeriodeId.toString(), it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(300, it.sats.toLong())
        }

    }

    /** 
     * 1:   ╭──────╮     ╭──────╮     ╭──────╮
     *      │ 100,-│     │ 200,-│     │ 300,-│
     *      ╰──────╯     ╰──────╯     ╰──────╯
     * 1:   ╭──────╮     ╭──────╮          
     *      │ 100,-│     │ 200,-│          
     *      ╰──────╯     ╰──────╯          
     * Res:                      ^
     *                           ╰ OPPHØR
     *                                   
     */
    @Test
    fun `opphør på perioder som allerede har tomrom`() {
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(3.jun, 3.jun, 100u)  
            periode(1.jul, 1.jul, 200u) 
            periode(1.aug, 1.aug, 300u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(3.jun, 3.jun, 100u)  
            periode(1.jul, 1.jul, 200u)
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
        assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
            assertEquals(2.jul, it.datoStatusFom.toLocalDate())
            assertEquals("ENDR", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(1.aug, it.datoVedtakFom.toLocalDate())
            assertEquals(1.aug, it.datoVedtakTom.toLocalDate())
            assertEquals(300, it.sats.toLong())
        }

    }

    /** 
     * 1:   ╭──────╮     ╭──────╮     ╭──────╮
     *      │ 100,-│     │ 200,-│     │ 300,-│
     *      ╰──────╯     ╰──────╯     ╰──────╯
     * 1:                ╭──────╮     ╭──────╮                  
     *                   │ 200,-│     │ 300,-│                  
     *                   ╰──────╯     ╰──────╯                  
     * Res: ^            ╭──────╮     ╭──────╮
     *      ╰ OPPHØR     │ 200,-│     │ 300,-│
     *                   ╰──────╯     ╰──────╯                 
     */
    @Test
    fun `opphør første periode i oppdrag som allerede har tomrom`() {
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val prev = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(3.jun, 3.jun, 100u)  
            periode(1.jul, 1.jul, 200u) 
            periode(1.aug, 1.aug, 300u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            periodetype = Periodetype.UKEDAG,
        ) {
            periode(1.jul, 1.jul, 200u)  
            periode(1.aug, 1.aug, 300u)
        }

        val oppdrag = OppdragService.update(new, prev) 

        assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
        assertEquals(3, oppdrag.oppdrag110.oppdragsLinje150s.size)
        oppdrag.oppdrag110.oppdragsLinje150s[0].let {
            assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
            assertEquals(3.jun, it.datoStatusFom.toLocalDate())
            assertEquals("ENDR", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(1.aug, it.datoVedtakFom.toLocalDate())
            assertEquals(1.aug, it.datoVedtakTom.toLocalDate())
            assertEquals(300, it.sats.toLong())
        }
        oppdrag.oppdrag110.oppdragsLinje150s[1].let {
            assertEquals(oppdrag.oppdrag110.oppdragsLinje150s[0].delytelseId, it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(1.jul, it.datoVedtakFom.toLocalDate())
            assertEquals(1.jul, it.datoVedtakTom.toLocalDate())
            assertEquals(200, it.sats.toLong())
        }
        oppdrag.oppdrag110.oppdragsLinje150s[2].let {
            assertEquals(oppdrag.oppdrag110.oppdragsLinje150s[1].delytelseId, it.refDelytelseId)
            assertEquals("NY", it.kodeEndringLinje)
            assertEquals(new.behandlingId.id, it.henvisning)
            assertEquals(1.aug, it.datoVedtakFom.toLocalDate())
            assertEquals(1.aug, it.datoVedtakTom.toLocalDate())
            assertEquals(300, it.sats.toLong())
        }

    }

    @Test
    fun `avstemming115 er påkrevd ved create`() {
        val new = utbetaling(
            action = Action.CREATE,
            uid = UtbetalingId(UUID.randomUUID()),
            fagsystem = Fagsystem.AAP,
        ) {
            periode(1.aug, 1.aug, 300u)
        }

        val oppdrag = OppdragService.opprett(new)

        assertEquals("AAP", oppdrag.oppdrag110.avstemming115.kodeKomponent)

        val todayAtTen = LocalDateTime.now().with(LocalTime.of(10, 10, 0, 0))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS"))
        assertEquals(todayAtTen, oppdrag.oppdrag110.avstemming115.nokkelAvstemming)

        val tidspktMelding = LocalDateTime.parse(
            oppdrag.oppdrag110.avstemming115.tidspktMelding,
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
        )
        assertTrue(tidspktMelding.isBefore(LocalDateTime.now()))
        assertTrue(tidspktMelding.isAfter(LocalDateTime.now().minusMinutes(1)))
    }

    @Test
    fun `avstemming115 er påkrevd ved update`() {
        val prev = utbetaling(
            action = Action.CREATE,
            uid = UtbetalingId(UUID.randomUUID()),
            fagsystem = Fagsystem.AAP,
        ) {
            periode(1.jul, 1.jul, 300u)
        }

        val new = utbetaling(
            action = Action.UPDATE,
            sakId = prev.sakId,
            uid = UtbetalingId(UUID.randomUUID()),
            fagsystem = Fagsystem.AAP,
        ) {
            periode(1.jul, 1.jul, 300u) 
            periode(1.aug, 1.aug, 300u)
        }

        val oppdrag = OppdragService.update(new, prev)

        assertEquals("AAP", oppdrag.oppdrag110.avstemming115.kodeKomponent)

        val todayAtTen = LocalDateTime.now().with(LocalTime.of(10, 10, 0, 0)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS"))
        assertEquals(todayAtTen, oppdrag.oppdrag110.avstemming115.nokkelAvstemming)

        val tidspktMelding = LocalDateTime.parse(
            oppdrag.oppdrag110.avstemming115.tidspktMelding,
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
        )
        assertTrue(tidspktMelding.isBefore(LocalDateTime.now()))
        assertTrue(tidspktMelding.isAfter(LocalDateTime.now().minusMinutes(1)))
    }

    @Test
    fun `avstemming115 er påkrevd ved delete`() {
        val prev = utbetaling(
            action = Action.CREATE,
            uid = UtbetalingId(UUID.randomUUID()),
            fagsystem = Fagsystem.AAP,
        ) {
            periode(1.jul, 1.jul, 300u)
        }

        val new = utbetaling(
            action = Action.DELETE,
            sakId = prev.sakId,
            uid = UtbetalingId(UUID.randomUUID()),
            fagsystem = Fagsystem.AAP,
        ) {
            periode(1.jul, 1.jul, 300u) 
            periode(1.aug, 1.aug, 300u)
        }

        val oppdrag = OppdragService.delete(new, prev)

        assertEquals("AAP", oppdrag.oppdrag110.avstemming115.kodeKomponent)
        val todayAtTen = LocalDateTime.now().with(LocalTime.of(10, 10, 0, 0)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS"))
        assertEquals(todayAtTen, oppdrag.oppdrag110.avstemming115.nokkelAvstemming)
    }
}



