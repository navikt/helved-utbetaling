package abetal.consumers

import abetal.*
import abetal.models.uuid
import abetal.models.dpUId
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import models.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class DpTest {

    @Test
    fun `1 meldekort blir til 1 utbetaling`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = uuid(sid, Fagsystem.DAGPENGER, meldeperiode)

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
                    uid = UtbetalingId(uid),
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
                assertEquals("NY", it.oppdrag110.kodeEndring)
            }

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
                assertEquals("NY", it.oppdrag110.kodeEndring)
            }
    }

    @Test
    fun `2 meldekort i en utbetaling blir til 2 utbetaling med 1 oppdrag`() {
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
                assertEquals("NY", it.oppdrag110.kodeEndring)
            }
    }

    // @Test
    // fun deterministiskUUIDGenerator() {
    //     val fagsystem = Fagsystem.DAGPENGER
    //     val sakId = SakId("123")
    //     val m1 = "132460781"
    //     val m2 = "232460781"
    //
    //     assertEquals(uuid(sakId, fagsystem, m1), uuid(sakId, fagsystem, m1))
    //     assertEquals(uuid(sakId, fagsystem, m2), uuid(sakId, fagsystem, m2))
    //     assertNotEquals(uuid(sakId, fagsystem, m1), uuid(sakId, fagsystem, m2))
    // }
}
