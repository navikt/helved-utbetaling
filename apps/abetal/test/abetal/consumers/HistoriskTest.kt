package abetal.consumers

import abetal.HISTORISK_TX_GAP_MS
import abetal.Historisk
import abetal.SakKey
import abetal.TestRuntime
import abetal.aug21
import abetal.jan
import abetal.jul
import abetal.jul21
import abetal.jun
import abetal.jun21
import abetal.nextInt
import abetal.periode
import abetal.toLocalDate
import abetal.utbetaling
import java.time.LocalDate
import java.util.UUID
import models.Action
import models.ApiError
import models.BehandlingId
import models.Detaljer
import models.DetaljerLinje
import models.DocumentedErrors
import models.Fagsystem
import models.Navident
import models.PeriodeId
import models.Periodetype
import models.Personident
import models.SakId
import models.Status
import models.StatusReply
import models.StønadTypeHistorisk
import models.UtbetalingId
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

internal class HistoriskTest {

    @AfterEach
    fun `assert empty topic`() {
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
    }

    @Test
    fun `utbetal på intern historisk topic`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.historiskIntern.produce(transactionId) {
            Historisk.utbetaling(uid, sid.id, bid.id) {
                Historisk.periode(
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    beløp = 1077u,
                )
            }
        }

        TestRuntime.topics.status.assertThat().has(transactionId)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat().has(transactionId).get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
    }

    @Test
    @Disabled // støtter ikke sessionWindows lengere
    fun `2 utbetalinger i transaksjon = 2 utbetaling og 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = UtbetalingId(UUID.randomUUID())
        val uid2 = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(uid1, sid.id, bid.id) {
                Historisk.periode(7.jun, 20.jun, 1077u)
            }
        }
        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(uid2, sid.id, bid.id) {
                Historisk.periode(7.jul, 20.jul, 2377u)
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.HISTORISK,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun, 20.jun, null, 1077u, "HJRIM"),
                    DetaljerLinje(bid.id, 7.jul, 20.jul, null, 2377u, "HJRIM"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    periodetype = Periodetype.EN_GANG,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                assertEquals("HELSREF", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("ENG", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("historisk", oppdrag.oppdrag110.saksbehId)
                assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)

                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("HJRIM", it.kodeKlassifik)
                    assertEquals(1077, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }

                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("HJRIM", it.kodeKlassifik)
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
                    fagsystem = Fagsystem.HISTORISK,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
                    personident = Personident("12345678910")
                ) {
                    periode(7.jul, 20.jul, 2377u, null)
                }
                assertEquals(expected, it)
            }

    }

    @Test
    @Disabled // støtter ikke sessionWindows lengere
    fun `3 utbetalinger i transaksjon = 3 utbetaling og 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = UtbetalingId(UUID.randomUUID())
        val uid2 = UtbetalingId(UUID.randomUUID())
        val uid3 = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(uid1, sid.id, bid.id) {
                Historisk.periode(
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    beløp = 1077u,
                )
            }
        }
        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(uid2, sid.id, bid.id) {
                Historisk.periode(
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    beløp = 2377u,
                )
            }
        }
        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(uid3, sid.id, bid.id) {
                Historisk.periode(
                    fom = LocalDate.of(2021, 8, 7),
                    tom = LocalDate.of(2021, 8, 20),
                    beløp = 3133u,
                )
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.HISTORISK,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 20.jun21, null, 1077u, "HJRIM"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, null, 2377u, "HJRIM"),
                    DetaljerLinje(bid.id, 7.aug21, 20.aug21, null, 3133u, "HJRIM"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                assertEquals("HELSREF", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("ENG", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("historisk", oppdrag.oppdrag110.saksbehId)
                assertEquals(3, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("HJRIM", it.kodeKlassifik)
                    assertEquals(1077, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("HJRIM", it.kodeKlassifik)
                    assertEquals(2377, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[2].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("HJRIM", it.kodeKlassifik)
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
                    fagsystem = Fagsystem.HISTORISK,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 8, 7), LocalDate.of(2021, 8, 20), 3133u, null)
                }
                assertEquals(expected, it)
            }
    }

    @Test
    @Disabled // støtter ikke sessionWindows lengere
    fun `2 utbetalinger i transaksjon med ulik bid = 2 utbetalinger og 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid1 = BehandlingId("$nextInt")
        val bid2 = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = UtbetalingId(UUID.randomUUID())
        val uid2 = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(uid1, sid.id, bid1.id) {
                Historisk.periode(
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    beløp = 553u,
                )
            }
        }
        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(uid2, sid.id, bid2.id) {
                Historisk.periode(
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    beløp = 779u,
                )
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.HISTORISK,
                linjer = listOf(
                    DetaljerLinje(bid1.id, 7.jun21, 20.jun21, null, 553u, "HJRIM"),
                    DetaljerLinje(bid2.id, 7.jul21, 20.jul21, null, 779u, "HJRIM"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                assertEquals("HELSREF", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("ENG", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("historisk", oppdrag.oppdrag110.saksbehId)
                assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid1.id, it.henvisning)
                    assertEquals("HJRIM", it.kodeKlassifik)
                    assertEquals(553, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid2.id, it.henvisning)
                    assertEquals("HJRIM", it.kodeKlassifik)
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
                    fagsystem = Fagsystem.HISTORISK,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, null)
                }
                assertEquals(expected, it)
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
                stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                periodetype = Periodetype.EN_GANG,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 6.jun.atStartOfDay(),
                beslutterId = Navident("historisk"),
                saksbehandlerId = Navident("historisk"),
                fagsystem = Fagsystem.HISTORISK,
            ) {
                periode(3.jun, 6.jun, 1000u, null)
            }
        }
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.HISTORISK)) {
            setOf(uid)
        }


        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(
                uid = uid,
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 7.jun.atStartOfDay(),
            ) {
                Historisk.periode(3.jun, 7.jun, 1500u)
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.HISTORISK,
                linjer = listOf(
                    DetaljerLinje(bid.id, 3.jun, 7.jun, null, 1500u, "HJRIM"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                assertEquals("HELSREF", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("ENG", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("historisk", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(periodeId.toString(), it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("HJRIM", it.kodeKlassifik)
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
                    fagsystem = Fagsystem.HISTORISK,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 7.jun, 1500u, null)
                }
                assertEquals(expected, it)
            }

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
                stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                periodetype = Periodetype.EN_GANG,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 1.jun.atStartOfDay(),
                beslutterId = Navident("historisk"),
                saksbehandlerId = Navident("historisk"),
                fagsystem = Fagsystem.HISTORISK,
            ) {
                periode(1.jun, 15.jun, 1500u, null)
            }
        }
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.HISTORISK)) {
            setOf(uid1)
        }


        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(
                uid = uid1,
                sakId = sid.id,
                periodetype = Periodetype.EN_GANG,
                stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                behandlingId = bid.id,
                vedtakstidspunkt = 1.jun.atStartOfDay(),
            ) {
                Historisk.periode(1.jun, 30.jun, 3000u)
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.HISTORISK,
                linjer = listOf(
                    DetaljerLinje(bid.id, 1.jun, 30.jun, null, 3000u, "HJRIM"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                assertEquals("HELSREF", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("ENG", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("historisk", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(periodeId.toString(), it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("HJRIM", it.kodeKlassifik)
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
                    fagsystem = Fagsystem.HISTORISK,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
                    personident = Personident("12345678910")
                ) {
                    periode(1.jun, 30.jun, 3000u, null)
                }
                assertEquals(expected, it)
            }

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
                stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                periodetype = Periodetype.EN_GANG,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 1.jun.atStartOfDay(),
                beslutterId = Navident("historisk"),
                saksbehandlerId = Navident("historisk"),
                fagsystem = Fagsystem.HISTORISK,
            ) {
                periode(1.jun, 30.jun, 3000u, null)
            }
        }
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.HISTORISK)) {
            setOf(uid1)
        }


        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(
                uid = uid1,
                sakId = sid.id,
                periodetype = Periodetype.EN_GANG,
                stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                behandlingId = bid.id,
                vedtakstidspunkt = 1.jun.atStartOfDay(),
            ) {
                Historisk.periode(1.jun, 30.jun, 3070u)
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.HISTORISK,
                linjer = listOf(
                    DetaljerLinje(bid.id, 1.jun, 30.jun, null, 3070u, "HJRIM"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                assertEquals("HELSREF", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("ENG", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("historisk", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(periodeId.toString(), it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("HJRIM", it.kodeKlassifik)
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
                    fagsystem = Fagsystem.HISTORISK,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
                    personident = Personident("12345678910")
                ) {
                    periode(1.jun, 30.jun, 3070u, null)
                }
                assertEquals(expected, it)
            }

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
                stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("historisk"),
                saksbehandlerId = Navident("historisk"),
                fagsystem = Fagsystem.HISTORISK,
                periodetype = Periodetype.EN_GANG
            ) {
                periode(2.jun, 13.jun, 100u, null)
            }
        }

        TestRuntime.topics.historisk.produce(transactionId1) {
            Historisk.utbetaling(
                uid = uid1,
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                emptyList()
            }
        }


        val mottatt = StatusReply(
            status = Status.MOTTATT,
            detaljer = Detaljer(
                Fagsystem.HISTORISK,
                listOf(DetaljerLinje(bid.id, 2.jun, 13.jun, null, 0u, "HJRIM"))
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
                    fagsystem = Fagsystem.HISTORISK,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = 14.jun.atStartOfDay(),
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                assertEquals("HELSREF", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("ENG", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("historisk", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertNull(oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
                    assertEquals(2.jun, it.datoStatusFom.toLocalDate())
                    assertNull(it.refDelytelseId)
                    assertEquals("ENDR", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("HJRIM", it.kodeKlassifik)
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
                    fagsystem = Fagsystem.HISTORISK,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = 14.jun.atStartOfDay(),
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
                    personident = Personident("12345678910"),
                    periodetype = Periodetype.EN_GANG,
                ) {
                    periode(2.jun, 13.jun, 100u, null)
                }
                assertEquals(expected, it)
            }
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
                stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                periodetype = Periodetype.EN_GANG,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 1.jun.atStartOfDay(),
                beslutterId = Navident("historisk"),
                saksbehandlerId = Navident("historisk"),
                fagsystem = Fagsystem.HISTORISK,
            ) {
                periode(1.jun, 3.jun, 210u, null)
            }
        }
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.HISTORISK)) {
            setOf(uid1)
        }


        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(
                uid = uid1,
                sakId = sid.id,
                periodetype = Periodetype.EN_GANG,
                stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                behandlingId = bid.id,
                vedtakstidspunkt = 1.jun.atStartOfDay(),
            ) {
                Historisk.periode(1.jun, 3.jun, 210u) +
                        Historisk.periode(6.jun, 6.jun, 70u)
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.HISTORISK,
                linjer = listOf(
                    DetaljerLinje(bid.id, 6.jun, 6.jun, null, 70u, "HJRIM"),
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
                    fagsystem = Fagsystem.HISTORISK,
                    lastPeriodeId = it.lastPeriodeId,
                    periodetype = Periodetype.EN_GANG,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
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
                assertEquals("HELSREF", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("ENG", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("historisk", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(periodeId.toString(), it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("HJRIM", it.kodeKlassifik)
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
                    fagsystem = Fagsystem.HISTORISK,
                    periodetype = Periodetype.EN_GANG,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("historisk"),
                    saksbehandlerId = Navident("historisk"),
                    personident = Personident("12345678910")
                ) {
                    periode(1.jun, 3.jun, 210u, null) +
                            periode(6.jun, 6.jun, 70u, null)
                }
                assertEquals(expected, it)
            }

    }

    @Test
    fun `simuler utbetaling blir ikke persistert`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(uid, sid.id, bid.id, dryrun = true) {
                Historisk.periode(
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    beløp = 1077u,
                )
            }
        }
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.HISTORISK)) {
            setOf(uid)
        }


        TestRuntime.topics.status.assertThat().isEmpty()
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.simulering.assertThat()
            .hasTotal(1)
            .has(transactionId)
            .with(transactionId) { simulering ->
                assertEquals("12345678910", simulering.request.oppdrag.oppdragGjelderId)
                assertEquals("NY", simulering.request.oppdrag.kodeEndring)
                assertEquals("HELSREF", simulering.request.oppdrag.kodeFagomraade)
                assertEquals(sid.id, simulering.request.oppdrag.fagsystemId)
                assertEquals("ENG", simulering.request.oppdrag.utbetFrekvens)
                assertEquals("12345678910", simulering.request.oppdrag.oppdragGjelderId)
                assertEquals("historisk", simulering.request.oppdrag.saksbehId)
                assertEquals(1, simulering.request.oppdrag.oppdragslinjes.size)
                assertNull(simulering.request.oppdrag.oppdragslinjes[0].refDelytelseId)
                simulering.request.oppdrag.oppdragslinjes[0].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals("HJRIM", it.kodeKlassifik)
                    assertEquals(1077, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
    }

    @Test
    @Disabled // støtter ikke sessionWindow lenger
    fun `simuler 4 utbetalinger blir til 1 xml`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = UtbetalingId(UUID.randomUUID())
        val uid2 = UtbetalingId(UUID.randomUUID())
        val uid3 = UtbetalingId(UUID.randomUUID())
        val uid4 = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(uid1, sid.id, bid.id, dryrun = true) {
                Historisk.periode(6.jun, 6.jun, 70u)
            }
        }
        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(uid2, sid.id, bid.id, dryrun = true) {
                Historisk.periode(7.jun, 7.jun, 70u)
            }
        }
        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(uid3, sid.id, bid.id, dryrun = true) {
                Historisk.periode(6.jun, 6.jun, 140u)
            }
        }
        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(uid4, sid.id, bid.id, dryrun = true) {
                Historisk.periode(7.jun, 7.jun, 140u)
            }
        }

        TestRuntime.topics.status.assertThat().isEmpty()
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.simulering.assertThat()
            .hasTotal(1)
            .has(transactionId)
            .with(transactionId) { simulering ->
                assertEquals("12345678910", simulering.request.oppdrag.oppdragGjelderId)
                assertEquals("NY", simulering.request.oppdrag.kodeEndring)
                assertEquals("HELSREF", simulering.request.oppdrag.kodeFagomraade)
                assertEquals(sid.id, simulering.request.oppdrag.fagsystemId)
                assertEquals("ENG", simulering.request.oppdrag.utbetFrekvens)
                assertEquals("12345678910", simulering.request.oppdrag.oppdragGjelderId)
                assertEquals("historisk", simulering.request.oppdrag.saksbehId)
                assertEquals(4, simulering.request.oppdrag.oppdragslinjes.size)
                assertNull(simulering.request.oppdrag.oppdragslinjes[0].refDelytelseId)
                simulering.request.oppdrag.oppdragslinjes[0].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals("HJRIM", it.kodeKlassifik)
                    assertEquals(70, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                simulering.request.oppdrag.oppdragslinjes[1].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals("HJRIM", it.kodeKlassifik)
                    assertEquals(70, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                simulering.request.oppdrag.oppdragslinjes[2].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals("HJRIM", it.kodeKlassifik)
                    assertEquals(140, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                simulering.request.oppdrag.oppdragslinjes[3].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals("HJRIM", it.kodeKlassifik)
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
                stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("historisk"),
                saksbehandlerId = Navident("historisk"),
                fagsystem = Fagsystem.HISTORISK,
            ) {
                periode(1.jan, 2.jan, 100u, null)
            }
        }

        TestRuntime.topics.historisk.produce(key) {
            Historisk.utbetaling(uid1, sid.id, bid.id, dryrun = true) {
                Historisk.periode(
                    fom = 1.jan,
                    tom = 2.jan,
                    beløp = 100u,
                )
            }
        }

        TestRuntime.topics.status.assertThat().has(key).with(key) { statusReply ->
            assertEquals(Status.OK, statusReply.status)
        }

        TestRuntime.topics.simulering.assertThat().hasNot(key)

    }


    @Test
    fun `endring av sakId på utbetaling med 2 identiske perioder`() {
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
                stønad = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
                periodetype = Periodetype.UKEDAG,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 6.jun.atStartOfDay(),
                beslutterId = Navident("historisk"),
                saksbehandlerId = Navident("historisk"),
                fagsystem = Fagsystem.HISTORISK,
            ) {
                periode(1.jun, 5.jun, 1000u, null) +
                        periode(8.jun, 10.jun, 500u, null)
            }
        }

        TestRuntime.topics.saker.produce(SakKey(sid1, Fagsystem.HISTORISK)) { setOf(uid) }


        TestRuntime.topics.historisk.produce(transactionId) {
            Historisk.utbetaling(
                uid = uid,
                sakId = sid2.id,
                behandlingId = bid2.id,
                vedtakstidspunkt = 7.jun.atStartOfDay(),
            ) {
                Historisk.periode(1.jun, 5.jun, 1000u) +
                        Historisk.periode(8.jun, 10.jun, 500u)
            }
        }


        val expectedError = StatusReply(
            status = Status.FEILET,
            detaljer = null,
            error=ApiError(400, "Kan ikke endre 'sakId'", DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_SAK_ID.doc)
        )

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, expectedError)
    }
}
