package abetal.consumers

import abetal.*
import abetal.TestRuntime
import com.fasterxml.jackson.module.kotlin.readValue
import libs.kafka.JsonSerde
import models.*
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class TpTest {

    @AfterEach
    fun `assert empty topic`() {
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
    }

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
        val transaction1 = UUID.randomUUID().toString()
        TestRuntime.topics.tp.produce(transaction1) { utbet }
        TestRuntime.topics.status.assertThat().has(transaction1)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid)
        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transaction1)
            .with(transaction1) { oppdrag ->
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
            }.get(transaction1)
        TestRuntime.topics.oppdrag.produce(transaction1) {
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
        val transaction2 = UUID.randomUUID().toString()
        TestRuntime.topics.tp.produce(transaction2) { dryrun }
        TestRuntime.topics.simulering.assertThat()
            .has(transaction2)
            .with(transaction2) { simulering ->
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
        val key = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val meldeperiode1 = UUID.randomUUID().toString()
        val uid1 = tpUId(sid.id, meldeperiode1, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)

        TestRuntime.topics.utbetalinger.produce("$uid1") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = key,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("tp"),
                saksbehandlerId = Navident("tp"),
                fagsystem = Fagsystem.TILTAKSPENGER,
            ) {
                periode(1.jan, 2.jan, 100u, null)
            }
        }

        TestRuntime.topics.tp.produce(key) {
            Tp.utbetaling(sid.id, bid.id, dryrun = true) {
                Tp.periode(
                    meldeperiode = meldeperiode1,
                    fom = 1.jan,
                    tom = 2.jan,
                    beløp = 100u,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    )
            }
        }

        TestRuntime.topics.status.assertThat().has(key).with(key) { statusReply ->
            assertEquals(Status.OK, statusReply.status)
        }

        TestRuntime.topics.simulering.assertThat().hasNot(key)

    }

    @Test
    fun `1 meldekort i 1 utbetalinger blir til 1 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = tpUId(sid.id, meldeperiode, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)

        TestRuntime.topics.tp.produce(transactionId) {
            Tp.utbetaling(sid.id, bid.id) {
                Tp.periode(
                    meldeperiode = "132460781",
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    beløp = 553u,
                )
            }
        }

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILTAKSPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, null, 553u, "TPTPAFT"),
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
                assertEquals("TILTPENG", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("tp", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("TPTPAFT", a.kodeKlassifik)
                    assertEquals(553, a.sats.toLong())
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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, null)
                }
                assertEquals(expected, it)
            }
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

        TestRuntime.topics.tp.produce(transactionId) {
            Tp.utbetaling(sid.id, bid.id) {
                Tp.periode(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    beløp = 553u,
                ) + Tp.periode(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    beløp = 779u,
                )
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILTAKSPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, null, 553u, "TPTPAFT"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, null, 779u, "TPTPAFT"),
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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, null)
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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, null)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId, size = 1)
            .with(transactionId, index = 0) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("TILTPENG", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("tp", oppdrag.oppdrag110.saksbehId)
                assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TPTPAFT", it.kodeKlassifik)
                    assertEquals(553, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TPTPAFT", it.kodeKlassifik)
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
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, null)
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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, null)
                }
                assertEquals(expected, it)
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

        TestRuntime.topics.tp.produce(transactionId) {
            Tp.utbetaling(sid.id, bid.id) {
                Tp.periode(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    beløp = 553u,
                ) + Tp.periode(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    beløp = 779u,
                ) + Tp.periode(
                    meldeperiode = meldeperiode3,
                    fom = LocalDate.of(2021, 8, 7),
                    tom = LocalDate.of(2021, 8, 20),
                    beløp = 3000u,
                )
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILTAKSPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 20.jun21, null, 553u, "TPTPAFT"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, null, 779u, "TPTPAFT"),
                    DetaljerLinje(bid.id, 7.aug21, 20.aug21, null, 3000u, "TPTPAFT"),
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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
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
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, null)
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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 8, 7), LocalDate.of(2021, 8, 20), 3000u, null)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("TILTPENG", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("tp", oppdrag.oppdrag110.saksbehId)
                assertEquals(3, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TPTPAFT", it.kodeKlassifik)
                    assertEquals(553, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TPTPAFT", it.kodeKlassifik)
                    assertEquals(779, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[2].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TPTPAFT", it.kodeKlassifik)
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
            .has(uid1.id.toString(), size = 1)
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
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
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, null)
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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 8, 7), LocalDate.of(2021, 8, 20), 3000u, null)
                }
                assertEquals(expected, it)
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

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = transactionId1,
                stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("tp"),
                saksbehandlerId = Navident("tp"),
                fagsystem = Fagsystem.TILTAKSPENGER,
            ) {
                periode(3.jun, 14.jun, 100u, null)
            }
        }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.TILTAKSPENGER)) {
            setOf(uid1)
        }


        TestRuntime.topics.tp.produce(transactionId2) {
            Tp.utbetaling(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                Tp.periode(
                    meldeperiode = meldeperiode1,
                    fom = 3.jun,
                    tom = 14.jun,
                    beløp = 100u,
                ) + Tp.periode(
                    meldeperiode = meldeperiode2,
                    fom = 17.jun,
                    tom = 28.jun,
                    beløp = 200u,
                )
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILTAKSPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 17.jun, 28.jun, null, 200u, "TPTPAFT"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId2)
            .has(transactionId2, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId2,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(17.jun, 28.jun, 200u, null)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId2)
            .with(transactionId2) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("TILTPENG", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("tp", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("ENDR", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("TPTPAFT", a.kodeKlassifik)
                    assertEquals(200, a.sats.toLong())
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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(17.jun, 28.jun, 200u, null)
                }
                assertEquals(expected, it)
            }

    }

    @Test
    fun `endre meldekort på eksisterende sak`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId1 = UUID.randomUUID().toString()
        val transactionId2 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val uid1 = tpUId(sid.id, meldeperiode1, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
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
        }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.TILTAKSPENGER)) {
            setOf(uid1)
        }


        TestRuntime.topics.tp.produce(transactionId2) {
            Tp.utbetaling(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                Tp.periode(
                    meldeperiode = meldeperiode1,
                    fom = 3.jun,
                    tom = 14.jun,
                    beløp = 80u,
                )
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILTAKSPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 3.jun, 14.jun, null, 80u, "TPTPAFT"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId2)
            .has(transactionId2, mottatt)

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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 14.jun, 80u, null)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId2)
            .with(transactionId2) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("TILTPENG", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("tp", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)

                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(periodeId.toString(), it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TPTPAFT", it.kodeKlassifik)
                    assertEquals(80, it.sats.toLong())
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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 14.jun, 80u, null)
                }
                assertEquals(expected, it)
            }

    }

    @Test
    fun `opphør på meldekort`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId1 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val uid1 = tpUId(sid.id, meldeperiode1, StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING)
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
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
                periode(2.jun, 13.jun, 100u, null)
            }
        }
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.TILTAKSPENGER)) {
            setOf(uid1)
        }

        TestRuntime.topics.tp.produce(transactionId1) {
            Tp.utbetaling(
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
                Fagsystem.TILTAKSPENGER, listOf(
                    DetaljerLinje(
                        bid.id, 2.jun, 13.jun, null, 0u,
                        "TPTPAFT"
                    )
                )
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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = 14.jun.atStartOfDay(),
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
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
                assertEquals("TILTPENG", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("tp", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertNull(oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)

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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = 14.jun.atStartOfDay(),
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.jun, 13.jun, 100u, null)
                }
                assertEquals(expected, it)
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
        val pid1 = PeriodeId()
        val pid2 = PeriodeId()

        TestRuntime.topics.utbetalinger.produce(uid1.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = transactionId,
                stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                lastPeriodeId = pid1,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.sep.atStartOfDay(),
                beslutterId = Navident("tp"),
                saksbehandlerId = Navident("tp"),
                fagsystem = Fagsystem.TILTAKSPENGER,
            ) {
                periode(2.sep, 13.sep, 500u, 500u) // 1-14
            }
        }
        TestRuntime.topics.utbetalinger.produce(uid2.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid2,
                sakId = sid,
                behandlingId = bid,
                originalKey = transactionId,
                stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                førsteUtbetalingPåSak = false,
                lastPeriodeId = pid2,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.sep.atStartOfDay(),
                beslutterId = Navident("tp"),
                saksbehandlerId = Navident("tp"),
                fagsystem = Fagsystem.TILTAKSPENGER,
            ) {
                periode(16.sep, 27.sep, 600u, null) // 15-28
            }
        }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.TILTAKSPENGER)) {
            setOf(uid1, uid2)
        }


        TestRuntime.topics.tp.produce(transactionId) {
            Tp.utbetaling(sid.id, bid.id) {
                Tp.periode("132460781", 2.sep, 13.sep, 600u) +
                        Tp.periode("132462765", 30.sep, 10.okt, 600u) // 29-12
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.TILTAKSPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 2.sep, 13.sep, null, 600u, "TPTPAFT"),
                    DetaljerLinje(bid.id, 30.sep, 10.okt, null, 600u, "TPTPAFT"),
                    DetaljerLinje(bid.id, 16.sep, 27.sep, null, 0u, "TPTPAFT"),
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
                    action = Action.UPDATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 600u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.DELETE,
                    uid = uid2,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(16.sep, 27.sep, 600u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid3.toString())
            .with(uid3.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(30.sep, 10.okt, 600u, null)
                }
                assertEquals(expected, it)
            }
        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("TILTPENG", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("tp", oppdrag.oppdrag110.saksbehId)
                assertEquals(3, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(pid1.toString(), it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TPTPAFT", it.kodeKlassifik)
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TPTPAFT", it.kodeKlassifik)
                    assertEquals(600, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
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

        TestRuntime.topics.oppdrag.produce(transactionId) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 600u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.DELETE,
                    uid = uid2,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(16.sep, 27.sep, 600u, null)
                }
                assertEquals(expected, it)
            }
            .has(uid3.toString())
            .with(uid3.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("tp"),
                    saksbehandlerId = Navident("tp"),
                    personident = Personident("12345678910")
                ) {
                    periode(30.sep, 10.okt, 600u, null)
                }
                assertEquals(expected, it)
            }

    }

    @Test
    fun `simuler 1 meldekort i 1 utbetalinger`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()

        TestRuntime.topics.tp.produce(transactionId) {
            Tp.utbetaling(sid.id, bid.id, dryrun = true) {
                Tp.periode(
                    meldeperiode = "132460781",
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    beløp = 553u,
                )
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
                assertEquals("TILTPENG", simulering.request.oppdrag.kodeFagomraade)
                assertEquals(sid.id, simulering.request.oppdrag.fagsystemId)
                assertEquals("MND", simulering.request.oppdrag.utbetFrekvens)
                assertEquals("12345678910", simulering.request.oppdrag.oppdragGjelderId)
                assertEquals("tp", simulering.request.oppdrag.saksbehId)
                assertEquals(1, simulering.request.oppdrag.oppdragslinjes.size)
                assertNull(simulering.request.oppdrag.oppdragslinjes[0].refDelytelseId)
                simulering.request.oppdrag.oppdragslinjes[0].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals("TPTPAFT", it.kodeKlassifik)
                    assertEquals(553, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
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
    }
}



