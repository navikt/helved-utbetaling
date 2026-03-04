package abetal.consumers

import abetal.*
import abetal.tp.linje
import com.fasterxml.jackson.module.kotlin.readValue
import libs.kafka.JsonSerde
import models.*
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class TpTest : ConsumerTestBase() {

    @Test
    fun `simulering av tp`() {
        val utbet = JsonSerde.jackson.readValue<TpUtbetaling>(
            """
            {
              "dryrun": false,
              "sakId": "rsid3",
              "behandlingId": "rbid1",
              "personident": "15898099536",
              "vedtakstidspunkt": "2025-08-27T10:00:00Z",
              "saksbehandler": "tp",
              "beslutter": "tp",
              "perioder": [
                {
                  "meldeperiode": "2025-08-01/2025-08-14",
                  "fom": "2025-08-01",
                  "tom": "2025-08-14",
                  "betalendeEnhet": "testEnhet",
                  "beløp": 1000,
                  "stønad": "ARBEIDSFORBEREDENDE_TRENING"
                }
              ]
            }""".trimIndent()
        )
        val uid = "db4aea1c-a343-54d1-504f-5ab063a5fc16"
        val transactionId = UUID.randomUUID().toString()
        TestRuntime.topics.tp.produce(transactionId) { utbet }
        TestRuntime.topics.status.assertThat().has(transactionId)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid)
        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("TILTPENG", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals("rsid3", oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("15898099536", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("tp", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertEquals("testEnhet", oppdrag.oppdrag110.oppdragsEnhet120s[0].enhet)
                assertNull(oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals("rbid1", it.henvisning)
                    assertEquals("TPTPAFT", it.kodeKlassifik)
                    assertEquals("DAG", it.typeSats)
                    assertEquals(1000, it.sats.toLong())
                    assertNull(it.vedtakssats157)
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }.get(transactionId)
        TestRuntime.topics.oppdrag.produce(transactionId, mapOf("uids" to uid)) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().has(uid)

        val dryrun = JsonSerde.jackson.readValue<TpUtbetaling>(
            """
            {
              "dryrun": true,
              "sakId": "rsid3",
              "behandlingId": "rbid1",
              "personident": "15898099536",
              "vedtakstidspunkt": "2025-08-27T10:00:00Z",
              "saksbehandler": "R123456",
              "beslutter": "R123456",
              "perioder": [
                {
                  "meldeperiode": "2025-08-01/2025-08-14",
                  "fom": "2025-08-01",
                  "tom": "2025-08-14",
                  "beløp": 900,
                  "stønad": "ARBEIDSFORBEREDENDE_TRENING"
                }
              ]
            }""".trimIndent()
        )
        val transactionId2 = UUID.randomUUID().toString()
        TestRuntime.topics.tp.produce(transactionId2) { dryrun }
        TestRuntime.topics.simulering.assertThat()
            .has(transactionId2)
            .with(transactionId2) { simulering ->
                assertEquals("ENDR", simulering.request.oppdrag.kodeEndring)
                assertEquals("TILTPENG", simulering.request.oppdrag.kodeFagomraade)
                assertEquals("rsid3", simulering.request.oppdrag.fagsystemId)
                assertEquals("MND", simulering.request.oppdrag.utbetFrekvens)
                assertEquals("15898099536", simulering.request.oppdrag.oppdragGjelderId)
                assertEquals("R123456", simulering.request.oppdrag.saksbehId)
                assertEquals(1, simulering.request.oppdrag.oppdragslinjes.size)
                simulering.request.oppdrag.oppdragslinjes[0].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertNull(it.kodeStatusLinje)
                    assertNull(it.datoStatusFom)
                    assertEquals("R123456", it.saksbehId)
                    assertEquals(900, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
    }

    @Test
    fun `simulering uten endring`() {
        val transactionId = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val meldeperiode = UUID.randomUUID().toString()
        val uid = tpUId(sid.id, meldeperiode, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)

        val existingUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("tp"),
            saksbehandlerId = Navident("tp"),
            fagsystem = Fagsystem.TILTAKSPENGER,
        ) {
            periode(1.jan, 2.jan, 100u, null)
        }
        TestRuntime.topics.utbetalinger.produce(uid.toString(), existingUtbetaling)

        TestRuntime.topics.tp.produce(transactionId) {
            Tp.utbetaling(sid.id, bid.id, dryrun = true) {
                Tp.periode(meldeperiode, 1.jan, 2.jan, 100u, stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)
            }
        }

        TestRuntime.topics.status.assertThat().has(transactionId).with(transactionId) { statusReply ->
            assertEquals(Status.OK, statusReply.status)
        }

        TestRuntime.topics.simulering.assertThat().hasNot(transactionId)
    }

    @Test
    fun `2 meldekort i 1 utbetalinger blir til 2 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = tpUId(sid.id, meldeperiode1, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)
        val uid2 = tpUId(sid.id, meldeperiode2, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)

        val expected1 = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            originalKey = transactionId,
            sakId = sid,
            behandlingId = bid,
            fagsystem = Fagsystem.TILTAKSPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("tp"),
            saksbehandlerId = Navident("tp"),
            personident = Personident("12345678910")
        ) {
            periode(7.jun21, 18.jun21, 553u, null)
        }

        val expected2 = expected1.copy(
            uid = uid2,
            perioder = listOf(periode(7.jul21, 20.jul21, 779u, null))
        )

        TestRuntime.topics.tp.produce(transactionId) {
            Tp.utbetaling(sid.id, bid.id) {
                Tp.periode(meldeperiode1, 7.jun21, 18.jun21, 553u) +
                Tp.periode(meldeperiode2, 7.jul21, 20.jul21, 779u)
            }
        }

        TestRuntime.topics.status.assertThat().has(transactionId) {
            Tp.mottatt {
                linje(bid, 7.jun21, 18.jun21, 553u)
                linje(bid, 7.jul21, 20.jul21, 779u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId, size = 1)
            .with(transactionId, index = 0) { oppdrag ->
                oppdrag.assertBasics("NY", "TILTPENG", sid.id, expectedLines = 2)
                assertEquals("tp", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "TPTPAFT",
                    sats = 553,
                    refDelytelseId = null
                )
                oppdrag.oppdrag110.oppdragsLinje150s[1].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "TPTPAFT",
                    sats = 779
                )
            }
            .get(transactionId)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertEquals(expected1.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt), it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                assertEquals(expected2.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt), it)
            }

        kvitterOk(transactionId, oppdrag, listOf(uid1, uid2))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertUtbetaling(expected1, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                assertUtbetaling(expected2, it)
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
        val uid1 = tpUId(sid.id, meldeperiode1, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)
        val uid2 = tpUId(sid.id, meldeperiode2, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)
        val uid3 = tpUId(sid.id, meldeperiode3, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)

        val expected1 = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            originalKey = transactionId,
            sakId = sid,
            behandlingId = bid,
            fagsystem = Fagsystem.TILTAKSPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("tp"),
            saksbehandlerId = Navident("tp"),
            personident = Personident("12345678910")
        ) {
            periode(7.jun21, 20.jun21, 553u, null)
        }

        val expected2 = expected1.copy(
            uid = uid2,
            perioder = listOf(periode(7.jul21, 20.jul21, 779u, null))
        )

        val expected3 = expected1.copy(
            uid = uid3,
            perioder = listOf(periode(7.aug21, 20.aug21, 3000u, null))
        )

        TestRuntime.topics.tp.produce(transactionId) {
            Tp.utbetaling(sid.id, bid.id) {
                Tp.periode(meldeperiode1, 7.jun21, 20.jun21, 553u) +
                Tp.periode(meldeperiode2, 7.jul21, 20.jul21, 779u) +
                Tp.periode(meldeperiode3, 7.aug21, 20.aug21, 3000u)
            }
        }

        TestRuntime.topics.status.assertThat().has(transactionId) {
            Tp.mottatt {
                linje(bid, 7.jun21, 20.jun21, 553u)
                linje(bid, 7.jul21, 20.jul21, 779u)
                linje(bid, 7.aug21, 20.aug21, 3000u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.assertBasics("NY", "TILTPENG", sid.id, expectedLines = 3)
                assertEquals("tp", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "TPTPAFT",
                    sats = 553,
                    refDelytelseId = null
                )
                oppdrag.oppdrag110.oppdragsLinje150s[1].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "TPTPAFT",
                    sats = 779,
                    refDelytelseId = null
                )
                oppdrag.oppdrag110.oppdragsLinje150s[2].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "TPTPAFT",
                    sats = 3000,
                    refDelytelseId = null
                )
            }
            .get(transactionId)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertEquals(expected1.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt), it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                assertEquals(expected2.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt), it)
            }
            .has(uid3.toString())
            .with(uid3.toString()) {
                assertEquals(expected3.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt), it)
            }

        kvitterOk(transactionId, oppdrag, listOf(uid1, uid2, uid3))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertUtbetaling(expected1, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                assertUtbetaling(expected2, it)
            }
            .has(uid3.toString())
            .with(uid3.toString()) {
                assertUtbetaling(expected3, it)
            }
    }

    @Test
    fun `nytt meldekort på eksisterende sak`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId1 = UUID.randomUUID().toString()
        val transactionId2 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = tpUId(sid.id, meldeperiode1, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)
        val uid2 = tpUId(sid.id, meldeperiode2, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)

        val existingUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId1,
            stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("tp"),
            saksbehandlerId = Navident("tp"),
            fagsystem = Fagsystem.TILTAKSPENGER,
        ) {
            periode(3.jun, 14.jun, 100u, null)
        }

        val expected = utbetaling(
            action = Action.CREATE,
            uid = uid2,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId2,
            førsteUtbetalingPåSak = false,
            fagsystem = Fagsystem.TILTAKSPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("tp"),
            saksbehandlerId = Navident("tp"),
            personident = Personident("12345678910")
        ) {
            periode(17.jun, 28.jun, 200u, null)
        }

        TestRuntime.topics.utbetalinger.produce(uid1.toString(), existingUtbetaling)
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.TILTAKSPENGER), setOf(uid1))
        TestRuntime.topics.tp.produce(transactionId2) {
            Tp.utbetaling(sid.id, bid.id, vedtakstidspunkt = 14.jun.atStartOfDay()) {
                Tp.periode(meldeperiode1, 3.jun, 14.jun, 100u) +
                Tp.periode(meldeperiode2, 17.jun, 28.jun, 200u)
            }
        }

        TestRuntime.topics.status.assertThat().has(transactionId2) {
            Tp.mottatt {
                linje(bid, 17.jun, 28.jun, 200u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId2)
            .with(transactionId2) { oppdrag ->
                oppdrag.assertBasics("ENDR", "TILTPENG", sid.id, expectedLines = 1)
                assertEquals("tp", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "TPTPAFT",
                    sats = 200,
                    refDelytelseId = null
                )
            }
            .get(transactionId2)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid2.toString())
            .with(uid2.toString()) {
                assertUtbetaling(expected, it)
            }

        kvitterOk(transactionId2, oppdrag, listOf(uid2))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid2.toString())
            .with(uid2.toString()) {
                assertUtbetaling(expected, it)
            }
    }

    @Test
    fun `endre meldekort på eksisterende sak`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId1 = UUID.randomUUID().toString()
        val transactionId2 = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = tpUId(sid.id, meldeperiode, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)
        val periodeId = PeriodeId()

        val utbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId1,
            stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
            lastPeriodeId = periodeId,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("tp"),
            saksbehandlerId = Navident("tp"),
            fagsystem = Fagsystem.TILTAKSPENGER,
        ) {
            periode(3.jun, 14.jun, 100u)
        }

        val expected = utbetaling.copy(
            action = Action.UPDATE,
            originalKey = transactionId2,
            førsteUtbetalingPåSak = false,
            lastPeriodeId = PeriodeId(),
            perioder = listOf(periode(3.jun, 14.jun, 80u, null))
        )

        TestRuntime.topics.utbetalinger.produce(uid.toString(), utbetaling)
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.TILTAKSPENGER), setOf(uid))
        TestRuntime.topics.tp.produce(transactionId2) {
            Tp.utbetaling(sid.id, bid.id, vedtakstidspunkt = 14.jun.atStartOfDay()) {
                Tp.periode(meldeperiode, 3.jun, 14.jun, 80u)
            }
        }

        TestRuntime.topics.status.assertThat().has(transactionId2) {
            Tp.mottatt {
                linje(bid, 3.jun, 14.jun, 80u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId2)
            .with(transactionId2) { oppdrag ->
                oppdrag.assertBasics("ENDR", "TILTPENG", sid.id, expectedLines = 1)
                assertEquals("tp", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "TPTPAFT",
                    sats = 80,
                    refDelytelseId = periodeId.toString()
                )
            }
            .get(transactionId2)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertUtbetaling(expected, it)
            }

        kvitterOk(transactionId2, oppdrag, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertUtbetaling(expected, it)
            }
    }

    @Test
    fun `opphør på meldekort`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = tpUId(sid.id, meldeperiode, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)
        val periodeId = PeriodeId()

        val existingUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId,
            stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
            lastPeriodeId = periodeId,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("tp"),
            saksbehandlerId = Navident("tp"),
            fagsystem = Fagsystem.TILTAKSPENGER,
        ) {
            periode(2.jun, 13.jun, 100u, null)
        }

        val expected = existingUtbetaling.copy(
            action = Action.DELETE,
            vedtakstidspunkt = 14.jun.atStartOfDay()
        )

        TestRuntime.topics.utbetalinger.produce(uid.toString(), existingUtbetaling)
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.TILTAKSPENGER), setOf(uid))

        TestRuntime.topics.tp.produce(transactionId) {
            Tp.utbetaling(sid.id, bid.id, vedtakstidspunkt = 14.jun.atStartOfDay()) {
                emptyList()
            }
        }

        TestRuntime.topics.status.assertThat().has(transactionId) {
            Tp.mottatt {
                linje(bid, 2.jun, 13.jun, 0u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.assertBasics("ENDR", "TILTPENG", sid.id, expectedLines = 1)
                assertEquals("tp", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
                    assertEquals(2.jun, it.datoStatusFom.toLocalDate())
                    assertNull(it.refDelytelseId)
                    assertEquals("ENDR", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TPTPAFT", it.kodeKlassifik)
                    assertEquals(100, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(expected.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt), it)
            }

        kvitterOk(transactionId, oppdrag, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertUtbetaling(expected, it)
            }
    }

    @Test
    fun `3 meldekort med ulike operasjoner`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = tpUId(sid.id, "132460781", StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)
        val uid2 = tpUId(sid.id, "232460781", StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)
        val uid3 = tpUId(sid.id, "132462765", StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)
        val periodeId1 = PeriodeId()
        val periodeId2 = PeriodeId()

        val existingUtbetaling1 = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId,
            stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
            lastPeriodeId = periodeId1,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 14.sep.atStartOfDay(),
            beslutterId = Navident("tp"),
            saksbehandlerId = Navident("tp"),
            fagsystem = Fagsystem.TILTAKSPENGER,
        ) {
            periode(2.sep, 13.sep, 500u, 500u)
        }

        val existingUtbetaling2 = utbetaling(
            action = Action.CREATE,
            uid = uid2,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId,
            stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
            førsteUtbetalingPåSak = false,
            lastPeriodeId = periodeId2,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 14.sep.atStartOfDay(),
            beslutterId = Navident("tp"),
            saksbehandlerId = Navident("tp"),
            fagsystem = Fagsystem.TILTAKSPENGER,
        ) {
            periode(16.sep, 27.sep, 600u, null)
        }

        val expected1 = existingUtbetaling1.copy(
            action = Action.UPDATE,
            førsteUtbetalingPåSak = false,
            lastPeriodeId = PeriodeId(),
            perioder = listOf(periode(2.sep, 13.sep, 600u, null))
        )

        val expected2 = existingUtbetaling2.copy(
            action = Action.DELETE,
            lastPeriodeId = PeriodeId()
        )

        val expected3 = utbetaling(
            action = Action.CREATE,
            uid = uid3,
            originalKey = transactionId,
            sakId = sid,
            behandlingId = bid,
            fagsystem = Fagsystem.TILTAKSPENGER,
            lastPeriodeId = PeriodeId(),
            førsteUtbetalingPåSak = false,
            stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
            vedtakstidspunkt = 14.sep.atStartOfDay(),
            beslutterId = Navident("tp"),
            saksbehandlerId = Navident("tp"),
            personident = Personident("12345678910")
        ) {
            periode(30.sep, 10.okt, 600u, null)
        }

        TestRuntime.topics.utbetalinger.produce(uid1.toString(), existingUtbetaling1)
        TestRuntime.topics.utbetalinger.produce(uid2.toString(), existingUtbetaling2)
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.TILTAKSPENGER), setOf(uid1, uid2))

        TestRuntime.topics.tp.produce(transactionId) {
            Tp.utbetaling(sid.id, bid.id) {
                Tp.periode("132460781", 2.sep, 13.sep, 600u) +
                Tp.periode("132462765", 30.sep, 10.okt, 600u)
            }
        }

        TestRuntime.topics.status.assertThat().has(transactionId) {
            Tp.mottatt {
                linje(bid, 2.sep, 13.sep, 600u)
                linje(bid, 30.sep, 10.okt, 600u)
                linje(bid, 16.sep, 27.sep, 0u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.assertBasics("ENDR", "TILTPENG", sid.id, expectedLines = 3)
                assertEquals("tp", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(periodeId1.toString(), it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TPTPAFT", it.kodeKlassifik)
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "TPTPAFT",
                    sats = 600,
                    refDelytelseId = null
                )
                oppdrag.oppdrag110.oppdragsLinje150s[2].let {
                    assertNull(it.refDelytelseId)
                    assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
                    assertEquals("ENDR", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TPTPAFT", it.kodeKlassifik)
                    assertEquals(600, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertEquals(expected1.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt), it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                assertEquals(expected2.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt), it)
            }
            .has(uid3.toString())
            .with(uid3.toString()) {
                assertEquals(expected3.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt), it)
            }

        kvitterOk(transactionId, oppdrag, listOf(uid1, uid2, uid3))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertUtbetaling(expected1, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                assertUtbetaling(expected2, it)
            }
            .has(uid3.toString())
            .with(uid3.toString()) {
                assertUtbetaling(expected3, it)
            }
    }

    @Test
    fun `betalendeEnhet mapping skal gi riktig oppdragsEnhet120`() {
        val json = """
            {
              "dryrun": false,
              "sakId": "HV2511260231",
              "behandlingId": "1",
              "personident": "30436818684",
              "saksbehandler": "s-kafka",
              "beslutter": "b-kafka",
              "vedtakstidspunkt": "2025-09-29T14:00:00.000000Z",
              "perioder": [
                  {
                  "meldeperiode": "20250630-20250711",
                  "fom": "2025-06-30",
                  "tom": "2025-06-30",
                  "beløp": 1000,
                  "utbetaltBeløp": 1000,
                  "betalendeEnhet": "1234",
                  "stønad": "GRUPPE_AMO"
                }
              ]
            }
            """.trimIndent()

        val utbet = JsonSerde.jackson.readValue<TpUtbetaling>(json)
        val transactionId = UUID.randomUUID().toString()

        TestRuntime.topics.tp.produce(transactionId) { utbet }

        TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                val enheter = oppdrag.oppdrag110.oppdragsEnhet120s
                assertEquals(2, enheter.size)

                val bos = enheter.find { it.typeEnhet == "BOS" }
                val beh = enheter.find { it.typeEnhet == "BEH" }
                assertEquals("1234", bos?.enhet)
                assertEquals("8020", beh?.enhet)
            }

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, StatusReply(Status.MOTTATT, Detaljer(Fagsystem.TILTAKSPENGER, listOf(
                DetaljerLinje("1", 30.jun25, 30.jun25, null, 1000u, "TPTPGRAMO"),
            ))))
    }

    @Test
    fun `meldekort med barnetillegg gir riktig klassekode`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = tpUId(sid.id, meldeperiode, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING_BARN)

        TestRuntime.topics.tp.produce(transactionId) {
            Tp.utbetaling(sid.id, bid.id) {
                Tp.periode(meldeperiode, 7.jun21, 18.jun21, 553u, stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, barnetillegg = true)
            }
        }

        TestRuntime.topics.status.assertThat().has(transactionId) {
            Tp.mottatt {
                linje(bid, 7.jun21, 18.jun21, 553u, klassekode = "TPBTAF")
            }
        }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) {
                assertEquals("TPBTAF", it.oppdrag110.oppdragsLinje150s[0].kodeKlassifik)
            }
            .get(transactionId)

        kvitterOk(transactionId, oppdrag, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING_BARN, it.stønad)
            }
    }
}
