package abetal.consumers

import abetal.*
import com.fasterxml.jackson.module.kotlin.readValue
import libs.kafka.JsonSerde
import models.*
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class DpTest {

    @AfterEach
    fun `assert empty topic`() {
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.simulering.assertThat()
        TestRuntime.topics.status.assertThat().isEmpty()
        TestRuntime.topics.saker.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat()
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
    }

    @Test
    fun `simulering av dp`() {
        val utbet = JsonSerde.jackson.readValue<DpUtbetaling>("""
            {
              "dryrun": false,
              "sakId": "rsid3",
              "behandlingId": "rbid1",
              "ident": "15898099536",
              "utbetalinger": [
                {
                  "meldeperiode": "2025-08-01/2025-08-14",
                  "dato": "2025-08-01",
                  "sats": 1000,
                  "utbetaltBeløp": 1000,
                  "utbetalingstype": "Dagpenger"
                },
                {
                  "meldeperiode": "2025-08-01/2025-08-14",
                  "dato": "2025-08-02",
                  "sats": 1000,
                  "utbetaltBeløp": 1000,
                  "utbetalingstype": "Dagpenger"
                },
                {
                  "meldeperiode": "2025-08-01/2025-08-14",
                  "dato": "2025-08-03",
                  "sats": 1000,
                  "utbetaltBeløp": 1000,
                  "utbetalingstype": "Dagpenger"
                }
              ],
              "vedtakstidspunktet": "2025-08-27T10:00:00Z",
              "saksbehandler": "dagpenger",
              "beslutter": "dagpenger"
            }""".trimIndent())
        val uid = "26c8ad95-1731-e800-abd5-ba92ec6aad86"
        val transaction1 = UUID.randomUUID().toString()
        TestRuntime.topics.dp.produce(transaction1) { utbet }
        TestRuntime.topics.status.assertThat().has(transaction1)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid)
            .hasHeader(uid, "hash_key")
        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transaction1)
            .with(transaction1) { oppdrag ->
            assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
            assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
            assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
            assertEquals("rsid3", oppdrag.oppdrag110.fagsystemId)
            assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
            assertEquals("15898099536", oppdrag.oppdrag110.oppdragGjelderId)
            assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
            assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
            assertNull(oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
            oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                assertNull(it.refDelytelseId)
                assertEquals("NY", it.kodeEndringLinje)
                assertEquals("rbid1", it.henvisning)
                assertEquals("DAGPENGER", it.kodeKlassifik)
                assertEquals("DAG", it.typeSats)
                assertEquals(1000, it.sats.toLong())
                assertEquals(1000, it.vedtakssats157.vedtakssats.toLong())
                assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
            }
        }.get(transaction1)
        TestRuntime.topics.oppdrag.produce(transaction1, mapOf("uids" to uid)) { 
            oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
        }
        TestRuntime.topics.utbetalinger.assertThat().has(uid)

        val dryrun = JsonSerde.jackson.readValue<DpUtbetaling>("""
            {
              "dryrun": true,
              "sakId": "rsid3",
              "behandlingId": "rbid2",
              "ident": "15898099536",
              "utbetalinger": [
                {
                  "meldeperiode": "2025-08-01/2025-08-14",
                  "dato": "2025-08-01",
                  "sats": 1000,
                  "utbetaltBeløp": 900,
                  "utbetalingstype": "Dagpenger"
                },
                {
                  "meldeperiode": "2025-08-01/2025-08-14",
                  "dato": "2025-08-02",
                  "sats": 1000,
                  "utbetaltBeløp": 900,
                  "utbetalingstype": "Dagpenger"
                },
                {
                  "meldeperiode": "2025-08-01/2025-08-14",
                  "dato": "2025-08-03",
                  "sats": 1000,
                  "utbetaltBeløp": 900,
                  "utbetalingstype": "Dagpenger"
                }
              ],
              "vedtakstidspunktet": "2025-08-27T10:00:00Z",
              "saksbehandler": "R123456",
              "beslutter": "R123456"
            }""".trimIndent())
        val transaction2 = UUID.randomUUID().toString()
        TestRuntime.topics.dp.produce(transaction2) { dryrun }
        TestRuntime.topics.simulering.assertThat()
            .has(transaction2)
            .with(transaction2) { simulering ->
            assertEquals("ENDR", simulering.request.oppdrag.kodeEndring)
            assertEquals("DP", simulering.request.oppdrag.kodeFagomraade)
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
    fun `tombstone one utbetaling then resend meldeperioder`() {
        val sid = "AZps2"
        val abid = "AZrupr"
        val auid = dpUId(sid, "132733037", StønadTypeDagpenger.DAGPENGER)
        val atid = "a19aeea6-bf92-7a7a-a2c0-d22dfde2c60c"

        val buid = dpUId(sid, "132733485", StønadTypeDagpenger.DAGPENGER)
        val bbid = "AZruzh"
        val btid = "b19aeece-1848-79bb-bda5-369b7a16ec67"

        val cbid = "AZsfGC"
        val cuid = dpUId(sid, "132735021", StønadTypeDagpenger.DAGPENGER)
        val ctid = "c19b1f18-2199-7395-b8f3-82c497ce2941"

        val a = JsonSerde.jackson.readValue<DpUtbetaling>("""{"sakId":"$sid","behandlingId":"$abid","ident":"12345678910","vedtakstidspunktet":"2025-12-05T14:57:21.107354","utbetalinger":[{"meldeperiode":"132733037","dato":"2025-11-10","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-11","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-12","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-13","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-14","sats":911,"utbetaltBeløp":366,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"}]}""")
        TestRuntime.topics.dp.produce(atid) { a }
        TestRuntime.topics.status.assertThat()
            .has(key = atid, index = 0, size = 1, value = StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.DAGPENGER, linjer = listOf(
                DetaljerLinje(abid, LocalDate.of(2025, 11, 10), LocalDate.of(2025, 11, 13), 911u, 364u, "DAGPENGER"),
                DetaljerLinje(abid, LocalDate.of(2025, 11, 14), LocalDate.of(2025, 11, 14), 911u, 366u, "DAGPENGER"),
            ))))

        val aoppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(atid)
            .with(atid) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(abid, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(364, it.sats.toLong())
                    assertEquals(911, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(abid, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(366, it.sats.toLong())
                    assertEquals(911, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(atid)
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(auid.toString())
            .hasHeader(auid.toString(), "hash_key" to hashOppdrag(aoppdrag).toString())
            .with(auid.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = auid,
                    originalKey = atid,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(abid),
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2025, 11, 10), LocalDate.of(2025, 11, 13), 364u, 911u) +
                    periode(LocalDate.of(2025, 11, 14), LocalDate.of(2025, 11, 14), 366u, 911u)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.oppdrag.produce(atid, mapOf("uids" to "$auid")) {
            aoppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().has(auid.toString())
        TestRuntime.topics.saker.produce(SakKey(SakId(sid), Fagsystem.DAGPENGER)) {
            setOf(auid)
        }

        val b = JsonSerde.jackson.readValue<DpUtbetaling>("""{"sakId":"$sid","behandlingId":"$bbid","ident":"12345678910","vedtakstidspunktet":"2025-12-08T07:09:38.510701","utbetalinger":[{"meldeperiode":"132733037","dato":"2025-11-10","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-11","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-12","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-13","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-14","sats":911,"utbetaltBeløp":366,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-17","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-18","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-19","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-20","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-21","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-24","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-25","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-26","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-27","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-28","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"}]}""")
        TestRuntime.topics.dp.produce(btid) { b }
        TestRuntime.topics.status.assertThat()
            .has(key = btid, index = 0, size = 1, value = StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.DAGPENGER, linjer = listOf(
                DetaljerLinje(bbid, LocalDate.of(2025, 11, 17), LocalDate.of(2025, 11, 28), 911u, 911u, "DAGPENGER"),
            ))))
        val boppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(btid)
            .with(btid) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bbid, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(911, it.sats.toLong())
                    assertEquals(911, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(btid)
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(buid.toString())
            .hasHeader(buid.toString(), "hash_key" to hashOppdrag(boppdrag).toString())
            .with(buid.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = buid,
                    originalKey = btid,
                    sakId = SakId(sid),
                    førsteUtbetalingPåSak = false,
                    behandlingId = BehandlingId(bbid),
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2025, 11, 17), LocalDate.of(2025, 11, 28), 911u, 911u)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.oppdrag.produce(btid, mapOf("uids" to "$buid")) {
            boppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().has(buid.toString())
        TestRuntime.topics.saker.produce(SakKey(SakId(sid), Fagsystem.DAGPENGER)) {
            setOf(auid, buid)
        }

        val c = JsonSerde.jackson.readValue<DpUtbetaling>("""{"sakId":"$sid","behandlingId":"$cbid","ident":"12345678910","vedtakstidspunktet":"2025-12-15T09:26:40.032951","utbetalinger":[{"meldeperiode":"132733037","dato":"2025-11-10","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-11","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-12","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-13","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-14","sats":911,"utbetaltBeløp":366,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-17","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-18","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-19","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-20","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-21","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-24","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-25","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-26","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-27","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-28","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-01","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-02","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-03","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-04","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-05","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-08","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-09","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-10","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-11","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-12","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"}]}""")
        TestRuntime.topics.dp.produce(ctid) { c }
        TestRuntime.topics.status.assertThat()
            .has(key = ctid, index = 0, size = 1, value = StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.DAGPENGER, linjer = listOf(
                DetaljerLinje(cbid, LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 12), 911u, 911u, "DAGPENGER"),
            ))))
        val coppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(ctid)
            .with(ctid) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(cbid, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(911, it.sats.toLong())
                    assertEquals(911, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(ctid)
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(cuid.toString())
            .hasHeader(cuid.toString(), "hash_key" to hashOppdrag(coppdrag).toString())
            .with(cuid.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = cuid,
                    originalKey = ctid,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(cbid),
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 12), 911u, 911u)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.oppdrag.produce(ctid, mapOf("uids" to "$cuid")) {
            coppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().has(cuid.toString())
        TestRuntime.topics.saker.produce(SakKey(SakId(sid), Fagsystem.DAGPENGER)) {
            setOf(auid, buid, cuid)
        }

        val dtid = "d19b1f18-2199-7395-b8f3-82c497ce2941"
        TestRuntime.topics.dp.produce(dtid) { c }
        TestRuntime.topics.status.assertThat()
            .has(key = dtid, index = 0, size = 1, value = StatusReply(Status.OK, null))
        TestRuntime.topics.utbetalinger.tombstone(cuid.toString())

        val etid = "e19b1f18-2199-7395-b8f3-82c497ce2941"
        TestRuntime.topics.dp.produce(etid) { c }
        TestRuntime.topics.status.assertThat()
            .has(key = etid, index = 0, size = 1, value = StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.DAGPENGER, linjer = listOf(
                DetaljerLinje(cbid, LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 12), 911u, 911u, "DAGPENGER"),
            ))))
        val eoppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(etid)
            .with(etid) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(cbid, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(911, it.sats.toLong())
                    assertEquals(911, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(etid)
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(cuid.toString())
            .hasHeader(cuid.toString(), "hash_key" to hashOppdrag(eoppdrag).toString())
            .with(cuid.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = cuid,
                    originalKey = etid,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(cbid),
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 12), 911u, 911u)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.oppdrag.produce(etid, mapOf("uids" to "$cuid")) {
            eoppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().has(cuid.toString())
        TestRuntime.topics.pendingUtbetalinger.assertThat().isEmpty()
    }

    @Test
    fun `AZp88bgreqOOlzsEuxzpzw==`() {
        val sid = "AZps"
        val abid = "AZrupr"
        val auid = dpUId(sid, "132733037", StønadTypeDagpenger.DAGPENGER)
        val atid = "019aeea6-bf92-7a7a-a2c0-d22dfde2c60c"

        val buid = dpUId(sid, "132733485", StønadTypeDagpenger.DAGPENGER)
        val bbid = "AZruzh"
        val btid = "019aeece-1848-79bb-bda5-369b7a16ec67"

        val cbid = "AZsfGC"
        val cuid = dpUId(sid, "132735021", StønadTypeDagpenger.DAGPENGER)
        val ctid = "019b1f18-2199-7395-b8f3-82c497ce2941"

        val a = JsonSerde.jackson.readValue<DpUtbetaling>("""{"sakId":"$sid","behandlingId":"$abid","ident":"12345678910","vedtakstidspunktet":"2025-12-05T14:57:21.107354","utbetalinger":[{"meldeperiode":"132733037","dato":"2025-11-10","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-11","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-12","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-13","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-14","sats":911,"utbetaltBeløp":366,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"}]}""")
        TestRuntime.topics.dp.produce(atid) { a }
        TestRuntime.topics.status.assertThat()
            .has(key = atid, index = 0, size = 1, value = StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.DAGPENGER, linjer = listOf(
                DetaljerLinje(abid, LocalDate.of(2025, 11, 10), LocalDate.of(2025, 11, 13), 911u, 364u, "DAGPENGER"),
                DetaljerLinje(abid, LocalDate.of(2025, 11, 14), LocalDate.of(2025, 11, 14), 911u, 366u, "DAGPENGER"),
            ))))
        val aoppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(atid)
            .with(atid) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(abid, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(364, it.sats.toLong())
                    assertEquals(911, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(abid, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(366, it.sats.toLong())
                    assertEquals(911, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(atid)
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(auid.toString())
            .hasHeader(auid.toString(), "hash_key" to hashOppdrag(aoppdrag).toString())
            .with(auid.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = auid,
                    originalKey = atid,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(abid),
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2025, 11, 10), LocalDate.of(2025, 11, 13), 364u, 911u) +
                    periode(LocalDate.of(2025, 11, 14), LocalDate.of(2025, 11, 14), 366u, 911u)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.oppdrag.produce(atid, mapOf("uids" to "$auid")) {
            aoppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().has(auid.toString())
        TestRuntime.topics.saker.produce(SakKey(SakId(sid), Fagsystem.DAGPENGER)) {
            setOf(auid)
        }

        val b = JsonSerde.jackson.readValue<DpUtbetaling>("""{"sakId":"$sid","behandlingId":"$bbid","ident":"12345678910","vedtakstidspunktet":"2025-12-08T07:09:38.510701","utbetalinger":[{"meldeperiode":"132733037","dato":"2025-11-10","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-11","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-12","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-13","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-14","sats":911,"utbetaltBeløp":366,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-17","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-18","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-19","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-20","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-21","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-24","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-25","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-26","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-27","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-28","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"}]}""")
        TestRuntime.topics.dp.produce(btid) { b }
        TestRuntime.topics.status.assertThat()
            .has(key = btid, index = 0, size = 1, value = StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.DAGPENGER, linjer = listOf(
                DetaljerLinje(bbid, LocalDate.of(2025, 11, 17), LocalDate.of(2025, 11, 28), 911u, 911u, "DAGPENGER"),
            ))))
        val boppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(btid)
            .with(btid) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bbid, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(911, it.sats.toLong())
                    assertEquals(911, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(btid)
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(buid.toString())
            .hasHeader(buid.toString(), "hash_key" to hashOppdrag(boppdrag).toString())
            .with(buid.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = buid,
                    originalKey = btid,
                    sakId = SakId(sid),
                    førsteUtbetalingPåSak = false,
                    behandlingId = BehandlingId(bbid),
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2025, 11, 17), LocalDate.of(2025, 11, 28), 911u, 911u)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.oppdrag.produce(btid, mapOf("uids" to "$buid")) {
            boppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().has(buid.toString())
        TestRuntime.topics.saker.produce(SakKey(SakId(sid), Fagsystem.DAGPENGER)) {
            setOf(auid, buid)
        }

        val c = JsonSerde.jackson.readValue<DpUtbetaling>("""{"sakId":"$sid","behandlingId":"$cbid","ident":"12345678910","vedtakstidspunktet":"2025-12-15T09:26:40.032951","utbetalinger":[{"meldeperiode":"132733037","dato":"2025-11-10","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-11","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-12","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-13","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-14","sats":911,"utbetaltBeløp":366,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-17","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-18","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-19","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-20","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-21","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-24","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-25","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-26","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-27","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-28","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-01","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-02","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-03","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-04","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-05","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-08","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-09","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-10","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-11","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-12","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"}]}""")
        TestRuntime.topics.dp.produce(ctid) { c }
        TestRuntime.topics.status.assertThat()
            .has(key = ctid, index = 0, size = 1, value = StatusReply(Status.MOTTATT, Detaljer( ytelse = Fagsystem.DAGPENGER, linjer = listOf(
                DetaljerLinje(cbid, LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 12), 911u, 911u, "DAGPENGER"),
            ))))
        val coppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(ctid)
            .with(ctid) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(cbid, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(911, it.sats.toLong())
                    assertEquals(911, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(ctid)
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(cuid.toString())
            .hasHeader(cuid.toString(), "hash_key" to hashOppdrag(coppdrag).toString())
            .with(cuid.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = cuid,
                    originalKey = ctid,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(cbid),
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 12), 911u, 911u)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.oppdrag.produce(ctid, mapOf("uids" to "$cuid")) {
            coppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().has(cuid.toString())

        TestRuntime.topics.pendingUtbetalinger.assertThat().isEmpty()
    }

    @Test
    fun `simulering uten endring`() {
        val key = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val meldeperiode1 = UUID.randomUUID().toString()
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.utbetalinger.produce("$uid1") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = key,
                stønad = StønadTypeDagpenger.DAGPENGER,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(1.jan, 2.jan, 100u, 100u)
            }
        }

        TestRuntime.topics.dp.produce(key) {
            Dp.utbetaling(sid.id, bid.id, dryrun = true) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = 1.jan,
                    tom = 2.jan,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                )
            }
        }

        TestRuntime.topics.status.assertThat().has(key).with(key) { statusReply ->
            assertEquals(Status.OK, statusReply.status)
        }

        TestRuntime.topics.simulering.assertThat().hasNot(key)
    }

    @Test
    fun `fom tom endres men meldeperiode står seg`() {
        val sid = SakId("$nextInt")
        var bid = BehandlingId("$nextInt")
        var tid = UUID.randomUUID().toString()
        val uid = dpUId(sid.id, "1-15 aug", StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(tid) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort("1-15 aug", 1.aug, 15.aug, 200u, 200u)
            }
        }
        TestRuntime.topics.status.assertThat()
            .has(tid)
            .has(tid, StatusReply(Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                DetaljerLinje(bid.id, 1.aug, 15.aug, 200u, 200u, "DAGPENGER"),
            ))))
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        var oppdrag = TestRuntime.topics.oppdrag.assertThat().has(tid).get(tid)
        TestRuntime.topics.oppdrag.produce(tid, mapOf("uids" to "$uid")) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid)
        }

        bid = BehandlingId("$nextInt")
        tid = UUID.randomUUID().toString()
        TestRuntime.topics.dp.produce(tid) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort("1-15 aug", 2.aug, 16.aug, 200u, 200u)
            }
        }
        TestRuntime.topics.status.assertThat()
            .has(tid)
            .has(tid, StatusReply(Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                DetaljerLinje(bid.id, 1.aug, 15.aug, 200u, 0u, "DAGPENGER"),
                DetaljerLinje(bid.id, 2.aug, 16.aug, 200u, 200u, "DAGPENGER"),
            ))))
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        oppdrag = TestRuntime.topics.oppdrag.assertThat().has(tid).get(tid)
        val mapper = libs.xml.XMLMapper<no.trygdeetaten.skjema.oppdrag.Oppdrag>()
        println(mapper.writeValueAsString(oppdrag))
        TestRuntime.topics.oppdrag.produce(tid, mapOf("uids" to "$uid")) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid)
        }
    }
    @Test
    fun `meldeperiode fom og tom endres`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        var tid = UUID.randomUUID().toString()
        var uid1 = dpUId(sid.id, "1-15 aug", StønadTypeDagpenger.DAGPENGER)
        var uid2 = dpUId(sid.id, "2-13 sep", StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(tid) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort("1-15 aug", 1.aug, 15.aug, 200u, 200u) +
                Dp.meldekort("2-13 sep", 2.sep, 13.sep, 200u, 200u)
            }
        }
        TestRuntime.topics.status.assertThat()
            .has(tid)
            .has(tid, StatusReply(Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                DetaljerLinje(bid.id, 1.aug, 15.aug, 200u, 200u, "DAGPENGER"),
                DetaljerLinje(bid.id, 2.sep, 13.sep, 200u, 200u, "DAGPENGER"),
            ))))
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        var oppdrag = TestRuntime.topics.oppdrag.assertThat().has(tid).get(tid)
        TestRuntime.topics.oppdrag.produce(tid, mapOf("uids" to "$uid1,$uid2")) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }
        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .has(uid2.toString())
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1,uid2)
        }

        val bid2 = BehandlingId("$nextInt")
        tid = UUID.randomUUID().toString()
        uid1 = dpUId(sid.id, "2-16 aug", StønadTypeDagpenger.DAGPENGER)
        uid2 = dpUId(sid.id, "3-12 sep", StønadTypeDagpenger.DAGPENGER)
        TestRuntime.topics.dp.produce(tid) {
            Dp.utbetaling(sid.id, bid2.id) {
                Dp.meldekort("2-16 aug", 2.aug, 16.aug, 200u, 200u) +
                Dp.meldekort("3-12 sep", 3.sep, 12.sep, 200u, 200u)
            }
        }

        TestRuntime.topics.status.assertThat()
            .has(tid)
            .has(tid, StatusReply(Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                DetaljerLinje(bid2.id, 2.aug, 16.aug, 200u, 200u, "DAGPENGER"),
                DetaljerLinje(bid2.id, 3.sep, 12.sep, 200u, 200u, "DAGPENGER"),
                DetaljerLinje(bid.id, 2.sep, 13.sep, 200u, 0u, "DAGPENGER"),
                DetaljerLinje(bid.id, 1.aug, 15.aug, 200u, 0u, "DAGPENGER"),
            ))))
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        oppdrag = TestRuntime.topics.oppdrag.assertThat().has(tid).get(tid)
        val mapper = libs.xml.XMLMapper<no.trygdeetaten.skjema.oppdrag.Oppdrag>()
        println(mapper.writeValueAsString(oppdrag))
        TestRuntime.topics.oppdrag.produce(tid, mapOf("uids" to "$uid1,$uid2")) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }
        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .has(uid2.toString())
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1, uid2)
        }
    }

    @Test
    fun `1 meldekort i 1 utbetalinger blir til 1 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = "132460781",
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                )
            }
        }

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, StatusReply(Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "DAGPENGER"),
            ))))

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("DAGPENGER", a.kodeKlassifik)
                    assertEquals(553, a.sats.toLong())
                    assertEquals(1077, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                    assertEquals(a.datoVedtakFom, a.datoKlassifikFom)
                    assertEquals(b.datoVedtakFom, b.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId, mapOf("uids" to "$uid")) {
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
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
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGER)
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 2377u,
                    utbetaltBeløp = 779u,
                )
            }
        }

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, StatusReply(Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "DAGPENGER"),
                DetaljerLinje(bid.id, 7.jul21, 20.jul21, 2377u, 779u, "DAGPENGER"),
            ))))

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId, size = 1)
            .with(transactionId, index = 0) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(2, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(553, it.sats.toLong())
                    assertEquals(1077, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(779, it.sats.toLong())
                    assertEquals(2377, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)
        val hashKey = hashOppdrag(oppdrag).toString()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .hasHeader(uid1.toString(), "hash_key" to hashKey)
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
                }
                assertEquals(expected, it)
            }
            .has(uid2.toString())
            .hasHeader(uid2.toString(), "hash_key" to hashKey)
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.oppdrag.produce(transactionId, mapOf("uids" to "$uid1,$uid2")) {
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
                }
                assertEquals(expected, it)
            }
    }

    @Test
    fun `2 meldekort i ett med 2 klassekoder hver blir til 4 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGER)
        val uid2 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGERFERIE)
        val uid3 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.DAGPENGER)
        val uid4 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.DAGPENGERFERIE)

        TestRuntime.topics.dp.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 1000u,
                    utbetaltBeløp = 1000u,
                    utbetalingstype = Utbetalingstype.Dagpenger,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 100u,
                    utbetaltBeløp = 100u,
                    utbetalingstype = Utbetalingstype.DagpengerFerietillegg,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 600u,
                    utbetaltBeløp = 600u,
                    utbetalingstype = Utbetalingstype.Dagpenger,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 300u,
                    utbetaltBeløp = 300u,
                    utbetalingstype = Utbetalingstype.DagpengerFerietillegg,
                )
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1000u, 1000u, "DAGPENGER"),
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, 100u, 100u, "DAGPENGERFERIE"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, 600u, 600u, "DAGPENGER"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, 300u, 300u, "DAGPENGERFERIE"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(4, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(1000, it.sats.toLong())
                    assertEquals(1000, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("DAGPENGERFERIE", it.kodeKlassifik)
                    assertEquals(100, it.sats.toLong())
                    assertEquals(100, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[2].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(600, it.sats.toLong())
                    assertEquals(600, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[3].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("DAGPENGERFERIE", it.kodeKlassifik)
                    assertEquals(300, it.sats.toLong())
                    assertEquals(300, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.id.toString())
            .hasHeader(uid1.toString(), "hash_key" to hashOppdrag(oppdrag).toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 1000u, 1000u)
                }
                assertEquals(expected, it)
            }
            .has(uid2.toString())
            .hasHeader(uid2.toString(), "hash_key" to hashOppdrag(oppdrag).toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGERFERIE,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 100u, 100u)
                }
                assertEquals(expected, it)
            }
            .has(uid3.id.toString())
            .hasHeader(uid3.toString(), "hash_key" to hashOppdrag(oppdrag).toString())
            .with(uid3.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 600u, 600u)
                }
                assertEquals(expected, it)
            }
            .has(uid4.id.toString())
            .hasHeader(uid4.toString(), "hash_key" to hashOppdrag(oppdrag).toString())
            .with(uid4.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid4,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGERFERIE,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 300u, 300u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.oppdrag.produce(transactionId, mapOf("uids" to "$uid1,$uid2,$uid3,$uid4")) {
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 1000u, 1000u)
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGERFERIE,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 100u, 100u)
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 600u, 600u)
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGERFERIE,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 300u, 300u)
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
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGER)
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.DAGPENGER)
        val uid3 = dpUId(sid.id, meldeperiode3, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 2377u,
                    utbetaltBeløp = 779u,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode3,
                    fom = LocalDate.of(2021, 8, 7),
                    tom = LocalDate.of(2021, 8, 20),
                    sats = 3133u,
                    utbetaltBeløp = 3000u,
                )
            }
        }

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, StatusReply( Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "DAGPENGER"),
                DetaljerLinje(bid.id, 7.jul21, 20.jul21, 2377u, 779u, "DAGPENGER"),
                DetaljerLinje(bid.id, 9.aug21, 20.aug21, 3133u, 3000u, "DAGPENGER"),
            ))))

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(3, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(553, it.sats.toLong())
                    assertEquals(1077, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(779, it.sats.toLong())
                    assertEquals(2377, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[2].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(3000, it.sats.toLong())
                    assertEquals(3133, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)

        val hashKey = hashOppdrag(oppdrag).toString()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.id.toString())
            .hasHeader(uid1.toString(), "hash_key" to hashKey)
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
                }
                assertEquals(expected, it)
            }
            .has(uid2.id.toString())
            .hasHeader(uid2.toString(), "hash_key" to hashKey)
            .with(uid2.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
                }
                assertEquals(expected, it)
            }
            .has(uid3.id.toString())
            .hasHeader(uid3.toString(), "hash_key" to hashKey)
            .with(uid3.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 8, 9), LocalDate.of(2021, 8, 20), 3000u, 3133u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.oppdrag.produce(transactionId, mapOf("uids" to "$uid1,$uid2,$uid3")) {
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 8, 9), LocalDate.of(2021, 8, 20), 3000u, 3133u)
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
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGER)
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = transactionId1,
                stønad = StønadTypeDagpenger.DAGPENGER,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(3.jun, 14.jun, 100u, 100u)
            }
        }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1)
        }


        TestRuntime.topics.dp.produce(transactionId2) {
            Dp.utbetaling(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = 3.jun,
                    tom = 14.jun,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = 17.jun,
                    tom = 28.jun,
                    sats = 200u,
                    utbetaltBeløp = 200u,
                )
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 17.jun, 28.jun, 200u, 200u, "DAGPENGER"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId2)
            .has(transactionId2, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId2)
            .with(transactionId2) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("ENDR", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("DAGPENGER", a.kodeKlassifik)
                    assertEquals(200, a.sats.toLong())
                    assertEquals(200, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                    assertEquals(a.datoVedtakFom, a.datoKlassifikFom)
                    assertEquals(b.datoVedtakFom, b.datoKlassifikFom)
                }
            }
            .get(transactionId2)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid2.toString())
            .hasHeader(uid2.toString(), "hash_key" to hashOppdrag(oppdrag).toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId2,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(17.jun, 28.jun, 200u, 200u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.oppdrag.produce(transactionId2, mapOf("uids" to "$uid2")) {
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(17.jun, 28.jun, 200u, 200u)
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
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGER)
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = transactionId1,
                stønad = StønadTypeDagpenger.DAGPENGER,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(3.jun, 14.jun, 100u)
            }
        }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1)
        }


        TestRuntime.topics.dp.produce(transactionId2) {
            Dp.utbetaling(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = 3.jun,
                    tom = 14.jun,
                    sats = 100u,
                    utbetaltBeløp = 80u,
                )
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 3.jun, 14.jun, 100u, 80u, "DAGPENGER"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId2)
            .has(transactionId2, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId2)
            .with(transactionId2) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(periodeId.toString(), it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(80, it.sats.toLong())
                    assertEquals(100, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId2)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .hasHeader(uid1.toString(), "hash_key" to hashOppdrag(oppdrag).toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId2,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 14.jun, 80u, 100u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.oppdrag.produce(transactionId2, mapOf("uids" to "$uid1")) {
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 14.jun, 80u, 100u)
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
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGERFERIE)
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = transactionId1,
                stønad = StønadTypeDagpenger.DAGPENGERFERIE,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(2.jun, 13.jun, 100u, 100u)
            }
        }
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1)
        }

        TestRuntime.topics.dp.produce(transactionId1) {
            Dp.utbetaling(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                emptyList()
            }
        }

        TestRuntime.topics.status.assertThat()
            .has(transactionId1)
            .has(transactionId1, StatusReply(Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                DetaljerLinje(bid.id, 2.jun, 13.jun, 100u, 0u, "DAGPENGERFERIE")))
            ))

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId1)
            .with(transactionId1) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
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
                    assertEquals("DAGPENGERFERIE", it.kodeKlassifik)
                    assertEquals(100, it.sats.toLong())
                    assertEquals(100, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId1)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .hasHeader(uid1.toString(), "hash_key" to hashOppdrag(oppdrag).toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.DELETE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = transactionId1,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGERFERIE,
                    vedtakstidspunkt = 14.jun.atStartOfDay(),
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.jun, 13.jun, 100u, 100u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.oppdrag.produce(transactionId1, mapOf("uids" to "$uid1")) {
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGERFERIE,
                    vedtakstidspunkt = 14.jun.atStartOfDay(),
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.jun, 13.jun, 100u, 100u)
                }
                assertEquals(expected, it)
            }

    }

    @Test
    fun `gjensend en opphørt meldeperiode`() {
        val sid = SakId("$nextInt")
        val meldeperiode = "132460781"
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.DAGPENGER)
        val bid1 = BehandlingId("$nextInt")
        val bid2 = BehandlingId("$nextInt")
        val bid3 = BehandlingId("$nextInt")
        val tid1 = UUID.randomUUID().toString()
        val tid2 = UUID.randomUUID().toString()
        val tid3 = UUID.randomUUID().toString()

        TestRuntime.topics.dp.produce(tid1) {
            Dp.utbetaling(sid.id, bid1.id) {
                Dp.meldekort(meldeperiode, 3.jun, 13.jun, 100u, 100u)
            }
        }
        TestRuntime.topics.status.assertThat()
            .has(tid1)
            .has(tid1, StatusReply(Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                DetaljerLinje(bid1.id, 3.jun, 13.jun, 100u, 100u, "DAGPENGER")))
            ))

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(tid1)
            .get(tid1)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .hasHeader(uid.toString(), "hash_key" to hashOppdrag(oppdrag).toString())
            .with(uid.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid,
                    sakId = sid,
                    behandlingId = bid1,
                    originalKey = tid1,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 13.jun, 100u, 100u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.oppdrag.produce(tid1, mapOf("uids" to "$uid")) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid)
        }

        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())

        TestRuntime.topics.dp.produce(tid2) {
            Dp.utbetaling(sid.id, bid2.id) {
                emptyList()
            }
        }

        TestRuntime.topics.status.assertThat()
            .has(tid2)
            .has(tid2, StatusReply(Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                // egentlig bid2, men vi har bare informasjon om den fra bid1
                DetaljerLinje(bid1.id, 3.jun, 13.jun, 100u, 0u, "DAGPENGER"))) 
            ))

        val oppdrag2 = TestRuntime.topics.oppdrag.assertThat()
            .has(tid2)
            .get(tid2)

        val pendingDelete = TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .hasHeader(uid.toString(), "hash_key" to hashOppdrag(oppdrag2).toString())
            .with(uid.toString()) {
                val expected = utbetaling(
                    action = Action.DELETE,
                    uid = uid,
                    sakId = sid,
                    behandlingId = bid1, // egentlig bid2
                    originalKey = tid1,  // egentlig tid2
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 13.jun, 100u, 100u)
                }
                assertEquals(expected, it)
            }
            .get(uid.toString())

        TestRuntime.topics.oppdrag.produce(tid3, mapOf("uids" to "$uid")) {
            oppdrag2.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf()
        }

        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())

        TestRuntime.topics.dp.produce(tid3) {
            Dp.utbetaling(sid.id, bid3.id) {
                Dp.meldekort(meldeperiode, 3.jun, 13.jun, 100u, 100u)
            }
        }

        TestRuntime.topics.status.assertThat()
            .has(tid3)
            .has(tid3, StatusReply(Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                DetaljerLinje(bid3.id, 3.jun, 13.jun, 100u, 100u, "DAGPENGER")))
            ))

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.oppdrag.assertThat()
            .has(tid3)
            .with(tid3) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(pendingDelete.lastPeriodeId.toString(), it.refDelytelseId) // kjede på forrige
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid3.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(100, it.sats.toLong())
                    assertEquals(100, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
    }

    @Test
    fun `utbetaling uten meldeperiode gir status OK`() {
        val uid = UUID.randomUUID().toString()
        TestRuntime.topics.dp.produce(uid) {
            Dp.utbetaling(
                sakId = "$nextInt",
                behandlingId = "$nextInt",
                vedtakstidspunkt = LocalDateTime.now(),
            ) {
                emptyList()
            }
        }
        val expectedStatus = StatusReply(Status.OK)

        TestRuntime.topics.status.assertThat()
            .has(uid, expectedStatus)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
    }

    @Test
    fun `3 meldekort med ulike operasjoner`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = dpUId(sid.id, "132460781", StønadTypeDagpenger.DAGPENGER)
        val uid2 = dpUId(sid.id, "232460781", StønadTypeDagpenger.DAGPENGER)
        val uid3 = dpUId(sid.id, "132462765", StønadTypeDagpenger.DAGPENGER)
        val pid1 = PeriodeId()
        val pid2 = PeriodeId()

        TestRuntime.topics.utbetalinger.produce(uid1.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = transactionId,
                stønad = StønadTypeDagpenger.DAGPENGER,
                lastPeriodeId = pid1,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.sep.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
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
                stønad = StønadTypeDagpenger.DAGPENGER,
                førsteUtbetalingPåSak = false,
                lastPeriodeId = pid2,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.sep.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(16.sep, 27.sep, 600u, 600u)
            }
        }
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1, uid2)
        }

        TestRuntime.topics.dp.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort("132460781", 2.sep, 13.sep, 600u) +
                Dp.meldekort("132462765", 30.sep, 10.okt, 600u)
            }
        }

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, StatusReply(Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                DetaljerLinje(bid.id, 2.sep, 13.sep, 600u, 600u, "DAGPENGER"),
                DetaljerLinje(bid.id, 30.sep, 10.okt, 600u, 600u, "DAGPENGER"),
                DetaljerLinje(bid.id, 16.sep, 27.sep, 600u, 0u, "DAGPENGER"),
            ))))

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(3, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(pid1.toString(), it.refDelytelseId) // kjede på forrige
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(600, it.sats.toLong())
                    assertEquals(600, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(600, it.sats.toLong())
                    assertEquals(600, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[2].let {
                    //assertEquals(pid2.toString(), it.refDelytelseId)
                    assertNull(it.refDelytelseId)
                    assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
                    // assertEquals(2.jun, it.datoStatusFom.toLocalDate())
                    assertEquals("ENDR", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(600, it.sats.toLong())
                    assertEquals(600, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)

        val hashKey = hashOppdrag(oppdrag).toString()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.id.toString())
            .hasHeader(uid1.toString(), "hash_key" to hashKey)
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid1,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 600u, 600u)
                }
                assertEquals(expected, it)
            }
            .has(uid2.toString())
            .hasHeader(uid2.toString(), "hash_key" to hashKey)
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.DELETE,
                    uid = uid2,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(16.sep, 27.sep, 600u, 600u)
                }
                assertEquals(expected, it)
            }
            .has(uid3.toString())
            .hasHeader(uid3.toString(), "hash_key" to hashKey)
            .with(uid3.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = transactionId,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(30.sep, 10.okt, 600u, 600u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.oppdrag.produce(transactionId, mapOf("uids" to "$uid1,$uid2,$uid3")) {
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 600u, 600u)
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(16.sep, 27.sep, 600u, 600u)
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(30.sep, 10.okt, 600u, 600u)
                }
                assertEquals(expected, it)
            }
    }

    @Test
    fun `skal sortere oppdragslinjer korrekt etter fom-dato`() {
        val sid = SakId("HV2511101004")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val ident = "14518800192"
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.DAGPENGER)

        val dpUtbetaling = DpUtbetaling(
            sakId = sid.id,
            behandlingId = bid.id,
            ident = ident,
            utbetalinger = dagpengerMeldeperiodeDager,
            vedtakstidspunktet = LocalDateTime.now(),
            saksbehandler = "s-kafka",
            beslutter = "b-kafka"
        )

        TestRuntime.topics.dp.produce(transactionId) { dpUtbetaling }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)

                val linjer = oppdrag.oppdrag110.oppdragsLinje150s
                assertEquals(3, linjer.size)

                val fomDatoer = linjer.map { it.datoVedtakFom.toLocalDate() }
                val forventetFomDatoer = listOf(
                    LocalDate.of(2025, 8, 4),
                    LocalDate.of(2025, 8, 7),
                    LocalDate.of(2025, 8, 8)
                )

                assertEquals(forventetFomDatoer, fomDatoer)

            }.get(transactionId)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .hasHeader(uid.toString(), "hash_key" to hashOppdrag(oppdrag).toString())

        TestRuntime.topics.status.assertThat().has(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId, mapOf("uids" to "$uid")) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
            .with(uid.toString()) { utbetaling ->
                val periodeFomDatoer = utbetaling.perioder.map { it.fom }
                val forventetFomDatoer = listOf(
                    LocalDate.of(2025, 8, 4),
                    LocalDate.of(2025, 8, 7),
                    LocalDate.of(2025, 8, 8)
                )
                assertEquals(forventetFomDatoer, periodeFomDatoer)
                assertEquals(3, utbetaling.perioder.size)
            }
    }

    @Test
    fun `bygg opp aggregat over tid med 3 meldeperioder`() {
        val sid = SakId("$nextInt")
        val bid1 = BehandlingId("$nextInt")
        val tid1 = UUID.randomUUID().toString()
        val m1 = "734734234"
        val uid1 = dpUId(sid.id, m1, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(tid1) {
            Dp.utbetaling(sid.id, bid1.id) {
                Dp.meldekort(m1, 2.sep, 13.sep, 300u, 300u)
            }
        }

        TestRuntime.topics.status.assertThat()
            .has(tid1)
            .has(tid1, StatusReply( Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                DetaljerLinje(bid1.id, 2.sep, 13.sep, 300u, 300u, "DAGPENGER"),
            ))))

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag1 = TestRuntime.topics.oppdrag.assertThat()
            .has(tid1)
            .with(tid1) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid1.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(300, it.sats.toLong())
                    assertEquals(300, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(tid1)

        val hashKey1 = hashOppdrag(oppdrag1).toString()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .hasHeader(uid1.toString(), "hash_key" to hashKey1)
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = tid1,
                    sakId = sid,
                    behandlingId = bid1,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 300u, 300u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.oppdrag.produce(tid1, mapOf("uids" to "$uid1")) {
            kvitterOk(oppdrag1)
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = tid1,
                    sakId = sid,
                    behandlingId = bid1,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 300u, 300u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1)
        }

        // ny meldeperiode
        val bid2 = BehandlingId("$nextInt")
        val tid2 = UUID.randomUUID().toString()
        val m2 = "487938984"
        val uid2 = dpUId(sid.id, m2, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(tid2) {
            Dp.utbetaling(sid.id, bid2.id) {
                Dp.meldekort(m1, 2.sep, 13.sep, 300u, 300u) + 
                Dp.meldekort(m2, 16.sep, 27.sep, 300u, 300u) 
            }
        }

        TestRuntime.topics.status.assertThat()
            .has(tid2)
            .has(tid2, StatusReply( Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                DetaljerLinje(bid2.id, 16.sep, 27.sep, 300u, 300u, "DAGPENGER"),
            ))))

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag2 = TestRuntime.topics.oppdrag.assertThat()
            .has(tid2)
            .with(tid2) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid2.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(300, it.sats.toLong())
                    assertEquals(300, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(tid2)

        val hashKey2 = hashOppdrag(oppdrag2).toString()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid2.toString())
            .hasHeader(uid2.toString(), "hash_key" to hashKey2)
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = tid2,
                    førsteUtbetalingPåSak = false,
                    sakId = sid,
                    behandlingId = bid2,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(16.sep, 27.sep, 300u, 300u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.oppdrag.produce(tid2, mapOf("uids" to "$uid2")) {
            kvitterOk(oppdrag2)
            // oppdrag2.apply {
            //     mmel = Mmel().apply { alvorlighetsgrad = "00" }
            // }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = tid2,
                    førsteUtbetalingPåSak = false,
                    sakId = sid,
                    behandlingId = bid2,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(16.sep, 27.sep, 300u, 300u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1, uid2)
        }
        
        // ny meldeperiode
        val bid3 = BehandlingId("$nextInt")
        val tid3 = UUID.randomUUID().toString()
        val m3 = "898597468"
        val uid3 = dpUId(sid.id, m3, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(tid3) {
            Dp.utbetaling(sid.id, bid3.id) {
                Dp.meldekort(m1, 2.sep, 13.sep, 300u, 300u) + 
                Dp.meldekort(m2, 16.sep, 27.sep, 300u, 300u) + 
                Dp.meldekort(m3, 30.sep, 10.okt, 300u, 300u) 
            }
        }

        TestRuntime.topics.status.assertThat()
            .has(tid3)
            .has(tid3, StatusReply( Status.MOTTATT, Detaljer(Fagsystem.DAGPENGER, listOf(
                DetaljerLinje(bid3.id, 30.sep, 10.okt, 300u, 300u, "DAGPENGER"),
            ))))

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag3 = TestRuntime.topics.oppdrag.assertThat()
            .has(tid3)
            .with(tid3) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertNull(it.refDelytelseId)
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid3.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(300, it.sats.toLong())
                    assertEquals(300, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(tid3)

        val hashKey3 = hashOppdrag(oppdrag3).toString()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid3.toString())
            .hasHeader(uid3.toString(), "hash_key" to hashKey3)
            .with(uid3.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = tid3,
                    førsteUtbetalingPåSak = false,
                    sakId = sid,
                    behandlingId = bid3,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(30.sep, 10.okt, 300u, 300u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.oppdrag.produce(tid3, mapOf("uids" to "$uid3")) {
            kvitterOk(oppdrag3)
            // oppdrag3.apply {
            //     mmel = Mmel().apply { alvorlighetsgrad = "00" }
            // }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid3.toString())
            .with(uid3.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = tid3,
                    førsteUtbetalingPåSak = false,
                    sakId = sid,
                    behandlingId = bid3,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(30.sep, 10.okt, 300u, 300u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1, uid2, uid3)
        }
    }

    @Test
    fun `simuler 1 meldekort i 1 utbetalinger`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()

        TestRuntime.topics.dp.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id, dryrun = true) {
                Dp.meldekort(
                    meldeperiode = "132460781",
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
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
                assertEquals("DP", simulering.request.oppdrag.kodeFagomraade)
                assertEquals(sid.id, simulering.request.oppdrag.fagsystemId)
                assertEquals("MND", simulering.request.oppdrag.utbetFrekvens)
                assertEquals("12345678910", simulering.request.oppdrag.oppdragGjelderId)
                assertEquals("dagpenger", simulering.request.oppdrag.saksbehId)
                assertEquals(1, simulering.request.oppdrag.oppdragslinjes.size)
                assertNull(simulering.request.oppdrag.oppdragslinjes[0].refDelytelseId)
                simulering.request.oppdrag.oppdragslinjes[0].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
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
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dpIntern.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = "132460781",
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                )
            }
        }


        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "DAGPENGER"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .has(transactionId, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertEquals("1", oppdrag.oppdrag110.kodeAksjon)
                assertEquals("NY", oppdrag.oppdrag110.kodeEndring)
                assertEquals("DP", oppdrag.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, oppdrag.oppdrag110.fagsystemId)
                assertEquals("MND", oppdrag.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", oppdrag.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(1, oppdrag.oppdrag110.oppdragsLinje150s.size)
                assertNull(oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("DAGPENGER", a.kodeKlassifik)
                    assertEquals(553, a.sats.toLong())
                    assertEquals(1077, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                    assertEquals(a.datoVedtakFom, b.datoKlassifikFom)
                }
            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId, mapOf("uids" to "$uid")) {
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
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.DAGPENGER,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
                }
                assertEquals(expected, it)
            }
    }

    @Test
    fun `avstemmingstidspunkt blir satt til i dag kl 10 over 10`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dpIntern.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = "132460781",
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                )
            }
        }

        TestRuntime.topics.status.assertThat().has(transactionId)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertNotNull(oppdrag.oppdrag110.avstemming115)
                assertEquals(
                    LocalDateTime.now().withHour(10).withMinute(10).withSecond(0).withNano(0),
                    LocalDateTime.parse(oppdrag.oppdrag110.avstemming115.tidspktMelding.trimEnd(), DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS"))
                )

            }
            .get(transactionId)

        TestRuntime.topics.oppdrag.produce(transactionId, mapOf("uids" to "$uid")) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
    }

    @Test
    fun `fagsystem header is propagated on success`() {
        val transactionId = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val meldeperiode = "132460781"
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(meldeperiode, 1.jun, 18.jun, 1077u, 553u)
            }
        }

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .with(transactionId) { reply -> assertEquals(Status.MOTTATT, reply.status) }
            .hasHeader(transactionId, FS_KEY to "DAGPENGER")

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .get(transactionId)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .hasHeader(uid.toString(), "hash_key" to hashOppdrag(oppdrag).toString())
    }

    @Test
    fun `fagsystem header is propagated on error`() {
        val transactionId = UUID.randomUUID().toString()
        TestRuntime.topics.dp.produce(transactionId) {
            Dp.utbetaling(sakId = "too long sak id 123456789012345") {
                Dp.meldekort("132460781", 1.jun, 18.jun, 1077u, 553u)
            }
        }
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .with(transactionId) { reply -> assertEquals(Status.FEILET, reply.status) }
            .hasHeader(transactionId, FS_KEY to "DAGPENGER")
    }
}

