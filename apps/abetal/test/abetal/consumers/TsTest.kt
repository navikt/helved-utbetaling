package abetal.consumers

import abetal.*
import abetal.TestRuntime
import com.fasterxml.jackson.module.kotlin.readValue
import libs.kafka.JsonSerde
import models.*
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

internal class TsTest {

    @AfterEach
    fun `assert empty topic`() {
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
    }

    @Test
    fun `1 utbetalinger med brukFagområdeTillst = gammel fagområde`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid, sid.id, bid.id, brukFagområdeTillst = true) {
                Ts.periode(7.jun, 18.jun, 1077u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILLEGGSSTØNADER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun, 18.jun, null, 1077u, "TSTBASISP2-OP"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("TILLST", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("ts", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("TSTBASISP2-OP", a.kodeKlassifik)
                    assertEquals(1077, a.sats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                    assertEquals(a.datoVedtakFom, a.datoKlassifikFom)
                    assertEquals(b.datoVedtakFom, b.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(7.jun, 18.jun, 1077u, null)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 1)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid), index = 0)
    }

    @Test
    fun `1 utbetalinger med nytt fagområde`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid, sid.id, bid.id, brukFagområdeTillst = false) {
                Ts.periode(7.jun, 18.jun, 1077u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILLSTPB,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun, 18.jun, null, 1077u, "TSTBASISP2-OP"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("TILLSTPB", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("ts", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("TSTBASISP2-OP", a.kodeKlassifik)
                    assertEquals(1077, a.sats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                    assertEquals(a.datoVedtakFom, a.datoKlassifikFom)
                    assertEquals(b.datoVedtakFom, b.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLSTPB,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(7.jun, 18.jun, 1077u, null)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 1)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid), index = 0)
    }

    @Test
    fun `1 utbetalinger i transaksjon = 1 utbetaling og 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid, sid.id, bid.id) {
                Ts.periode(
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    beløp = 1077u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILLEGGSSTØNADER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, null, 1077u, "TSTBASISP2-OP"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("TILLST", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("ts", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("TSTBASISP2-OP", a.kodeKlassifik)
                    assertEquals(1077, a.sats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                    assertEquals(a.datoVedtakFom, a.datoKlassifikFom)
                    assertEquals(b.datoVedtakFom, b.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 1077u, null)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 1)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid), index = 0)
    }

    @Test
    fun `2 utbetalinger i transaksjon = 2 utbetaling og 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = UtbetalingId(UUID.randomUUID())
        val uid2 = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid1, sid.id, bid.id) {
                Ts.periode(7.jun, 20.jun, 1077u)
            }
        }
        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid2, sid.id, bid.id) {
                Ts.periode(7.jul, 20.jul, 2377u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILLEGGSSTØNADER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun, 20.jun, null, 1077u, "TSTBASISP2-OP"),
                    DetaljerLinje(bid.id, 7.jul, 20.jul, null, 2377u, "TSTBASISP2-OP"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(7.jun, 20.jun, 1077u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid2.id.toString())
            .with(uid2.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    periodetype = Periodetype.EN_GANG,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(7.jul, 20.jul, 2377u, null)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("TILLST", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("ts", oppdrag.oppdrag110.saksbehId)
                assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)

                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(1077, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }

                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(2377, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(7.jun, 20.jun, 1077u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid2.id.toString())
            .with(uid2.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(7.jul, 20.jul, 2377u, null)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 2)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid1))
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid1, uid2), index = 1)
    }

    @Test
    fun `3 utbetalinger i transaksjon = 3 utbetaling og 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = UtbetalingId(UUID.randomUUID())
        val uid2 = UtbetalingId(UUID.randomUUID())
        val uid3 = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid1, sid.id, bid.id) {
                Ts.periode(
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    beløp = 1077u,
                )
            }
        }
        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid2, sid.id, bid.id) {
                Ts.periode(
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    beløp = 2377u,
                )
            }
        }
        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid3, sid.id, bid.id) {
                Ts.periode(
                    fom = LocalDate.of(2021, 8, 7),
                    tom = LocalDate.of(2021, 8, 20),
                    beløp = 3133u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILLEGGSSTØNADER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 20.jun21, null, 1077u, "TSTBASISP2-OP"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, null, 2377u, "TSTBASISP2-OP"),
                    DetaljerLinje(bid.id, 7.aug21, 20.aug21, null, 3133u, "TSTBASISP2-OP"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 20), 1077u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid2.id.toString())
            .with(uid2.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    periodetype = Periodetype.EN_GANG,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 2377u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid3.id.toString())
            .with(uid3.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 8, 7), LocalDate.of(2021, 8, 20), 3133u, null)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("TILLST", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("ts", oppdrag.oppdrag110.saksbehId)
                assertEquals(3, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(1077, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(2377, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[2].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(3133, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.id.toString(), size = 1)
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 20), 1077u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid2.id.toString())
            .with(uid2.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    periodetype = Periodetype.EN_GANG,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 2377u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid3.id.toString())
            .with(uid3.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    periodetype = Periodetype.EN_GANG,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 8, 7), LocalDate.of(2021, 8, 20), 3133u, null)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 3)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid1, uid2), index = 1)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid1, uid2, uid3), index = 2)
    }

    @Test
    fun `2 utbetalinger i transaksjon med ulik bid = 2 utbetalinger og 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid1 = BehandlingId("$nextInt")
        val bid2 = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = UtbetalingId(UUID.randomUUID())
        val uid2 = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid1, sid.id, bid1.id) {
                Ts.periode(
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    beløp = 553u,
                )
            }
        }
        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid2, sid.id, bid2.id) {
                Ts.periode(
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    beløp = 779u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILLEGGSSTØNADER,
                linjer = listOf(
                    DetaljerLinje(bid1.id, 7.jun21, 20.jun21, null, 553u, "TSTBASISP2-OP"),
                    DetaljerLinje(bid2.id, 7.jul21, 20.jul21, null, 779u, "TSTBASISP2-OP"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid1,
                    periodetype = Periodetype.EN_GANG,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 20), 553u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid2.id.toString())
            .with(uid2.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid2,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, null)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("TILLST", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("ts", oppdrag.oppdrag110.saksbehId)
                assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid1.id, it.henvisning)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(553, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid2.id, it.henvisning)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(779, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid1,
                    periodetype = Periodetype.EN_GANG,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 20), 553u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid2.id.toString())
            .with(uid2.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid2,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, null)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 2)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid1))
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid1, uid2), index = 1)
    }

    @Test
    fun `4 utbetalinger i transaksjon med 2 stønader = 4 utbetaling og 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = UtbetalingId(UUID.randomUUID())
        val uid2 = UtbetalingId(UUID.randomUUID())
        val uid3 = UtbetalingId(UUID.randomUUID())
        val uid4 = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(
                uid = uid1,
                sakId = sid.id,
                behandlingId = bid.id,
                stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
            ) {
                Ts.periode(
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    beløp = 1000u,
                )
            }
        }
        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(
                uid = uid2,
                sakId = sid.id,
                behandlingId = bid.id,
                stønad = StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER,
            ) {
                Ts.periode(
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    beløp = 100u,
                )
            }
        }
        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(
                uid = uid3,
                sakId = sid.id,
                behandlingId = bid.id,
                stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
            ) {
                Ts.periode(
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    beløp = 600u,
                )

            }
        }
        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(
                uid = uid4,
                sakId = sid.id,
                behandlingId = bid.id,
                stønad = StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER,
            ) {
                Ts.periode(
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    beløp = 300u,
                )

            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILLEGGSSTØNADER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, null, 1000u, "TSTBASISP2-OP"),
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, null, 100u, "TSLMASISP2-OP"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, null, 600u, "TSTBASISP2-OP"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, null, 300u, "TSLMASISP2-OP"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 1000u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 100u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid3.id.toString())
            .with(uid3.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 600u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid4.id.toString())
            .with(uid4.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid4,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    periodetype = Periodetype.EN_GANG,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 300u, null)
                }
                assertEquals(expected, it)
            }
        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("TILLST", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("ts", oppdrag.oppdrag110.saksbehId)
                assertEquals(4, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(1000, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSLMASISP2-OP", it.kodeKlassifik)
                    assertEquals(100, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[2].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(600, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[3].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSLMASISP2-OP", it.kodeKlassifik)
                    assertEquals(300, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 1000u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    periodetype = Periodetype.EN_GANG,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 100u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid3.id.toString())
            .with(uid3.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    periodetype = Periodetype.EN_GANG,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 600u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid4.id.toString())
            .with(uid4.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid4,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 300u, null)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 4)
            .with(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), index = 0) {
                assertEquals(it, setOf(uid1))
            }
            .with(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), index = 1) {
                assertEquals(it, setOf(uid1, uid2))
            }
            .with(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), index = 2) {
                assertEquals(it, setOf(uid1, uid2, uid3))
            }
            .with(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), index = 3) {
                assertEquals(it, setOf(uid1, uid2, uid3, uid4))
            }
    }

    @Test
    fun `endre eksisterende utbetaling`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce(uid.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid,
                sakId = sid,
                behandlingId = BehandlingId("$nextInt"),
                originalKey = UUID.randomUUID().toString(),
                stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                periodetype = Periodetype.EN_GANG,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 6.jun.atStartOfDay(),
                beslutterId = Navident("ts"),
                saksbehandlerId = Navident("ts"),
                fagsystem = Fagsystem.TILLSTPB,
            ) {
                periode(3.jun, 6.jun, 1000u, null)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(
                uid = uid,
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 7.jun.atStartOfDay(),
                brukFagområdeTillst = false,
            ) {
                Ts.periode(3.jun, 7.jun, 1500u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILLSTPB,
                linjer = listOf(
                    DetaljerLinje(bid.id, 3.jun, 7.jun, null, 1500u, "TSTBASISP2-OP"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.TILLSTPB,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 7.jun, 1500u, null)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("TILLSTPB", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("ts", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(periodeId.toString(), it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(1500, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.TILLSTPB,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 7.jun, 1500u, null)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 2)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid), index = 0)
    }

    @Test
    fun `korriger en korrigering`() {
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        // UTGANGSPUNKT
        val transactionId = UUID.randomUUID().toString()
        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(
                uid = uid,
                sakId = sid.id,
                behandlingId = BehandlingId("$nextInt").id,
                vedtakstidspunkt = 7.jun.atStartOfDay(),
                brukFagområdeTillst = false,
            ) {
                Ts.periode(1.jun, 30.jun, 1100u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)
        TestRuntime.topics.status.assertThat().has(transactionId)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        var periodeId1: PeriodeId? = null
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertNotEquals(it.lastPeriodeId, periodeId1)
                periodeId1 = it.lastPeriodeId
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals(periodeId1.toString(), it.delytelseId)
                    assertEquals(1100, it.sats.toLong())
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(it.lastPeriodeId, periodeId1)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 1)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid), index = 0)


        // KORRIGER
        val transactionId2 = UUID.randomUUID().toString()
        TestRuntime.topics.ts.produce(transactionId2) {
            Ts.utbetaling(
                uid = uid,
                sakId = sid.id,
                behandlingId = BehandlingId("$nextInt").id,
                vedtakstidspunkt = 7.jun.atStartOfDay(),
                brukFagområdeTillst = false,
            ) {
                Ts.periode(1.jun, 30.jun, 1200u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)
        TestRuntime.topics.status.assertThat().has(transactionId2)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        var periodeId2: PeriodeId? = null
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertNotEquals(it.lastPeriodeId, periodeId1)
                periodeId2 = it.lastPeriodeId
            }

        val oppdrag2 = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId2)
            .with(transactionId2) { oppdrag ->
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(periodeId1.toString(), it.refDelytelseId)
                    assertEquals(periodeId2.toString(), it.delytelseId)
                    assertEquals(1200, it.sats.toLong())
                }
            }
            .get(transactionId2)

        TestRuntime.topics.oppdrag.produce(transactionId2) {
            oppdrag2.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(periodeId2, it.lastPeriodeId)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 1)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid), index = 0)

        // KORRIGER EN KORRIGERING
        val transactionId3 = UUID.randomUUID().toString()
        TestRuntime.topics.ts.produce(transactionId3) {
            Ts.utbetaling(
                uid = uid,
                sakId = sid.id,
                behandlingId = BehandlingId("$nextInt").id,
                vedtakstidspunkt = 7.jun.atStartOfDay(),
                brukFagområdeTillst = false,
            ) {
                Ts.periode(1.jun, 30.jun, 1300u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)
        TestRuntime.topics.status.assertThat().has(transactionId3)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        var periodeId3: PeriodeId? = null
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertNotEquals(it.lastPeriodeId, periodeId1)
                assertNotEquals(it.lastPeriodeId, periodeId2)
                periodeId3 = it.lastPeriodeId
            }

        val oppdrag3 = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId3)
            .with(transactionId3) { oppdrag ->
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(periodeId2.toString(), it.refDelytelseId)
                    assertEquals(periodeId3.toString(), it.delytelseId)
                    assertEquals(1300, it.sats.toLong())
                }
            }
            .get(transactionId3)

        TestRuntime.topics.oppdrag.produce(transactionId3) {
            oppdrag3.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(periodeId3, it.lastPeriodeId)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 1)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid), index = 0)
    }

    @Test
    fun `forlenge periode på eksisterende utbetaling`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = UtbetalingId(UUID.randomUUID())
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce(uid1.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = UUID.randomUUID().toString(),
                stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP,
                periodetype = Periodetype.EN_GANG,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 1.jun.atStartOfDay(),
                beslutterId = Navident("ts"),
                saksbehandlerId = Navident("ts"),
                fagsystem = Fagsystem.TILLEGGSSTØNADER,
            ) {
                periode(1.jun, 15.jun, 1500u, null)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(
                uid = uid1,
                sakId = sid.id,
                periodetype = Periodetype.EN_GANG,
                stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP,
                behandlingId = bid.id,
                vedtakstidspunkt = 1.jun.atStartOfDay(),
            ) {
                Ts.periode(1.jun, 30.jun, 3000u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILLEGGSSTØNADER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 1.jun, 30.jun, null, 3000u, "TSBUASIA-OP"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(1.jun, 30.jun, 3000u, null)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("TILLST", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("ts", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(periodeId.toString(), it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSBUASIA-OP", it.kodeKlassifik)
                    assertEquals(3000, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(1.jun, 30.jun, 3000u, null)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 2)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid1), index = 1)
    }

    @Test
    fun `endre periode på eksisterende utbetaling`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = UtbetalingId(UUID.randomUUID())
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce(uid1.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = UUID.randomUUID().toString(),
                stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP,
                periodetype = Periodetype.EN_GANG,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 1.jun.atStartOfDay(),
                beslutterId = Navident("ts"),
                saksbehandlerId = Navident("ts"),
                fagsystem = Fagsystem.TILLEGGSSTØNADER,
            ) {
                periode(1.jun, 30.jun, 3000u, null)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(
                uid = uid1,
                sakId = sid.id,
                periodetype = Periodetype.EN_GANG,
                stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP,
                behandlingId = bid.id,
                vedtakstidspunkt = 1.jun.atStartOfDay(),
            ) {
                Ts.periode(1.jun, 30.jun, 3070u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILLEGGSSTØNADER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 1.jun, 30.jun, null, 3070u, "TSBUASIA-OP"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(1.jun, 30.jun, 3070u, null)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("TILLST", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("ts", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(periodeId.toString(), it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSBUASIA-OP", it.kodeKlassifik)
                    assertEquals(3070, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(1.jun, 30.jun, 3070u, null)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 2)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid1), index = 1)
    }
    @Test
    fun `legge til periode på eksisterende utbetaling`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = UtbetalingId(UUID.randomUUID())
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce(uid1.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = UUID.randomUUID().toString(),
                stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP,
                periodetype = Periodetype.EN_GANG,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 1.jun.atStartOfDay(),
                beslutterId = Navident("ts"),
                saksbehandlerId = Navident("ts"),
                fagsystem = Fagsystem.TILLEGGSSTØNADER,
            ) {
                periode(1.jun, 3.jun, 210u, null)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(
                uid = uid1,
                sakId = sid.id,
                periodetype = Periodetype.EN_GANG,
                stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP,
                behandlingId = bid.id,
                vedtakstidspunkt = 1.jun.atStartOfDay(),
            ) {
                Ts.periode(1.jun, 3.jun, 210u) +
                Ts.periode(6.jun, 6.jun, 70u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILLEGGSSTØNADER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 6.jun, 6.jun, null, 70u, "TSBUASIA-OP"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(1.jun, 3.jun, 210u, null) +
                    periode(6.jun, 6.jun, 70u, null)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("TILLST", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("ts", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(periodeId.toString(), it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSBUASIA-OP", it.kodeKlassifik)
                    assertEquals(70, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(1.jun, 3.jun, 210u, null) +
                    periode(6.jun, 6.jun, 70u, null)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 2)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid1), index = 1)
    }

    @Test
    fun `endre eksisterende utbetaling med tom periodelist = opphør`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId1 = UUID.randomUUID().toString()
        val uid1 = UtbetalingId(UUID.randomUUID())
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce("$uid1") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = transactionId1,
                stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("ts"),
                saksbehandlerId = Navident("ts"),
                fagsystem = Fagsystem.TILLEGGSSTØNADER,
            ) {
                periode(2.jun, 13.jun, 100u, null)
            }
        }

        TestRuntime.topics.ts.produce(transactionId1) {
            Ts.utbetaling(
                uid = uid1,
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                emptyList()
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            status = Status.MOTTATT,
            detaljer = Detaljer(
                Fagsystem.TILLEGGSSTØNADER, 
                listOf(DetaljerLinje(bid.id, 2.jun, 13.jun, null, 0u, "TSTBASISP2-OP"))
            )
        )

        TestRuntime.topics.status.assertThat()
            .has(transactionId1)
            .has(transactionId1, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.DELETE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId1,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = 14.jun.atStartOfDay(),
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.jun, 13.jun, 100u, null)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId1)
            .with(transactionId1) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("TILLST", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("ts", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
                    assertEquals(2.jun, it.datoStatusFom.toLocalDate())
                    assertEquals(periodeId.toString(), it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(100, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId1)

        TestRuntime.topics.oppdrag.produce(transactionId1) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.DELETE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId1,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    vedtakstidspunkt = 14.jun.atStartOfDay(),
                    beslutterId = Navident("ts"),
                    saksbehandlerId = Navident("ts"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.jun, 13.jun, 100u, null)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), size = 2)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.TILLEGGSSTØNADER), setOf(), index = 1)
    }

    @Test
    fun `simuler utbetaling blir ikke persistert`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid, sid.id, bid.id, dryrun = true) {
                Ts.periode(
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    beløp = 1077u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.status.assertThat().isEmpty()
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.saker.assertThat().isEmpty()
        TestRuntime.topics.simulering.assertThat()
            .hasTotal(1)
            .has(transactionId)
            .with(transactionId) { simulering ->
                assertEquals("12345678910", simulering.request.oppdrag.oppdragGjelderId)
                assertEquals("NY", simulering.request.oppdrag.kodeEndring)
                assertEquals("TILLST", simulering.request.oppdrag.kodeFagomraade)
                assertEquals(sid.id, simulering.request.oppdrag.fagsystemId)
                assertEquals("MND", simulering.request.oppdrag.utbetFrekvens)
                assertEquals("12345678910", simulering.request.oppdrag.oppdragGjelderId)
                assertEquals("ts", simulering.request.oppdrag.saksbehId)
                assertEquals(1, simulering.request.oppdrag.oppdragslinjes.size)
                assertNull(simulering.request.oppdrag.oppdragslinjes[0].refDelytelseId)
                simulering.request.oppdrag.oppdragslinjes[0].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(1077, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
    }

    @Test
    fun `simuler 4 utbetalinger blir til 1 xml`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = UtbetalingId(UUID.randomUUID())
        val uid2 = UtbetalingId(UUID.randomUUID())
        val uid3 = UtbetalingId(UUID.randomUUID())
        val uid4 = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid1, sid.id, bid.id, dryrun = true) {
                Ts.periode(6.jun, 6.jun, 70u)
            }
        }
        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid2, sid.id, bid.id, dryrun = true) {
                Ts.periode(7.jun, 7.jun, 70u)
            }
        }
        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid3, sid.id, bid.id, dryrun = true) {
                Ts.periode(6.jun, 6.jun, 140u)
            }
        }
        TestRuntime.topics.ts.produce(transactionId) {
            Ts.utbetaling(uid4, sid.id, bid.id, dryrun = true) {
                Ts.periode(7.jun, 7.jun, 140u)
            }
        }
        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.status.assertThat().isEmpty()
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.saker.assertThat().isEmpty()
        TestRuntime.topics.simulering.assertThat()
            .hasTotal(1)
            .has(transactionId)
            .with(transactionId) { simulering ->
                assertEquals("12345678910", simulering.request.oppdrag.oppdragGjelderId)
                assertEquals("NY", simulering.request.oppdrag.kodeEndring)
                assertEquals("TILLST", simulering.request.oppdrag.kodeFagomraade)
                assertEquals(sid.id, simulering.request.oppdrag.fagsystemId)
                assertEquals("MND", simulering.request.oppdrag.utbetFrekvens)
                assertEquals("12345678910", simulering.request.oppdrag.oppdragGjelderId)
                assertEquals("ts", simulering.request.oppdrag.saksbehId)
                assertEquals(4, simulering.request.oppdrag.oppdragslinjes.size)
                assertNull(simulering.request.oppdrag.oppdragslinjes[0].refDelytelseId)
                simulering.request.oppdrag.oppdragslinjes[0].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(70, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                simulering.request.oppdrag.oppdragslinjes[1].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(70, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                simulering.request.oppdrag.oppdragslinjes[2].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(140, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                simulering.request.oppdrag.oppdragslinjes[3].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(140, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
    }

    @Test
    fun `simulering uten endring`() {
        val key = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val uid1 = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.utbetalinger.produce("$uid1") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = key,
                stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("ts"),
                saksbehandlerId = Navident("ts"),
                fagsystem = Fagsystem.TILLEGGSSTØNADER,
            ) {
                periode(1.jan, 2.jan, 100u, null)
            }
        }
        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.ts.produce(key) {
            Ts.utbetaling(uid1, sid.id, bid.id, dryrun = true) {
                Ts.periode(
                    fom = 1.jan,
                    tom = 2.jan,
                    beløp = 100u,
                )
            }
        }
        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.status.assertThat().has(key).with(key) { statusReply ->
            assertEquals(Status.OK, statusReply.status)
        }

        TestRuntime.topics.simulering.assertThat().hasNot(key)

    }
}

