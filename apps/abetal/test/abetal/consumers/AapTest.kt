package abetal.consumers

import abetal.AAP_TX_GAP_MS
import abetal.Aap
import abetal.SakKey
import abetal.TestRuntime
import abetal.aug
import abetal.jul
import abetal.jun
import abetal.meldekort
import abetal.nextInt
import abetal.okt
import abetal.periode
import abetal.sep
import abetal.toLocalDate
import abetal.utbetaling
import java.util.UUID
import models.Action
import models.BehandlingId
import models.Detaljer
import models.DetaljerLinje
import models.Fagsystem
import models.Navident
import models.PeriodeId
import models.Personident
import models.SakId
import models.Status
import models.StatusReply
import models.StønadTypeAAP
import models.aapUId
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class AapTest {

    @Test
    fun `1 meldekort i 1 utbetalinger blir til 1 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = aapUId(sid.id, meldeperiode, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid.id) {
                meldekort(meldeperiode, 7.jun, 18.jun, 553u, 1077u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .with(transactionId) {
                StatusReply(Status.MOTTATT, Detaljer(ytelse = Fagsystem.AAP, linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun, 18.jun, 553u, 1077u, "AAPOR"),
                )))
            }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("kelvin", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("AAPOR", a.kodeKlassifik)
                    assertEquals(553, a.sats.toLong())
                    assertEquals(1077, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.datoVedtakFom, a.datoKlassifikFom)
                    assertEquals(b.datoVedtakFom, b.datoKlassifikFom)
                    assertEquals(a.delytelseId, b.refDelytelseId)
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
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(7.jun, 18.jun, 553u, 1077u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 1)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid), index = 0)
    }

    @Test
    fun `2 meldekort i 1 utbetalinger blir til 2 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid.id) {
                meldekort(meldeperiode1, 7.jun, 18.jun, 553u, 1077u)
                meldekort(meldeperiode2, 7.jul, 20.jul, 779u, 2377u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .with(transactionId) {
                StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.AAP, linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun, 18.jun, 553u, 1077u, "AAPOR"),
                    DetaljerLinje(bid.id, 7.jul, 20.jul, 779u, 2377u, "AAPOR"),
                )))
            }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId, size = 1)
            .with(transactionId, index = 0) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("kelvin", it.oppdrag110.saksbehId)
                assertEquals(2, it.oppdrag110.oppdragsLinje150s.size)
                it.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("AAPOR", it.kodeKlassifik)
                    assertEquals(553, it.sats.toLong())
                    assertEquals(1077, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                it.oppdrag110.oppdragsLinje150s[1].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("AAPOR", it.kodeKlassifik)
                    assertEquals(779, it.sats.toLong())
                    assertEquals(2377, it.vedtakssats157.vedtakssats.toLong())
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
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(7.jun, 18.jun, 553u, 1077u)
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
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(8.jul, 19.jul, 779u, 2377u) // fjern surrounding helg
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 2)
            .with(SakKey(sid, Fagsystem.AAP), index = 0) {
                assertEquals(it, setOf(uid1))
            }
            .with(SakKey(sid, Fagsystem.AAP), index = 1) {
                assertEquals(it, setOf(uid1, uid2))
            }
    }

    @Test
    fun `3 meldekort i 1 utbetalinger blir til 3 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val meldeperiode3 = "132462765"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid3 = aapUId(sid.id, meldeperiode3, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid.id) {
                meldekort(meldeperiode1, 7.jun, 20.jun, 553u, 1077u)
                meldekort(meldeperiode2, 8.jul, 19.jul, 779u, 2377u)
                meldekort(meldeperiode3, 7.aug, 20.aug, 3000u, 3133u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .with(transactionId) {
                StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.AAP, linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun, 18.jun, 1077u, 553u, "AAPOR"),
                    DetaljerLinje(bid.id, 7.jul, 20.jul, 2377u, 779u, "AAPOR"),
                    DetaljerLinje(bid.id, 7.aug, 20.aug, 3133u, 3000u, "AAPOR"),
                )))
            }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("kelvin", it.oppdrag110.saksbehId)
                assertEquals(3, it.oppdrag110.oppdragsLinje150s.size)

                it.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("AAPOR", it.kodeKlassifik)
                    assertEquals(553, it.sats.toLong())
                    assertEquals(1077, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }

                it.oppdrag110.oppdragsLinje150s[1].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("AAPOR", it.kodeKlassifik)
                    assertEquals(779, it.sats.toLong())
                    assertEquals(2377, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }

                it.oppdrag110.oppdragsLinje150s[2].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("AAPOR", it.kodeKlassifik)
                    assertEquals(3000, it.sats.toLong())
                    assertEquals(3133, it.vedtakssats157.vedtakssats.toLong())
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
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(7.jun, 20.jun, 553u, 1077u)
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
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(8.jul, 19.jul, 779u, 2377u)
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
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(7.aug, 20.aug, 3000u, 3133u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 3)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1, uid2), index = 1)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1, uid2, uid3), index = 2)
    }

    @Test
    fun `2 meldekort i 2 utbetalinger blir til 2 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid.id) {
                meldekort(meldeperiode1, 7.jun, 20.jun, 553u, 1077u)
            }
        }
        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid.id) {
                meldekort(meldeperiode2, 8.jul, 19.jul, 779u, 2377u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .with(transactionId) {
                StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.AAP, linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun, 20.jun, 553u, 1077u, "AAPOR"),
                    DetaljerLinje(bid.id, 8.jul, 19.jul, 779u, 2377u, "AAPOR"),
                )))
            }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) {
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("kelvin", it.oppdrag110.saksbehId)
                assertEquals(2, it.oppdrag110.oppdragsLinje150s.size)
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.id.toString())
            .has(uid2.id.toString())

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 2)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1))
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1, uid2), index = 1)
    }

    @Test
    fun `2 meldekort med 2 behandlinger for samme person blir til 2 utbetalinger med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid1 = BehandlingId("$nextInt")
        val bid2 = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid1.id) {
                meldekort(meldeperiode1, 7.jun, 20.jun, 553u, 1077u)
            }
        }
        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid2.id) {
                meldekort(meldeperiode1, 7.jun, 20.jun, 553u, 1077u)
                meldekort(meldeperiode2, 8.jul, 19.jul, 779u, 2377u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .with(transactionId) {
                StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.AAP, linjer = listOf(
                    DetaljerLinje(bid1.id, 7.jun, 18.jun, 553u, 1077u, "AAPOR"),
                    DetaljerLinje(bid2.id, 8.jul, 19.jul, 779u, 2377u, "AAPOR"),
                )))
            }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("AAP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(bid1.id, it.henvisning)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertEquals(bid2.id, it.henvisning)
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
            .has(uid2.id.toString())

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 2)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1))
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1, uid2), index = 1)
    }

    @Test
    fun `nytt meldekort på eksisterende sak`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId1 = UUID.randomUUID().toString()
        val transactionId2 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = transactionId1,
                stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("kelvin"),
                saksbehandlerId = Navident("kelvin"),
                fagsystem = Fagsystem.AAP,
            ) {
                periode(3.jun, 14.jun, 100u, 100u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 1)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1), index = 0)

        TestRuntime.topics.aap.produce(transactionId2) {
            Aap.utbetaling(sid.id, bid.id, vedtakstidspunkt = 14.jun.atStartOfDay()) {
                meldekort(meldeperiode1, 3.jun, 14.jun, 100u, 100u)
                meldekort(meldeperiode2, 17.jun, 28.jun, 200u, 200u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.status.assertThat()
            .has(transactionId2)
            .with(transactionId2) {
                StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.AAP, linjer = listOf(
                    DetaljerLinje(bid.id, 17.jun, 28.jun, 200u, 200u, "AAPOR"),
                )))
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId2)
            .with(transactionId2) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("kelvin", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("ENDR", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("AAPOR", a.kodeKlassifik)
                    assertEquals(200, a.sats.toLong())
                    assertEquals(200, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                    assertEquals(a.datoVedtakFom, a.datoKlassifikFom)
                    assertEquals(b.datoVedtakFom, b.datoKlassifikFom)
                }
            }
            .get(transactionId2)

        TestRuntime.topics.oppdrag.produce(transactionId2) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId2,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(17.jun, 28.jun, 200u, 200u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 1)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1, uid2), index = 0)
    }

    @Test
    fun `endre meldekort på eksisterende sak`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId1 = UUID.randomUUID().toString()
        val transactionId2 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = transactionId1,
                stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("kelvin"),
                saksbehandlerId = Navident("kelvin"),
                fagsystem = Fagsystem.AAP,
            ) {
                periode(3.jun, 14.jun, 100u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 1)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1), index = 0)

        TestRuntime.topics.aap.produce(transactionId2) {
            Aap.utbetaling(sid.id, bid.id, vedtakstidspunkt = 14.jun.atStartOfDay()) {
                meldekort(meldeperiode1, 3.jun, 14.jun, 80u, 100u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.status.assertThat()
            .has(transactionId2)
            .with(transactionId2) {
                StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.AAP, linjer = listOf(
                    DetaljerLinje(bid.id, 3.jun, 14.jun, 100u, 80u, "AAPOR"),
                )))
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
                    originalKey = transactionId2,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 14.jun, 80u, 100u)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId2)
            .with(transactionId2) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("AAP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("kelvin", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)

                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals("AAPOR", it.kodeKlassifik)
                    assertEquals(80, it.sats.toLong())
                    assertEquals(100, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId2)

        TestRuntime.topics.oppdrag.produce(transactionId2) {
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
                    originalKey = transactionId2,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 14.jun, 80u, 100u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 1)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1), index = 0)
    }

    @Test
    fun `opphør på meldekort`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId1 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = transactionId1,
                stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("kelvin"),
                saksbehandlerId = Navident("kelvin"),
                fagsystem = Fagsystem.AAP,
            ) {
                periode(2.jun, 13.jun, 100u, 100u)
            }
        }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.AAP)) {
            setOf(uid1)
        }

        TestRuntime.topics.aap.produce(transactionId1) {
            Aap.utbetaling(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.status.assertThat()
            .has(transactionId1)
            .with(transactionId1) {
                val expected = StatusReply(
                    status = Status.MOTTATT,
                    detaljer = Detaljer(Fagsystem.AAP, listOf(DetaljerLinje(bid.id, 2.jun, 13.jun, 100u, 0u, "AAPOR")))
                )
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId1)
            .with(transactionId1) {
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                it.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
                    assertEquals(2.jun, it.datoStatusFom.toLocalDate())
                    assertEquals("AAPOR", it.kodeKlassifik)
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
                assertEquals(Action.DELETE, it.action)
                assertEquals(uid1, it.uid)
            }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 2)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.AAP), setOf(), index = 1)

    }

    @Test
    fun `3 meldekort med ulike operasjoner`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = aapUId(sid.id, "132460781", StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, "232460781", StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid3 = aapUId(sid.id, "132462765", StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val pid1 = PeriodeId()
        val pid2 = PeriodeId()

        TestRuntime.topics.utbetalinger.produce(uid1.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = transactionId,
                stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                lastPeriodeId = pid1,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.sep.atStartOfDay(),
                beslutterId = Navident("kelvin"),
                saksbehandlerId = Navident("kelvin"),
                fagsystem = Fagsystem.AAP,
            ) {
                periode(2.sep, 13.sep, 500u, 500u)
            }
        }
        TestRuntime.topics.utbetalinger.produce(uid2.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid2,
                sakId = sid,
                behandlingId = bid,
                originalKey = transactionId,
                stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                førsteUtbetalingPåSak = false,
                lastPeriodeId = pid2,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.sep.atStartOfDay(),
                beslutterId = Navident("kelvin"),
                saksbehandlerId = Navident("kelvin"),
                fagsystem = Fagsystem.AAP,
            ) {
                periode(16.sep, 27.sep, 600u, 600u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 2)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1, uid2), index = 1)

        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid.id) {
                meldekort("132460781", 2.sep, 13.sep, 600u, 600u)
                meldekort("132462765", 30.sep, 10.okt, 600u, 600u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .with(transactionId) {
                StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.AAP, linjer = listOf(
                    DetaljerLinje(bid.id, 2.sep, 13.sep, 600u, 600u, "AAPOR"),
                    DetaljerLinje(bid.id, 30.sep, 10.okt, 600u, 600u, "AAPOR"),
                    DetaljerLinje(bid.id, 16.sep, 27.sep, 600u, 0u, "AAPOR"),
                )))
            }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("AAP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(3, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(pid1.toString(), it.refDelytelseId)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[2].let {
                    //assertEquals(pid2.toString(), it.refDelytelseId)
                    assertNull(it.refDelytelseId)
                    assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString()).with(uid1.toString()) { assertEquals(Action.UPDATE, it.action) }
            .has(uid2.toString()).with(uid2.toString()) { assertEquals(Action.DELETE, it.action) }
            .has(uid3.toString()).with(uid3.toString()) { assertEquals(Action.CREATE, it.action) }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 3)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1, uid3), index = 2)
    }

    @Test
    @Disabled
    fun `en meldeperiode som endres 3 ganger samtidig skal feile`() {
        val sid = SakId("$nextInt")
        val bid1 = BehandlingId("$nextInt")
        val bid2 = BehandlingId("$nextInt")
        val bid3 = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = aapUId(sid.id, meldeperiode, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid1.id) {
                Aap.meldekort(meldeperiode, 2.sep, 13.sep, 300u, 300u)
            }
        }
        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid2.id) {
                Aap.meldekort(meldeperiode, 2.sep, 13.sep, 300u, 300u)
                Aap.meldekort(meldeperiode, 16.sep, 27.sep, 300u, 300u)
            }
        }
        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid3.id) {
                Aap.meldekort(meldeperiode, 2.sep, 13.sep, 300u, 300u)
                Aap.meldekort(meldeperiode, 16.sep, 27.sep, 300u, 300u)
                Aap.meldekort(meldeperiode, 30.sep, 10.okt, 300u, 300u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.AAP,
                linjer = listOf(
                    DetaljerLinje(bid1.id, 2.sep, 13.sep, 300u, 300u, "AAPOR"),
                    DetaljerLinje(bid2.id, 16.sep, 27.sep, 300u, 300u, "AAPOR"),
                    DetaljerLinje(bid3.id, 30.sep, 10.okt, 300u, 300u, "AAPOR"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString(), 1)
            .with(uid.toString(), index = 0) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid3,
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 300u, 300u) +
                    periode(16.sep, 27.sep, 300u, 300u) +
                    periode(30.sep, 10.okt, 300u, 300u)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag -> 
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("AAP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("kelvin", oppdrag.oppdrag110.saksbehId)
                assertEquals(3, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertNull(oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid1.id, it.henvisning)
                    assertEquals("AAPOR", it.kodeKlassifik)
                    assertEquals(300, it.sats.toLong())
                    assertEquals(300, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertEquals(oppdrag.oppdrag110.oppdragsLinje150s[0].delytelseId, it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid2.id, it.henvisning)
                    assertEquals("AAPOR", it.kodeKlassifik)
                    assertEquals(300, it.sats.toLong())
                    assertEquals(300, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[2].let {
                    assertEquals(oppdrag.oppdrag110.oppdragsLinje150s[1].delytelseId, it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid3.id, it.henvisning)
                    assertEquals("AAPOR", it.kodeKlassifik)
                    assertEquals(300, it.sats.toLong())
                    assertEquals(300, it.vedtakssats157.vedtakssats.toLong())
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
            .has(uid.toString(), size = 1)
            .with(uid.toString(), index = 0) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid3,
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 300u, 300u) +
                    periode(16.sep, 27.sep, 300u, 300u) +
                    periode(30.sep, 10.okt, 300u, 300u)
                }
                assertEquals(expected, it)
            }


        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 1)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid), index = 0)
    }

    @Test
    @Disabled
    fun `3 saker blir til 3 utbetalinger med 3 oppdrag`() {
        val sid1 = SakId("$nextInt")
        val sid2 = SakId("$nextInt")
        val sid3 = SakId("$nextInt")
        val bid1 = BehandlingId("$nextInt")
        val bid2 = BehandlingId("$nextInt")
        val bid3 = BehandlingId("$nextInt")
        val transactionId = "12345678910"
        val meldeperiode1 = "100000000"
        val meldeperiode2 = "200000000"
        val meldeperiode3 = "300000000"
        val uid1 = aapUId(sid1.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid2.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid3 = aapUId(sid3.id, meldeperiode3, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid1.id, bid1.id) {
                Aap.meldekort(meldeperiode1, 2.sep, 13.sep, 300u, 300u)
            }
        }
        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid2.id, bid2.id) {
                Aap.meldekort(meldeperiode2, 16.sep, 27.sep, 300u, 300u)
            }
        }
        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid3.id, bid3.id) {
                Aap.meldekort(meldeperiode3, 30.sep, 10.okt, 300u, 300u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.status.assertThat().has(transactionId, 3)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val assertOppdrag = TestRuntime.topics.oppdrag.assertThat()
        assertOppdrag.has(transactionId, size = 3)
            .with(transactionId, index = 0) { assertEquals(sid1.id, it.oppdrag110.fagsystemId) }
            .with(transactionId, index = 1) { assertEquals(sid2.id, it.oppdrag110.fagsystemId) }
            .with(transactionId, index = 2) { assertEquals(sid3.id, it.oppdrag110.fagsystemId) }

        val oppdrag1 = assertOppdrag.get(transactionId, index = 0)
        val oppdrag2 = assertOppdrag.get(transactionId, index = 1)
        val oppdrag3 = assertOppdrag.get(transactionId, index = 2)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag1.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
        }
        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag2.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
        }
        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag3.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .has(uid2.toString())
            .has(uid3.toString())

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid1, Fagsystem.AAP))
            .has(SakKey(sid2, Fagsystem.AAP))
            .has(SakKey(sid3, Fagsystem.AAP))
    }

    @Test
    fun `simuler 1 meldekort i 1 utbetalinger`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()

        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid.id, dryrun = true) {
                meldekort("132460781", 7.jun, 18.jun, 553u, 1077u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.status.assertThat().isEmpty()
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.saker.assertThat().isEmpty()
        TestRuntime.topics.simulering.assertThat()
            .hasTotal(1)
            .has(transactionId)
            .with(transactionId) { simulering ->
                assertEquals("AAP", simulering.request.oppdrag.kodeFagomraade)
                assertEquals(sid.id, simulering.request.oppdrag.fagsystemId)
                assertEquals("kelvin", simulering.request.oppdrag.saksbehId)
                simulering.request.oppdrag.oppdragslinjes[0].let {
                    assertEquals("AAPOR", it.kodeKlassifik)
                    assertEquals(553, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
    }

    @Test
    fun `test 1 meldekort i 1 utbetalinger blir til 1 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = aapUId(sid.id, meldeperiode, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid.id) {
                meldekort(meldeperiode, 7.jun, 18.jun, 1077u, 553u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime((AAP_TX_GAP_MS + 1).milliseconds)

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .with(transactionId) {
                StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.AAP, linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun, 18.jun, 1077u, 553u, "AAPOR"),
                )))
            }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) {
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals("kelvin", it.oppdrag110.saksbehId)
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 1)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid), index = 0)
    }
}
