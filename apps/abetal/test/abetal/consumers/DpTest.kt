package abetal.consumers

import abetal.*
import abetal.models.uuid
import abetal.models.dpUId
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import models.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class DpTest {

    @Test
    fun `1 meldekort i 1 utbetalinger blir til 1 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = dpUId(sid.id, meldeperiode) // 16364e1c-7615-6b30-882b-d7d19ea96279

        TestTopics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = "132460781",
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaling = 553u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestTopics.status.assertThat()
            .has(originalKey)
            .has(originalKey, StatusReply(Status.MOTTATT))

        TestTopics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid,
                    originalKey = originalKey,
                    sakId = sid, 
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    listOf(
                        periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 7), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 8), LocalDate.of(2021, 6, 8), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 9), LocalDate.of(2021, 6, 9), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 10), LocalDate.of(2021, 6, 10), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 11), LocalDate.of(2021, 6, 11), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 14), LocalDate.of(2021, 6, 14), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 15), LocalDate.of(2021, 6, 15), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 16), LocalDate.of(2021, 6, 16), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 17), LocalDate.of(2021, 6, 17), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 18), LocalDate.of(2021, 6, 18), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 21), LocalDate.of(2021, 6, 21), 553u, 1077u),
                    )
                }
                assertEquals(expected, it)
            }

        TestTopics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(11, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("DPORAS", a.kodeKlassifik)
                    assertEquals(553, a.sats.toLong())
                    assertEquals(1077, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                }
            }

        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 1)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid), index = 0)
    }

    @Test
    fun `2 meldekort i 1 utbetalinger blir til 2 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = dpUId(sid.id, meldeperiode1) // 16364e1c-7615-6b30-882b-d7d19ea96279
        val uid2 = dpUId(sid.id, meldeperiode2) // 6fa69f14-a3eb-1457-7859-b3676f59da9d

        TestTopics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaling = 553u,
                ) + Dp.meldekort(
                        meldeperiode = meldeperiode2,
                        fom = LocalDate.of(2021, 7, 7),
                        tom = LocalDate.of(2021, 7, 20),
                        sats = 2377u,
                        utbetaling = 779u,
                    )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestTopics.status.assertThat()
            .has(originalKey, size = 1) 
            .has(originalKey, StatusReply(Status.MOTTATT), index = 0)

        TestTopics.utbetalinger.assertThat()
            .has(uid1.id.toString(), size = 1)
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = originalKey,
                    sakId = sid, 
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    listOf(
                        periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 7), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 8), LocalDate.of(2021, 6, 8), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 9), LocalDate.of(2021, 6, 9), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 10), LocalDate.of(2021, 6, 10), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 11), LocalDate.of(2021, 6, 11), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 14), LocalDate.of(2021, 6, 14), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 15), LocalDate.of(2021, 6, 15), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 16), LocalDate.of(2021, 6, 16), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 17), LocalDate.of(2021, 6, 17), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 18), LocalDate.of(2021, 6, 18), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 21), LocalDate.of(2021, 6, 21), 553u, 1077u), // skal 21 være med?
                    )
                }
                assertEquals(expected, it)
            }
            .has(uid2.id.toString())
            .with(uid2.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = originalKey,
                    sakId = sid, 
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    listOf(
                        periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 7), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 8), LocalDate.of(2021, 7, 8), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 9), LocalDate.of(2021, 7, 9), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 12), LocalDate.of(2021, 7, 12), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 13), LocalDate.of(2021, 7, 13), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 14), LocalDate.of(2021, 7, 14), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 15), LocalDate.of(2021, 7, 15), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 16), LocalDate.of(2021, 7, 16), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 19), LocalDate.of(2021, 7, 19), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 20), LocalDate.of(2021, 7, 20), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 21), LocalDate.of(2021, 7, 21), 779u, 2377u), // skal 21 være med?
                    )
                }
                assertEquals(expected, it)
            }
        TestTopics.oppdrag.assertThat()
            .has(originalKey, size = 1) 
            .with(originalKey, index = 0) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(22, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.take(11).windowed(2, 1) { (a, b) -> 
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("DPORAS", a.kodeKlassifik)
                    assertEquals(553, a.sats.toLong())
                    assertEquals(1077, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                }
                assertNull(it.oppdrag110.oppdragsLinje150s[11].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.takeLast(11).windowed(2, 1) { (a, b) -> 
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("DPORAS", a.kodeKlassifik)
                    assertEquals(779, a.sats.toLong())
                    assertEquals(2377, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                }
            }
        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 2)
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 0) {
                assertEquals(it, setOf(uid1))
            }
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 1) {
                assertEquals(it, setOf(uid1, uid2))
            }
    }

    @Test
    fun `3 meldekort i 1 utbetalinger blir til 3 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val meldeperiode3 = "132462765"
        val uid1 = dpUId(sid.id, meldeperiode1) // 16364e1c-7615-6b30-882b-d7d19ea96279
        val uid2 = dpUId(sid.id, meldeperiode2) // 6fa69f14-a3eb-1457-7859-b3676f59da9d
        val uid3 = dpUId(sid.id, meldeperiode3) // 08e58fec-5907-af4c-e346-4c039df44050

        TestTopics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaling = 553u,
                ) + Dp.meldekort(
                        meldeperiode = meldeperiode2,
                        fom = LocalDate.of(2021, 7, 7),
                        tom = LocalDate.of(2021, 7, 20),
                        sats = 2377u,
                        utbetaling = 779u,
                ) + Dp.meldekort(
                        meldeperiode = meldeperiode3,
                        fom = LocalDate.of(2021, 8, 7),
                        tom = LocalDate.of(2021, 8, 20),
                        sats = 3133u,
                        utbetaling = 3000u,
                    )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestTopics.status.assertThat()
            .has(originalKey, size = 1) 
            .has(originalKey, StatusReply(Status.MOTTATT), index = 0)

        TestTopics.utbetalinger.assertThat()
            .has(uid1.id.toString(), size = 1)
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = originalKey,
                    sakId = sid, 
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    listOf(
                        periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 7), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 8), LocalDate.of(2021, 6, 8), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 9), LocalDate.of(2021, 6, 9), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 10), LocalDate.of(2021, 6, 10), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 11), LocalDate.of(2021, 6, 11), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 14), LocalDate.of(2021, 6, 14), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 15), LocalDate.of(2021, 6, 15), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 16), LocalDate.of(2021, 6, 16), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 17), LocalDate.of(2021, 6, 17), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 18), LocalDate.of(2021, 6, 18), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 21), LocalDate.of(2021, 6, 21), 553u, 1077u), // skal 21 være med?
                    )
                }
                assertEquals(expected, it)
            }
            .has(uid2.id.toString())
            .with(uid2.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = originalKey,
                    sakId = sid, 
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    listOf(
                        periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 7), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 8), LocalDate.of(2021, 7, 8), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 9), LocalDate.of(2021, 7, 9), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 12), LocalDate.of(2021, 7, 12), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 13), LocalDate.of(2021, 7, 13), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 14), LocalDate.of(2021, 7, 14), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 15), LocalDate.of(2021, 7, 15), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 16), LocalDate.of(2021, 7, 16), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 19), LocalDate.of(2021, 7, 19), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 20), LocalDate.of(2021, 7, 20), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 21), LocalDate.of(2021, 7, 21), 779u, 2377u), // skal 21 være med?
                    )
                }
                assertEquals(expected, it)
            }
            .has(uid3.id.toString())
            .with(uid3.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = originalKey,
                    sakId = sid, 
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    listOf(
                        periode(LocalDate.of(2021, 8, 9), LocalDate.of(2021, 8, 9), 3000u, 3133u),
                        periode(LocalDate.of(2021, 8, 10), LocalDate.of(2021, 8, 10), 3000u, 3133u),
                        periode(LocalDate.of(2021, 8, 11), LocalDate.of(2021, 8, 11), 3000u, 3133u),
                        periode(LocalDate.of(2021, 8, 12), LocalDate.of(2021, 8, 12), 3000u, 3133u),
                        periode(LocalDate.of(2021, 8, 13), LocalDate.of(2021, 8, 13), 3000u, 3133u),
                        periode(LocalDate.of(2021, 8, 16), LocalDate.of(2021, 8, 16), 3000u, 3133u),
                        periode(LocalDate.of(2021, 8, 17), LocalDate.of(2021, 8, 17), 3000u, 3133u),
                        periode(LocalDate.of(2021, 8, 18), LocalDate.of(2021, 8, 18), 3000u, 3133u),
                        periode(LocalDate.of(2021, 8, 19), LocalDate.of(2021, 8, 19), 3000u, 3133u),
                        periode(LocalDate.of(2021, 8, 20), LocalDate.of(2021, 8, 20), 3000u, 3133u),
                    )
                }
                assertEquals(expected, it)
            }
        TestTopics.oppdrag.assertThat()
            .has(originalKey, size = 1) 
            .with(originalKey, index = 0) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(32, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.take(11).windowed(2, 1) { (a, b) -> 
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("DPORAS", a.kodeKlassifik)
                    assertEquals(553, a.sats.toLong())
                    assertEquals(1077, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                }
                assertNull(it.oppdrag110.oppdragsLinje150s[11].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.drop(11).take(11).windowed(2, 1) { (a, b) -> 
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("DPORAS", a.kodeKlassifik)
                    assertEquals(779, a.sats.toLong())
                    assertEquals(2377, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                }
                assertNull(it.oppdrag110.oppdragsLinje150s[22].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.takeLast(10).windowed(2, 1) { (a, b) -> 
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("DPORAS", a.kodeKlassifik)
                    assertEquals(3000, a.sats.toLong())
                    assertEquals(3133, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                }
            }
        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 3)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2), index = 1)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2, uid3), index = 2)
    }

    @Test
    fun `2 meldekort i 2 utbetalinger blir til 2 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = dpUId(sid.id, meldeperiode1) // 16364e1c-7615-6b30-882b-d7d19ea96279
        val uid2 = dpUId(sid.id, meldeperiode2) // 6fa69f14-a3eb-1457-7859-b3676f59da9d

        TestTopics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaling = 553u,
                )
            }
        }
        TestTopics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 2377u,
                    utbetaling = 779u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestTopics.status.assertThat()
            .has(originalKey, size = 1) 
            .has(originalKey, StatusReply(Status.MOTTATT), index = 0)

        TestTopics.utbetalinger.assertThat()
            .has(uid1.id.toString(), size = 1)
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = originalKey,
                    sakId = sid, 
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    listOf(
                        periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 7), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 8), LocalDate.of(2021, 6, 8), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 9), LocalDate.of(2021, 6, 9), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 10), LocalDate.of(2021, 6, 10), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 11), LocalDate.of(2021, 6, 11), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 14), LocalDate.of(2021, 6, 14), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 15), LocalDate.of(2021, 6, 15), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 16), LocalDate.of(2021, 6, 16), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 17), LocalDate.of(2021, 6, 17), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 18), LocalDate.of(2021, 6, 18), 553u, 1077u),
                        periode(LocalDate.of(2021, 6, 21), LocalDate.of(2021, 6, 21), 553u, 1077u), // skal 21 være med?
                    )
                }
                assertEquals(expected, it)
            }
            .has(uid2.id.toString())
            .with(uid2.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = originalKey,
                    sakId = sid, 
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    listOf(
                        periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 7), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 8), LocalDate.of(2021, 7, 8), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 9), LocalDate.of(2021, 7, 9), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 12), LocalDate.of(2021, 7, 12), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 13), LocalDate.of(2021, 7, 13), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 14), LocalDate.of(2021, 7, 14), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 15), LocalDate.of(2021, 7, 15), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 16), LocalDate.of(2021, 7, 16), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 19), LocalDate.of(2021, 7, 19), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 20), LocalDate.of(2021, 7, 20), 779u, 2377u),
                        periode(LocalDate.of(2021, 7, 21), LocalDate.of(2021, 7, 21), 779u, 2377u), // skal 21 være med?
                    )
                }
                assertEquals(expected, it)
            }
        TestTopics.oppdrag.assertThat()
            .has(originalKey, size = 1) 
            .with(originalKey, index = 0) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(22, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.take(11).windowed(2, 1) { (a, b) -> 
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("DPORAS", a.kodeKlassifik)
                    assertEquals(553, a.sats.toLong())
                    assertEquals(1077, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                }
                assertNull(it.oppdrag110.oppdragsLinje150s[11].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.takeLast(11).windowed(2, 1) { (a, b) -> 
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("DPORAS", a.kodeKlassifik)
                    assertEquals(779, a.sats.toLong())
                    assertEquals(2377, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                }
            }

        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 2)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2), index = 1)
    }
}

