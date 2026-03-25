package abetal.ts

import abetal.*
import models.*
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class TsTest : ConsumerTestBase() {

    @Test
    fun `migration - continuing after migrated opphør`() {
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
                // empty
            }
        }
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.TILLEGGSSTØNADER)) {
            setOf(uid)
        }


        TestRuntime.topics.ts.produce(transactionId) {
            Ts.dto(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 7.jun.atStartOfDay(),
            ) {
                Ts.utbetaling(uid, brukFagområdeTillst = false) {
                    periode(3.jun, 7.jun, 1500u)
                }
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId) {
                Ts.mottatt(Fagsystem.TILLSTPB) {
                    linje(bid, 3.jun, 7.jun, 1500u)
                }
            }

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())

        TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
    }

    @Test
    fun `migration - utbetaling using legacy fagområde`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.dto(sid.id, bid.id) {
                Ts.utbetaling(uid, brukFagområdeTillst = true) {
                    periode(7.jun, 18.jun, 1077u)
                }
            }.asBytes()
        }


        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId) {
                Ts.mottatt(Fagsystem.TILLEGGSSTØNADER) {
                    linje(bid, 7.jun, 18.jun, 1077u)
                }
            }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())

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

        kvitterOk(transactionId, oppdrag, listOf(uid))

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
    }

    @Test
    fun `create - utbetaling with new fagområde`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.dto(sid.id, bid.id) {
                Ts.utbetaling(uid, brukFagområdeTillst = false) {
                    periode(7.jun, 18.jun, 1077u)
                }
            }.asBytes()
        }


        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId) {
                Ts.mottatt(Fagsystem.TILLSTPB) {
                    linje(bid, 7.jun, 18.jun, 1077u)
                }
            }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())

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

        kvitterOk(transactionId, oppdrag, listOf(uid))

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
    }

    @Test
    fun `create - utbetaling on internal topic`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.tsIntern.produce(transactionId) {
            Ts.dto(sid.id, bid.id) {
                Ts.utbetaling(uid) {
                    periode(7.jun21, 18.jun21, 1077u)
                }
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(transactionId)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())

        val oppdrag = TestRuntime.topics.oppdrag.assertThat().has(transactionId).get(transactionId)

        kvitterOk(transactionId, oppdrag, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
    }

    @Test
    fun `update - modifying existing utbetaling`() {
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
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.TILLEGGSSTØNADER)) {
            setOf(uid)
        }


        TestRuntime.topics.ts.produce(transactionId) {
            Ts.dto(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 7.jun.atStartOfDay(),
            ) {
                Ts.utbetaling(uid, brukFagområdeTillst = false) {
                    periode(3.jun, 7.jun, 1500u)
                }
            }.asBytes()
        }


        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId) {
                Ts.mottatt(Fagsystem.TILLSTPB) {
                    linje(bid, 3.jun, 7.jun, 1500u)
                }
            }

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

        kvitterOk(transactionId, oppdrag, listOf(uid))

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

    }

    @Test
    fun `update - correcting a previous correction`() {
        val sid = SakId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        // UTGANGSPUNKT
        val transactionId = UUID.randomUUID().toString()
        TestRuntime.topics.ts.produce(transactionId) {
            Ts.dto(
                sakId = sid.id,
                behandlingId = BehandlingId("$nextInt").id,
                vedtakstidspunkt = 7.jun.atStartOfDay(),
            ) {
                Ts.utbetaling(uid, brukFagområdeTillst = false) {
                    periode(1.jun, 30.jun, 1100u)
                }
            }.asBytes()
        }

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

        kvitterOk(transactionId, oppdrag, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(it.lastPeriodeId, periodeId1)
            }



        // KORRIGER
        val transactionId2 = UUID.randomUUID().toString()
        TestRuntime.topics.ts.produce(transactionId2) {
            Ts.dto(
                sakId = sid.id,
                behandlingId = BehandlingId("$nextInt").id,
                vedtakstidspunkt = 7.jun.atStartOfDay(),
            ) {
                Ts.utbetaling(uid, brukFagområdeTillst = false) {
                    periode(1.jun, 30.jun, 1200u)
                }
            }.asBytes()
        }

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

        kvitterOk(transactionId2, oppdrag2, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(periodeId2, it.lastPeriodeId)
            }


        // KORRIGER EN KORRIGERING
        val transactionId3 = UUID.randomUUID().toString()
        TestRuntime.topics.ts.produce(transactionId3) {
            Ts.dto(
                sakId = sid.id,
                behandlingId = BehandlingId("$nextInt").id,
                vedtakstidspunkt = 7.jun.atStartOfDay(),
            ) {
                Ts.utbetaling(uid, brukFagområdeTillst = false) {
                    periode(1.jun, 30.jun, 1300u)
                }
            }.asBytes()
        }

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

        kvitterOk(transactionId3, oppdrag3, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(periodeId3, it.lastPeriodeId)
            }

    }

    @Test
    fun `update - extending periode on existing utbetaling`() {
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
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.TILLEGGSSTØNADER)) {
            setOf(uid1)
        }


        TestRuntime.topics.ts.produce(transactionId) {
            Ts.dto(
                sakId = sid.id,
                periodetype = Periodetype.EN_GANG,
                behandlingId = bid.id,
                vedtakstidspunkt = 1.jun.atStartOfDay(),
            ) {
                Ts.utbetaling(uid1, stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP) { // tillst=false ?
                    periode(1.jun, 30.jun, 3000u)
                }
                
            }.asBytes()
        }


        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId) {
                Ts.mottatt(Fagsystem.TILLEGGSSTØNADER) {
                    linje(bid, 1.jun, 30.jun, 3000u, "TSBUASIA-OP")
                }
            }

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

        kvitterOk(transactionId, oppdrag, listOf(uid1))

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

    }

    @Test
    fun `update - changing periode on existing utbetaling`() {
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
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.TILLEGGSSTØNADER)) {
            setOf(uid1)
        }


        TestRuntime.topics.ts.produce(transactionId) {
            Ts.dto(
                sakId = sid.id,
                periodetype = Periodetype.EN_GANG,
                behandlingId = bid.id,
                vedtakstidspunkt = 1.jun.atStartOfDay(),
            ) {
                Ts.utbetaling(uid = uid1, stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP) { // tillst=tru3?
                    periode(1.jun, 30.jun, 3070u)
                }
            }.asBytes()
        }


        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId) {
                Ts.mottatt(Fagsystem.TILLEGGSSTØNADER) {
                    linje(bid, 1.jun, 30.jun, 3070u, "TSBUASIA-OP")
                }
            }

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

        kvitterOk(transactionId, oppdrag, listOf(uid1))

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

    }

    @Test
    fun `update - adding periode to existing utbetaling`() {
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
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.TILLEGGSSTØNADER)) {
            setOf(uid1)
        }


        TestRuntime.topics.ts.produce(transactionId) {
            Ts.dto(
                sakId = sid.id,
                periodetype = Periodetype.EN_GANG,
                behandlingId = bid.id,
                vedtakstidspunkt = 1.jun.atStartOfDay(),
            ) {
                Ts.utbetaling(uid1, stønad = StønadTypeTilleggsstønader.BOUTGIFTER_AAP) { // tillst=true?=
                    periode(1.jun, 3.jun, 210u)
                    periode(6.jun, 6.jun, 70u)
                }
            }.asBytes()
        }


        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId) {
                Ts.mottatt(Fagsystem.TILLEGGSSTØNADER) {
                    linje(bid, 6.jun, 6.jun, 70u, "TSBUASIA-OP")
                }
            }

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
                    periode(1.jun, 3.jun, 210u, null)
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

        kvitterOk(transactionId, oppdrag, listOf(uid1))

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
                    periode(1.jun, 3.jun, 210u, null)
                    periode(6.jun, 6.jun, 70u, null)
                }
                assertEquals(expected, it)
            }

    }

    @Test
    fun `opphør - empty periode list cancels utbetaling`() {
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
                periodetype = Periodetype.EN_GANG
            ) {
                periode(2.jun, 13.jun, 100u, null)
            }
        }

        TestRuntime.topics.ts.produce(transactionId1) {
            Ts.dto(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                Ts.utbetaling(uid = uid1) {
                    // empty
                }
            }.asBytes()
        }


        TestRuntime.topics.status.assertThat()
            .has(transactionId1)
            .has(transactionId1) {
                Ts.mottatt(Fagsystem.TILLEGGSSTØNADER) {
                    linje(bid, 2.jun, 13.jun, 0u)
                }
            }

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
                    personident = Personident("12345678910"),
                    periodetype = Periodetype.EN_GANG,
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
                //assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                assertNull(oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
                    assertEquals(2.jun, it.datoStatusFom.toLocalDate())
                    //assertEquals(periodeId.toString(), it.refDelytelseId)
                    assertNull(it.refDelytelseId)
                    assertEquals("ENDR", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TSTBASISP2-OP", it.kodeKlassifik)
                    assertEquals(100, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId1)

        kvitterOk(transactionId1, oppdrag, listOf(uid1))

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
                    personident = Personident("12345678910"),
                    periodetype = Periodetype.EN_GANG,
                ) {
                    periode(2.jun, 13.jun, 100u, null)
                }
                assertEquals(expected, it)
            }
    }

    @Test
    fun `simulation - no changes produces no oppdrag`() {
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

        TestRuntime.topics.ts.produce(key) {
            Ts.dto(sid.id, bid.id, dryrun = true) {
                Ts.utbetaling(uid1) {
                    periode(1.jan, 2.jan, 100u)
                }
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(key).with(key) { statusReply ->
            assertEquals(Status.OK, statusReply.status)
        }

        TestRuntime.topics.simulering.assertThat().hasNot(key)

        TestRuntime.topics.dryrunTs.assertThat()
            .has(key)
            .with(key) { simulering ->
                assertTrue(simulering is Info)
                assertEquals(Info.Status.OK_UTEN_ENDRING, simulering.status)
                assertEquals(Fagsystem.TILLEGGSSTØNADER, simulering.fagsystem)
            }

    }

    @Test
    fun `simulation - ny sak med dryrun produserer simuleringsrequest, ikke OK_UTEN_ENDRING`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val tid = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.ts.produce(tid) {
            Ts.dto(sid.id, bid.id, dryrun = true) {
                Ts.utbetaling(uid) {
                    periode(7.jun, 18.jun, 553u)
                }
            }.asBytes()
        }

        TestRuntime.topics.simulering.assertThat()
            .hasTotal(1)
            .has(tid)
            .with(tid) { simulering ->
                assertEquals("NY", simulering.request.oppdrag.kodeEndring)
                assertEquals(sid.id, simulering.request.oppdrag.fagsystemId)
                assertEquals("12345678910", simulering.request.oppdrag.oppdragGjelderId)
            }

        TestRuntime.topics.dryrunTs.assertThat().isEmpty()
        TestRuntime.topics.status.assertThat().isEmpty()
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
    }

    @Test
    fun `simulation - dryrun med endringer produserer ENDR simuleringsrequest`() {
        val key = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.utbetalinger.produce("$uid") {
            utbetaling(
                action = Action.CREATE,
                uid = uid,
                sakId = sid,
                behandlingId = bid,
                originalKey = key,
                stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                periodetype = Periodetype.EN_GANG,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("ts"),
                saksbehandlerId = Navident("ts"),
                fagsystem = Fagsystem.TILLEGGSSTØNADER,
            ) {
                periode(1.jan, 2.jan, 100u, null)
            }
        }

        TestRuntime.topics.ts.produce(key) {
            Ts.dto(sid.id, bid.id, dryrun = true) {
                Ts.utbetaling(uid) {
                    periode(1.jan, 2.jan, 200u)
                }
            }.asBytes()
        }

        TestRuntime.topics.simulering.assertThat()
            .has(key)
            .with(key) { simulering ->
                assertEquals("ENDR", simulering.request.oppdrag.kodeEndring)
                assertEquals("TILLST", simulering.request.oppdrag.kodeFagomraade)
                assertEquals(sid.id, simulering.request.oppdrag.fagsystemId)
                assertEquals("12345678910", simulering.request.oppdrag.oppdragGjelderId)
                assertNull(simulering.request.oppdrag.oppdragslinjes[0].kodeStatusLinje)
            }

        TestRuntime.topics.dryrunTs.assertThat().isEmpty()
        TestRuntime.topics.status.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
    }

    @Test
    fun `simulation - opphør med dryrun produserer simuleringsrequest`() {
        val key = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce("$uid") {
            utbetaling(
                action = Action.CREATE,
                uid = uid,
                sakId = sid,
                behandlingId = bid,
                originalKey = key,
                stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("ts"),
                saksbehandlerId = Navident("ts"),
                fagsystem = Fagsystem.TILLEGGSSTØNADER,
            ) {
                periode(1.jan, 2.jan, 100u, null)
            }
        }

        TestRuntime.topics.ts.produce(key) {
            Ts.dto(sid.id, bid.id, dryrun = true) {
                Ts.utbetaling(uid) {
                    // empty - triggers opphør
                }
            }.asBytes()
        }

        TestRuntime.topics.simulering.assertThat()
            .has(key)
            .with(key) { simulering ->
                assertEquals("ENDR", simulering.request.oppdrag.kodeEndring)
                assertEquals("TILLST", simulering.request.oppdrag.kodeFagomraade)
                assertEquals(sid.id, simulering.request.oppdrag.fagsystemId)
                assertEquals("12345678910", simulering.request.oppdrag.oppdragGjelderId)
                assertEquals(1, simulering.request.oppdrag.oppdragslinjes.size)
                assertNotNull(simulering.request.oppdrag.oppdragslinjes[0].kodeStatusLinje)
            }

        TestRuntime.topics.dryrunTs.assertThat().isEmpty()
        TestRuntime.topics.status.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
    }

    @Test
    fun `update - changing sakId on utbetaling with duplicate perioder`() {
        val sid1 = SakId("$nextInt")
        val sid2 = SakId("$nextInt")
        val bid1 = BehandlingId("$nextInt")
        val bid2 = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce("$uid") {
            utbetaling(
                action = Action.CREATE,
                uid = uid,
                sakId = sid1,
                behandlingId = bid1,
                originalKey = UUID.randomUUID().toString(),
                stønad = StønadTypeTilleggsstønader.DAGLIG_REISE_AAP,
                periodetype = Periodetype.UKEDAG,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 6.jun.atStartOfDay(),
                beslutterId = Navident("ts"),
                saksbehandlerId = Navident("ts"),
                fagsystem = Fagsystem.TILLSTDR,
            ) {
                periode(1.jun, 5.jun, 1000u, null)
                periode(8.jun, 10.jun, 500u, null)
            }
        }

        TestRuntime.topics.saker.produce(SakKey(sid1, Fagsystem.TILLSTDR)) { setOf(uid) }


        TestRuntime.topics.ts.produce(transactionId) {
            Ts.dto(
                sakId = sid2.id,
                behandlingId = bid2.id,
                vedtakstidspunkt = 7.jun.atStartOfDay(),
            ) {
                Ts.utbetaling(uid) {
                    periode(1.jun, 5.jun, 1000u)
                    periode(8.jun, 10.jun, 500u)
                }
            }.asBytes()
        }


        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId) {
                Ts.feilet(ApiError(400, "Kan ikke endre 'sakId'", DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_SAK_ID.doc))
            }
    }

    @Test
    fun `opphør - uses new behandlingId and saksbehandler`() {
        val sid = SakId("$nextInt")
        val bidOld = BehandlingId("1833")
        val bidNew = BehandlingId("20971")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce("$uid") {
            utbetaling(
                action = Action.CREATE,
                uid = uid,
                sakId = sid,
                behandlingId = bidOld,
                originalKey = UUID.randomUUID().toString(),
                stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("ts"),
                saksbehandlerId = Navident("ts"),
                fagsystem = Fagsystem.TILLEGGSSTØNADER,
                periodetype = Periodetype.EN_GANG
            ) {
                periode(2.jun, 13.jun, 100u, null)
            }
        }
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.TILLEGGSSTØNADER)) {
            setOf(uid)
        }


        TestRuntime.topics.ts.produce(transactionId) {
            Ts.dto(
                sakId = sid.id,
                behandlingId = bidNew.id,
                vedtakstidspunkt = 15.jun.atStartOfDay(),
            ) {
                Ts.utbetaling(uid = uid) {
                }
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())

        TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
                    assertEquals(bidNew.id, it.henvisning)
                }
            }
    }

    @Test
    fun `status - FEILET ved deserialiseringsfeil`() {
        val transactionId = UUID.randomUUID().toString()

        TestRuntime.topics.ts.produce(transactionId) {
            """{ "ugyldig-json": """.toByteArray()
        }

        val status = TestRuntime.topics.status.readValue()
        assertEquals(Status.FEILET, status.status)
        assertNotNull(status.error)
    }

    @Test
    fun `status - FEILET ved prosesseringsfeil`() {
        val transactionId = UUID.randomUUID().toString()

        TestRuntime.topics.ts.produce(transactionId) {
            Ts.dto(
                sakId = "sak-1",
                behandlingId = "beh-1",
                periodetype = Periodetype.EN_GANG,
            ) {
                Ts.utbetaling(
                    uid = UtbetalingId(UUID.randomUUID()),
                    brukFagområdeTillst = true,
                    stønad = StønadTypeTilleggsstønader.REIS_ARBEID_AAP,
                ) {
                    periode(1.jun, 1.jun, 100u)
                }
            }.asBytes()
        }

        val status = TestRuntime.topics.status.readValue()
        assertEquals(Status.FEILET, status.status)
        assertNotNull(status.error)
    }

}
