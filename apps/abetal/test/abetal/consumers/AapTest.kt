package abetal.consumers

import abetal.*
import java.time.LocalDate
import kotlin.test.assertEquals
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.*
import org.junit.jupiter.api.Test

// TODO: assert hele oppdrag XMLene for å verifisere at alle felter blir satt som forventet.
internal class AapTest {

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
        val uid = randomUtbetalingId()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        TestTopics.utbetalinger.produce("${uid.id}") {
            utbetaling(Action.CREATE, uid, sid, bid) {
                listOf(
                    periode(1.jan, 2.jan, 100u)
                )
            }
        }
        // TestTopics.saker.produce("${Fagsystem.AAP}-${sid.id}") {
        //     SakIdWrapper("${sid.id}", setOf(uid))
        // }
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.UPDATE, sid) {
                listOf(
                    Aap.dag(1.jan, 200u),
                    Aap.dag(2.jan, 200u)
                )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasValue(StatusReply(Status.MOTTATT))
        TestTopics.utbetalinger.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasLastValue("${uid.id}") {
                utbetaling(Action.UPDATE, uid, sid, bid) {
                    listOf(
                        periode(1.jan, 2.jan, 200u)
                    )
                }
            }
        TestTopics.oppdrag.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .withLastValue {
                assertEquals("ENDR", it!!.oppdrag110.kodeEndring)
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
        val uid = randomUtbetalingId()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        TestTopics.utbetalinger.produce("${uid.id}") {
            utbetaling(Action.CREATE, uid, sid, bid) {
                listOf(
                    periode(1.jan, 3.jan, 100u),
                )
            }
        }
        // TestTopics.saker.produce("${Fagsystem.AAP}-${sid.id}") {
        //     SakIdWrapper(sid.id, setOf(uid))
        // }
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.UPDATE, sid) {
                listOf(
                    Aap.dag(2.jan, 100u),
                    Aap.dag(3.jan, 100u)
                )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasValue(StatusReply(Status.MOTTATT))
        TestTopics.utbetalinger.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasLastValue("${uid.id}") {
                utbetaling(Action.UPDATE, uid, sid, bid) {
                    listOf(
                        periode(2.jan, 3.jan, 100u),
                    )
                }
            }
        TestTopics.oppdrag.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .withLastValue { o: Oppdrag? ->
                assertEquals("ENDR", o!!.oppdrag110.kodeEndring)
                assertEquals(TkodeStatusLinje.OPPH, o.oppdrag110.oppdragsLinje150s.first().kodeStatusLinje)
                assertEquals(1.jan, o.oppdrag110.oppdragsLinje150s.first().datoStatusFom.toLocalDate())
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
         val uid = randomUtbetalingId()
         val sid = SakId("$nextInt")
         TestTopics.utbetalinger.produce("${uid.id}") {
             utbetaling(Action.CREATE, uid, sid) {
                 listOf(
                     periode(1.jan, 3.jan, 100u),
                 )
             }
         }
         TestTopics.utbetalinger.produce("${uid.id}") {
             utbetaling(Action.UPDATE, uid, sid) {
                 listOf(
                     periode(1.jan, 2.jan, 100u),
                     periode(3.jan, 3.jan, 200u),
                 )
             }
         }
         // TestTopics.saker.produce("${Fagsystem.AAP}-${sid.id}") {
         //     SakIdWrapper(sid.id, setOf(uid))
         // }
         val bid = BehandlingId("$nextInt")
         TestTopics.aap.produce("${uid.id}") {
             Aap.utbetaling(Action.UPDATE, sid, bid) {
                 listOf(
                     Aap.dag(1.jan, 100u),
                     Aap.dag(2.jan, 100u),
                     Aap.dag(3.jan, 200u),
                     Aap.dag(6.jan, 300u),
                 )
             }
         }
         TestTopics.status.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasValue(StatusReply(Status.MOTTATT))
         TestTopics.utbetalinger.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasLastValue("${uid.id}") {
                 utbetaling(Action.UPDATE, uid, sid, bid) {
                     listOf(
                         periode(1.jan, 2.jan, 100u),
                         periode(3.jan, 3.jan, 200u),
                         periode(6.jan, 6.jan, 300u),
                     )
                 }
             }
         TestTopics.oppdrag.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .withLastValue { o: Oppdrag? ->
                 assertEquals("ENDR", o!!.oppdrag110.kodeEndring)
                 assertEquals(6.jan, o.oppdrag110.oppdragsLinje150s.last().datoVedtakFom.toLocalDate())
                 assertEquals(300u, o.oppdrag110.oppdragsLinje150s.last().sats.toDouble().toUInt())
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
         val uid = randomUtbetalingId()
         val sid = SakId("$nextInt")
         val bid = BehandlingId("$nextInt")
         TestTopics.utbetalinger.produce("${uid.id}") {
             utbetaling(Action.CREATE, uid, sid, bid) {
                 listOf(
                     periode(1.jan, 3.jan, 100u),
                 )
             }
         }
         // TestTopics.saker.produce("${Fagsystem.AAP}-${sid.id}") {
         //     SakIdWrapper(sid.id, setOf(uid))
         // }
         TestTopics.aap.produce("${uid.id}") {
             Aap.utbetaling(Action.UPDATE, sid) {
                 listOf(
                     Aap.dag(1.jan, 100u),
                     Aap.dag(2.jan, 100u),
                 )
             }
         }
         TestTopics.status.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasValue(StatusReply(Status.MOTTATT))
         TestTopics.utbetalinger.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasLastValue("${uid.id}") {
                 utbetaling(Action.UPDATE, uid, sid, bid) {
                     listOf(
                         periode(1.jan, 2.jan, 100u),
                     )
                 }
             }
         TestTopics.oppdrag.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .withLastValue { o: Oppdrag? ->
                 assertEquals("ENDR", o!!.oppdrag110.kodeEndring)
                 assertEquals(TkodeStatusLinje.OPPH, o.oppdrag110.oppdragsLinje150s.first().kodeStatusLinje)
                 assertEquals(3.jan, o.oppdrag110.oppdragsLinje150s.first().datoStatusFom.toLocalDate())
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
         val uid = randomUtbetalingId()
         val sid = SakId("$nextInt")
         val bid = BehandlingId("$nextInt")
         TestTopics.utbetalinger.produce("${uid.id}") {
             utbetaling(Action.CREATE, uid, sid) {
                 listOf(
                     periode(1.jan, 10.jan, 100u),
                 )
             }
         }
         TestTopics.utbetalinger.produce("${uid.id}") {
             utbetaling(Action.UPDATE, uid, sid) {
                 listOf(
                     periode(1.jan, 3.jan, 100u), // 4-5 er helgedag og har blitt filtrert ut
                     periode(6.jan, 10.jan, 200u),
                 )
             }
         }
         // TestTopics.saker.produce("${Fagsystem.AAP}-${sid.id}") {
         //     SakIdWrapper(sid.id, setOf(uid))
         // }
         TestTopics.aap.produce("${uid.id}") {
             Aap.utbetaling(Action.UPDATE, sid, bid) {
                 listOf(
                     Aap.dag(1.jan, 100u),
                     Aap.dag(2.jan, 100u),
                     Aap.dag(3.jan, 100u),
                     // 4 og 5 er helg
                 )
             }
         }
         TestTopics.status.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasValue(StatusReply(Status.MOTTATT))
         TestTopics.utbetalinger.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasLastValue("${uid.id}") {
                 utbetaling(Action.UPDATE, uid, sid, bid) {
                     listOf(
                         periode(1.jan, 3.jan, 100u),
                     )
                 }
             }
         TestTopics.oppdrag.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .withLastValue { o: Oppdrag? ->
                 assertEquals("ENDR", o!!.oppdrag110.kodeEndring)
                 assertEquals(TkodeStatusLinje.OPPH, o.oppdrag110.oppdragsLinje150s.first().kodeStatusLinje)
                 assertEquals(4.jan, o.oppdrag110.oppdragsLinje150s.first().datoStatusFom.toLocalDate())
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
         val uid = randomUtbetalingId()
         val sid = SakId("$nextInt")
         val bid = BehandlingId("$nextInt")
         TestTopics.utbetalinger.produce("${uid.id}") {
             utbetaling(Action.CREATE, uid, sid) {
                 listOf(
                     periode(1.jan, 20.jan, 100u),
                 )
             }
         }
         TestTopics.utbetalinger.produce("${uid.id}") {
             utbetaling(Action.UPDATE, uid, sid) {
                 listOf(
                     periode(1.jan, 10.jan, 100u),
                     periode(13.jan, 20.jan, 200u), // 11 og 12 er helg
                 )
             }
         }
         // TestTopics.saker.produce("${Fagsystem.AAP}-${sid.id}") {
         //     SakIdWrapper(sid.id, setOf(uid))
         // }
         TestTopics.aap.produce("${uid.id}") {
             Aap.utbetaling(Action.UPDATE, sid, bid) {
                 listOf(
                     Aap.dag(1.jan, 100u),
                     Aap.dag(2.jan, 100u),
                     Aap.dag(3.jan, 100u),
                     // 4 og 5 er helg
                     Aap.dag(13.jan, 200u),
                     Aap.dag(14.jan, 200u),
                     Aap.dag(15.jan, 200u),
                     Aap.dag(16.jan, 200u),
                     Aap.dag(17.jan, 200u),
                     // 18 og 19 er helg
                     Aap.dag(20.jan, 200u),
                 )
             }
         }
         TestTopics.status.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasValue(StatusReply(Status.MOTTATT))
         TestTopics.utbetalinger.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasLastValue("${uid.id}") {
                 utbetaling(Action.UPDATE, uid, sid, bid) {
                     listOf(
                         periode(1.jan, 3.jan, 100u),
                         periode(13.jan, 20.jan, 200u),
                     )
                 }
             }
         TestTopics.oppdrag.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .withLastValue { o: Oppdrag? ->
                 assertEquals("ENDR", o!!.oppdrag110.kodeEndring)
                 assertEquals(2, o.oppdrag110.oppdragsLinje150s.size)
                 assertEquals(TkodeStatusLinje.OPPH, o.oppdrag110.oppdragsLinje150s.first().kodeStatusLinje)
                 assertEquals(4.jan, o.oppdrag110.oppdragsLinje150s.first().datoStatusFom.toLocalDate())
                 assertEquals(13.jan, o.oppdrag110.oppdragsLinje150s.last().datoVedtakFom.toLocalDate())
                 assertEquals(200u, o.oppdrag110.oppdragsLinje150s.last().sats.toDouble().toUInt())
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
         val uid = randomUtbetalingId()
         val sid = SakId("$nextInt")
         val bid = BehandlingId("$nextInt")
         TestTopics.utbetalinger.produce("${uid.id}") {
             utbetaling(Action.CREATE, uid, sid) {
                 listOf(
                     periode(1.jan, 20.jan, 100u),
                 )
             }
         }
         TestTopics.utbetalinger.produce("${uid.id}") {
             utbetaling(Action.UPDATE, uid, sid) {
                 listOf(
                     periode(1.jan, 3.jan, 100u),
                     periode(6.jan, 14.jan, 200u), // 11 og 12 er helg
                     periode(15.jan, 20.jan, 100u),
                 )
             }
         }
         // TestTopics.saker.produce("${Fagsystem.AAP}-${sid.id}") {
         //     SakIdWrapper(sid.id, setOf(uid))
         // }
         TestTopics.aap.produce("${uid.id}") {
             Aap.utbetaling(Action.UPDATE, sid, bid) {
                 listOf(
                     Aap.dag(1.jan, 100u),
                     Aap.dag(2.jan, 100u),
                     Aap.dag(3.jan, 100u),
                     // 4 og 5 er helg
                     Aap.dag(10.jan, 200u),
                     // 11 og 12 er helg
                     Aap.dag(13.jan, 200u),
                     Aap.dag(14.jan, 200u),
                     Aap.dag(15.jan, 100u),
                     Aap.dag(16.jan, 100u),
                     Aap.dag(17.jan, 100u),
                     // 18 og 19 er helg
                     Aap.dag(20.jan, 100u),
                 )
             }
         }
         TestTopics.status.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasValue(StatusReply(Status.MOTTATT))
         TestTopics.utbetalinger.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasLastValue("${uid.id}") {
                 utbetaling(Action.UPDATE, uid, sid, bid) {
                     listOf(
                         periode(1.jan, 3.jan, 100u),
                         periode(10.jan, 14.jan, 200u),
                         periode(15.jan, 20.jan, 100u),
                     )
                 }
             }
         TestTopics.oppdrag.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .withLastValue { o: Oppdrag? ->
                 assertEquals("ENDR", o!!.oppdrag110.kodeEndring)
                 assertEquals(3, o.oppdrag110.oppdragsLinje150s.size)

                 assertEquals(TkodeStatusLinje.OPPH, o.oppdrag110.oppdragsLinje150s.first().kodeStatusLinje)
                 assertEquals(4.jan, o.oppdrag110.oppdragsLinje150s.first().datoStatusFom.toLocalDate())

                 assertEquals(10.jan, o.oppdrag110.oppdragsLinje150s.get(1).datoVedtakFom.toLocalDate())
                 assertEquals(14.jan, o.oppdrag110.oppdragsLinje150s.get(1).datoVedtakTom.toLocalDate())
                 assertEquals(200u, o.oppdrag110.oppdragsLinje150s.get(1).sats.toDouble().toUInt())

                 assertEquals(15.jan, o.oppdrag110.oppdragsLinje150s.get(2).datoVedtakFom.toLocalDate())
                 assertEquals(20.jan, o.oppdrag110.oppdragsLinje150s.get(2).datoVedtakTom.toLocalDate())
                 assertEquals(100u, o.oppdrag110.oppdragsLinje150s.get(2).sats.toDouble().toUInt())
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
         val uid = randomUtbetalingId()
         val sid = SakId("$nextInt")
         val bid = BehandlingId("$nextInt")
         TestTopics.utbetalinger.produce("${uid.id}") {
             utbetaling(Action.CREATE, uid, sid, bid) {
                 listOf(
                     periode(1.jan, 3.jan, 100u)
                 )
             }
         }
         // TestTopics.saker.produce("${Fagsystem.AAP}-${sid.id}") {
         //     SakIdWrapper(sid.id, setOf(uid))
         // }
         TestTopics.aap.produce("${uid.id}") {
             Aap.utbetaling(Action.UPDATE, sid) {
                 listOf(
                     Aap.dag(1.jan, 100u),
                     Aap.dag(2.jan, 100u),
                     Aap.dag(3.jan, 100u),
                     Aap.dag(6.jan, 100u),
                 )
             }
         }
         TestTopics.status.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasValue(StatusReply(Status.MOTTATT))
         TestTopics.utbetalinger.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasLastValue("${uid.id}") {
                 utbetaling(Action.UPDATE, uid, sid, bid) {
                     listOf(
                         periode(1.jan, 6.jan, 100u)
                     )
                 }
             }
         TestTopics.oppdrag.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .withLastValue { o: Oppdrag? ->
                 assertEquals("ENDR", o!!.oppdrag110.kodeEndring)
                 assertEquals(1, o.oppdrag110.oppdragsLinje150s.size)
                 assertEquals(1.jan, o.oppdrag110.oppdragsLinje150s[0].datoVedtakFom.toLocalDate())
                 assertEquals(6.jan, o.oppdrag110.oppdragsLinje150s[0].datoVedtakTom.toLocalDate())
                 assertEquals(100u, o.oppdrag110.oppdragsLinje150s[0].sats.toDouble().toUInt())
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
         val uid = randomUtbetalingId()
         val sid = SakId("$nextInt")
         val bid = BehandlingId("$nextInt")
         TestTopics.utbetalinger.produce("${uid.id}") {
             utbetaling(Action.CREATE, uid, sid) {
                 listOf(
                     periode(1.jan, 8.jan, 100u)
                 )
             }
         }
         // TestTopics.saker.produce("${Fagsystem.AAP}-${sid.id}") {
         //     SakIdWrapper(sid.id, setOf(uid))
         // }
         TestTopics.aap.produce("${uid.id}") {
             Aap.utbetaling(Action.UPDATE, sid, bid) {
                 listOf(
                     Aap.dag(1.jan, 200u),
                     Aap.dag(2.jan, 200u),
                     Aap.dag(3.jan, 200u),
                     Aap.dag(6.jan, 100u),
                     Aap.dag(7.jan, 100u),
                     Aap.dag(8.jan, 100u),
                 )
             }
         }
         TestTopics.status.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasValue(StatusReply(Status.MOTTATT))
         TestTopics.utbetalinger.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasLastValue("${uid.id}") {
                 utbetaling(Action.UPDATE, uid, sid, bid) {
                     listOf(
                         periode(1.jan, 3.jan, 200u),
                         periode(6.jan, 8.jan, 100u),
                     )
                 }
             }
         TestTopics.oppdrag.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .withLastValue { o: Oppdrag? ->
                 assertEquals("ENDR", o!!.oppdrag110.kodeEndring)
                 assertEquals(2, o.oppdrag110.oppdragsLinje150s.size)
                 assertEquals(1.jan, o.oppdrag110.oppdragsLinje150s[0].datoVedtakFom.toLocalDate())
                 assertEquals(3.jan, o.oppdrag110.oppdragsLinje150s[0].datoVedtakTom.toLocalDate())
                 assertEquals(200u, o.oppdrag110.oppdragsLinje150s[0].sats.toDouble().toUInt())
                 assertEquals(6.jan, o.oppdrag110.oppdragsLinje150s[1].datoVedtakFom.toLocalDate())
                 assertEquals(8.jan, o.oppdrag110.oppdragsLinje150s[1].datoVedtakTom.toLocalDate())
                 assertEquals(100u, o.oppdrag110.oppdragsLinje150s[1].sats.toDouble().toUInt())
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
         val uid = randomUtbetalingId()
         val sid = SakId("$nextInt")
         val bid = BehandlingId("$nextInt")
         TestTopics.utbetalinger.produce("${uid.id}") {
             utbetaling(Action.CREATE, uid, sid, bid) {
                 listOf(
                     periode(1.jan, 10.jan, 100u)
                 )
             }
         }
         // TestTopics.saker.produce("${Fagsystem.AAP}-${sid.id}") {
         //     SakIdWrapper(sid.id, setOf(uid))
         // }
         TestTopics.aap.produce("${uid.id}") {
             Aap.utbetaling(Action.UPDATE, sid) {
                 listOf(
                     Aap.dag(1.jan, 100u),
                     Aap.dag(2.jan, 100u),
                     Aap.dag(3.jan, 100u),
                     Aap.dag(6.jan, 100u),
                     Aap.dag(7.jan, 200u),
                     Aap.dag(8.jan, 200u),
                     Aap.dag(9.jan, 200u),
                     Aap.dag(10.jan, 200u),
                 )
             }
         }
         TestTopics.status.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasValue(StatusReply(Status.MOTTATT))
         TestTopics.utbetalinger.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasLastValue("${uid.id}") {
                 utbetaling(Action.UPDATE, uid, sid, bid) {
                     listOf(
                         periode(1.jan, 6.jan, 100u),
                         periode(7.jan, 10.jan, 200u),
                     )
                 }
             }
         TestTopics.oppdrag.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .withLastValue { o: Oppdrag? ->
                 assertEquals("ENDR", o!!.oppdrag110.kodeEndring)
                 assertEquals(1, o.oppdrag110.oppdragsLinje150s.size)
                 assertEquals(7.jan, o.oppdrag110.oppdragsLinje150s[0].datoVedtakFom.toLocalDate())
                 assertEquals(10.jan, o.oppdrag110.oppdragsLinje150s[0].datoVedtakTom.toLocalDate())
                 assertEquals(200u, o.oppdrag110.oppdragsLinje150s[0].sats.toDouble().toUInt())
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
         val uid = randomUtbetalingId()
         val sid = SakId("$nextInt")
         val bid = BehandlingId("$nextInt")
         TestTopics.utbetalinger.produce("${uid.id}") {
             utbetaling(Action.CREATE, uid, sid, bid) {
                 listOf(
                     periode(2.jan, 9.jan, 100u)
                 )
             }
         }
         // TestTopics.saker.produce("${Fagsystem.AAP}-${sid.id}") {
         //     SakIdWrapper(sid.id, setOf(uid))
         // }
         TestTopics.aap.produce("${uid.id}") {
             Aap.utbetaling(Action.UPDATE, sid) {
                 listOf(
                     Aap.dag(2.jan, 100u),
                     Aap.dag(3.jan, 100u),
                     Aap.dag(6.jan, 200u),
                     Aap.dag(7.jan, 200u),
                     Aap.dag(8.jan, 100u),
                     Aap.dag(9.jan, 100u),
                 )
             }
         }
         TestTopics.status.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasValue(StatusReply(Status.MOTTATT))
         TestTopics.utbetalinger.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasLastValue("${uid.id}") {
                 utbetaling(Action.UPDATE, uid, sid, bid) {
                     listOf(
                         periode(2.jan, 3.jan, 100u),
                         periode(6.jan, 7.jan, 200u),
                         periode(8.jan, 9.jan, 100u),
                     )
                 }
             }
         TestTopics.oppdrag.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .withLastValue { o: Oppdrag? ->
                 assertEquals("ENDR", o!!.oppdrag110.kodeEndring)
                 assertEquals(2, o.oppdrag110.oppdragsLinje150s.size)
                 assertEquals(6.jan, o.oppdrag110.oppdragsLinje150s[0].datoVedtakFom.toLocalDate())
                 assertEquals(7.jan, o.oppdrag110.oppdragsLinje150s[0].datoVedtakTom.toLocalDate())
                 assertEquals(200u, o.oppdrag110.oppdragsLinje150s[0].sats.toDouble().toUInt())
                 assertEquals(8.jan, o.oppdrag110.oppdragsLinje150s[1].datoVedtakFom.toLocalDate())
                 assertEquals(9.jan, o.oppdrag110.oppdragsLinje150s[1].datoVedtakTom.toLocalDate())
                 assertEquals(100u, o.oppdrag110.oppdragsLinje150s[1].sats.toDouble().toUInt())
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
         val uid = randomUtbetalingId()
         val sid = SakId("$nextInt")
         val bid = BehandlingId("$nextInt")
         TestTopics.utbetalinger.produce("${uid.id}") {
             utbetaling(Action.CREATE, uid, sid, bid) {
                 listOf(
                     periode(2.jan, 9.jan, 100u)
                 )
             }
         }
         // TestTopics.saker.produce("${Fagsystem.AAP}-${sid.id}") {
         //     SakIdWrapper(sid.id, setOf(uid))
         // }
         TestTopics.aap.produce("${uid.id}") {
             Aap.utbetaling(Action.UPDATE, sid) {
                 listOf(
                     Aap.dag(2.jan, 100u),
                     Aap.dag(3.jan, 100u),
                     Aap.dag(8.jan, 100u),
                     Aap.dag(9.jan, 100u),
                 )
             }
         }
         TestTopics.status.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasValue(StatusReply(Status.MOTTATT))
         TestTopics.utbetalinger.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .hasLastValue("${uid.id}") {
                 utbetaling(Action.UPDATE, uid, sid, bid) {
                     listOf(
                         periode(2.jan, 3.jan, 100u),
                         periode(8.jan, 9.jan, 100u),
                     )
                 }
             }
         TestTopics.oppdrag.assertThat()
             .hasNumberOfRecordsForKey("${uid.id}", 1)
             .withLastValue { o: Oppdrag? ->
                 assertEquals("ENDR", o!!.oppdrag110.kodeEndring)
                 assertEquals(2, o.oppdrag110.oppdragsLinje150s.size)

                 assertEquals(TkodeStatusLinje.OPPH, o.oppdrag110.oppdragsLinje150s.first().kodeStatusLinje)
                 assertEquals(4.jan, o.oppdrag110.oppdragsLinje150s.first().datoStatusFom.toLocalDate())

                 assertEquals(8.jan, o.oppdrag110.oppdragsLinje150s[1].datoVedtakFom.toLocalDate())
                 assertEquals(9.jan, o.oppdrag110.oppdragsLinje150s[1].datoVedtakTom.toLocalDate())
                 assertEquals(100u, o.oppdrag110.oppdragsLinje150s[1].sats.toDouble().toUInt())
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
    fun `simuler legge til en ekstra periode`() {
        val uid = randomUtbetalingId()
        val sid = SakId("$nextInt")
        TestTopics.utbetalinger.produce("${uid.id}") {
            utbetaling(Action.CREATE, uid, sid) {
                listOf(
                    periode(1.jan, 3.jan, 100u),
                )
            }
        }
        TestTopics.utbetalinger.produce("${uid.id}") {
            utbetaling(Action.UPDATE, uid, sid) {
                listOf(
                    periode(1.jan, 2.jan, 100u),
                    periode(3.jan, 3.jan, 200u),
                )
            }
        }

        val bid = BehandlingId("$nextInt")
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.UPDATE, sid, bid, true) {
                listOf(
                    Aap.dag(1.jan, 100u),
                    Aap.dag(2.jan, 100u),
                    Aap.dag(3.jan, 200u),
                    Aap.dag(6.jan, 300u),
                )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasValue(StatusReply(Status.MOTTATT))

        TestTopics.utbetalinger.assertThat().isEmpty()
        TestTopics.oppdrag.assertThat().isEmpty()
        TestTopics.simulering.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .withLastValue { o: SimulerBeregningRequest? ->
                assertEquals("ENDR", o!!.request.oppdrag.kodeEndring)
                assertEquals(6.jan, o.request.oppdrag.oppdragslinjes.last().datoVedtakFom.toLocalDate())
                assertEquals(300u, o.request.oppdrag.oppdragslinjes.last().sats.toDouble().toUInt())
            }
    }

    @Test
    fun `avvent i 5 dager`() {
        val uid = randomUtbetalingId()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val avvent = Avvent(
            fom = LocalDate.now(), 
            tom = LocalDate.now().plusDays(5),
            overføres = LocalDate.now().plusDays(5).nesteVirkedag(), 
        )

        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE, sid, avvent = avvent) {
                listOf(
                    Aap.dag(1.jan, 200u),
                    Aap.dag(2.jan, 200u)
                )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasValue(StatusReply(Status.MOTTATT))
        TestTopics.utbetalinger.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasLastValue("${uid.id}") {
                utbetaling(Action.UPDATE, uid, sid, bid) {
                    listOf(
                        periode(1.jan, 2.jan, 200u)
                    )
                }
            }
        TestTopics.oppdrag.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .withLastValue {
                assertEquals(LocalDate.now(), it!!.oppdrag110.avvent118.datoAvventFom.toLocalDate())
                assertEquals(LocalDate.now().plusDays(5), it!!.oppdrag110.avvent118.datoAvventTom.toLocalDate())
            }
    }
}

private fun String.toLocalDate() = LocalDate.parse(this)
