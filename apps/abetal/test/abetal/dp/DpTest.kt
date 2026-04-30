package abetal.dp

import abetal.*
import com.fasterxml.jackson.module.kotlin.readValue
import libs.kafka.JsonSerde
import models.*
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class DpTest : ConsumerTestBase() {

    @Test
    fun `simulation - dry run dp utbetaling`() {
        val utbet = JsonSerde.jackson.readValue<DpUtbetaling>(
            """
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
            }""".trimIndent()
        )
        val uid = "26c8ad95-1731-e800-abd5-ba92ec6aad86"
        val transaction1 = UUID.randomUUID().toString()
        TestRuntime.topics.dp.produce(transaction1) { utbet.asBytes() }
        TestRuntime.topics.status.assertThat().has(transaction1)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid)
            .hasHeader(uid, "hash_key")
        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transaction1)
            .with(transaction1) { oppdrag ->
                oppdrag.assertBasics("NY", "DP", "rsid3", expectedLines = 1, ident = "15898099536")
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = BehandlingId("rbid1"),
                    kodeKlassifik = "DAGPENGER",
                    sats = 1000,
                    vedtakssats = 1000,
                    typeSats = "DAG"
                )
            }.get(transaction1)
        TestRuntime.topics.oppdrag.produce(transaction1, mapOf("uids" to uid)) {
            oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
        }
        TestRuntime.topics.utbetalinger.assertThat().has(uid)

        val dryrun = JsonSerde.jackson.readValue<DpUtbetaling>(
            """
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
            }""".trimIndent()
        )
        val transaction2 = UUID.randomUUID().toString()
        TestRuntime.topics.dp.produce(transaction2) { dryrun.asBytes() }
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
    fun `edge case - tombstone utbetaling then resend meldeperioder`() {
        val sid = "AZps2"
        val abid = BehandlingId("AZrupr")
        val auid = dpUId(sid, "132733037", StønadTypeDagpenger.DAGPENGER)
        val atid = "a19aeea6-bf92-7a7a-a2c0-d22dfde2c60c"

        val buid = dpUId(sid, "132733485", StønadTypeDagpenger.DAGPENGER)
        val bbid = BehandlingId("AZruzh")
        val btid = "b19aeece-1848-79bb-bda5-369b7a16ec67"

        val cbid = BehandlingId("AZsfGC")
        val cuid = dpUId(sid, "132735021", StønadTypeDagpenger.DAGPENGER)
        val ctid = "c19b1f18-2199-7395-b8f3-82c497ce2941"

        val expectedUtbetalingA = utbetaling(
            action = Action.CREATE,
            uid = auid,
            originalKey = atid,
            sakId = SakId(sid),
            behandlingId = abid,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(10.nov, 13.nov, 364u, 911u)
            periode(14.nov, 14.nov, 366u, 911u)
        }

        val expectedUtbetalingB = expectedUtbetalingA.copy(
            uid = buid,
            originalKey = btid,
            førsteUtbetalingPåSak = false,
            behandlingId = bbid,
            perioder = listOf(periode(17.nov, 28.nov, 911u, 911u)),
        )

        val expectedUtbetalingC = expectedUtbetalingA.copy(
            uid = cuid,
            originalKey = ctid,
            behandlingId = cbid,
            førsteUtbetalingPåSak = false,
            perioder = listOf(periode(1.des25, 12.des25, 911u, 911u)),
        )

        val expectedUtbetalingE = expectedUtbetalingC.copy(
            originalKey = "e19b1f18-2199-7395-b8f3-82c497ce2941"
        )

        val a =
            JsonSerde.jackson.readValue<DpUtbetaling>("""{"sakId":"$sid","behandlingId":"$abid","ident":"12345678910","vedtakstidspunktet":"2025-12-05T14:57:21.107354","utbetalinger":[{"meldeperiode":"132733037","dato":"2025-11-10","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-11","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-12","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-13","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-14","sats":911,"utbetaltBeløp":366,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"}]}""")
        TestRuntime.topics.dp.produce(atid, a.asBytes())
        TestRuntime.topics.status.assertThat().has(atid) {
            Dp.mottatt {
                linje(abid, 10.nov, 13.nov, 911u, 364u)
                linje(abid, 14.nov, 14.nov, 911u, 366u)
            }
        }

        val aoppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(atid)
            .with(atid) { oppdrag ->
                oppdrag.assertBasics("NY", "DP", sid, expectedLines = 2)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = abid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 364,
                    vedtakssats = 911
                )
                oppdrag.oppdrag110.oppdragsLinje150s[1].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = abid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 366,
                    vedtakssats = 911,
                    refDelytelseId = oppdrag.oppdrag110.oppdragsLinje150s[0].delytelseId
                )
            }
            .get(atid)
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(auid.toString())
            .hasHeader(auid.toString(), "hash_key" to hashOppdrag(aoppdrag))
            .with(auid.toString()) {
                assertUtbetaling(expectedUtbetalingA, it)
            }
        kvitterOk(atid, aoppdrag, listOf(auid))
        TestRuntime.topics.utbetalinger.assertThat().has(auid.toString())
        TestRuntime.topics.saker.produce(SakKey(SakId(sid), Fagsystem.DAGPENGER), setOf(auid))

        val b = JsonSerde.jackson.readValue<DpUtbetaling>(
            """{"sakId":"$sid","behandlingId":"$bbid","ident":"12345678910","vedtakstidspunktet":"2025-12-08T07:09:38.510701","utbetalinger":[{"meldeperiode":"132733037","dato":"2025-11-10","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-11","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-12","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-13","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-14","sats":911,"utbetaltBeløp":366,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-17","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-18","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-19","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-20","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-21","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-24","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-25","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-26","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-27","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-28","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"}]}"""
        )
        TestRuntime.topics.dp.produce(btid, b.asBytes())
        TestRuntime.topics.status.assertThat().has(btid) {
            Dp.mottatt {
                linje(bbid, 17.nov, 28.nov, 911u, 911u)
            }
        }
        val boppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(btid)
            .with(btid) { oppdrag ->
                oppdrag.assertBasics("ENDR", "DP", sid, expectedLines = 1)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bbid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 911,
                    vedtakssats = 911
                )
            }
            .get(btid)
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(buid.toString())
            .hasHeader(buid.toString(), "hash_key" to hashOppdrag(boppdrag))
            .with(buid.toString()) {
                assertUtbetaling(expectedUtbetalingB, it)
            }
        kvitterOk(btid, boppdrag, listOf(buid))
        TestRuntime.topics.utbetalinger.assertThat().has(buid.toString())
        TestRuntime.topics.saker.produce(SakKey(SakId(sid), Fagsystem.DAGPENGER), setOf(auid, buid))

        val c = JsonSerde.jackson.readValue<DpUtbetaling>(
            """{"sakId":"$sid","behandlingId":"$cbid","ident":"12345678910","vedtakstidspunktet":"2025-12-15T09:26:40.032951","utbetalinger":[{"meldeperiode":"132733037","dato":"2025-11-10","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-11","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-12","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-13","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-14","sats":911,"utbetaltBeløp":366,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-17","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-18","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-19","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-20","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-21","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-24","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-25","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-26","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-27","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-28","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-01","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-02","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-03","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-04","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-05","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-08","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-09","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-10","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-11","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-12","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"}]}"""
        )
        TestRuntime.topics.dp.produce(ctid, c.asBytes())
        TestRuntime.topics.status.assertThat().has(ctid) {
            Dp.mottatt {
                linje(cbid, 1.des25, 12.des25, 911u, 911u)
            }
        }
        val coppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(ctid)
            .with(ctid) { oppdrag ->
                oppdrag.assertBasics("ENDR", "DP", sid, expectedLines = 1)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = cbid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 911,
                    vedtakssats = 911
                )
            }
            .get(ctid)
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(cuid.toString())
            .hasHeader(cuid.toString(), "hash_key" to hashOppdrag(coppdrag))
            .with(cuid.toString()) {
                assertUtbetaling(expectedUtbetalingC, it)
            }
        kvitterOk(ctid, coppdrag, listOf(cuid))
        TestRuntime.topics.utbetalinger.assertThat().has(cuid.toString())
        TestRuntime.topics.saker.produce(SakKey(SakId(sid), Fagsystem.DAGPENGER), setOf(auid, buid, cuid))

        val dtid = "d19b1f18-2199-7395-b8f3-82c497ce2941"
        TestRuntime.topics.dp.produce(dtid) { c.asBytes() }
        TestRuntime.topics.status.assertThat().has(dtid, StatusReply(Status.OK, null))
        TestRuntime.topics.utbetalinger.tombstone(cuid.toString())

        val etid = "e19b1f18-2199-7395-b8f3-82c497ce2941"
        TestRuntime.topics.dp.produce(etid, c.asBytes())
        TestRuntime.topics.status.assertThat().has(etid) {
            Dp.mottatt {
                linje(cbid, 1.des25, 12.des25, 911u, 911u)
            }
        }
        val eoppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(etid)
            .with(etid) { oppdrag ->
                oppdrag.assertBasics("ENDR", "DP", sid, expectedLines = 1)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = cbid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 911,
                    vedtakssats = 911
                )
            }
            .get(etid)
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(cuid.toString())
            .hasHeader(cuid.toString(), "hash_key" to hashOppdrag(eoppdrag))
            .with(cuid.toString()) {
                assertUtbetaling(expectedUtbetalingE, it)
            }
        kvitterOk(etid, eoppdrag, listOf(cuid))
        TestRuntime.topics.utbetalinger.assertThat().has(cuid.toString())
        TestRuntime.topics.pendingUtbetalinger.assertThat().isEmpty()
    }

    @Test
    fun `aggregate - building utbetaling state with multiple transactions`() {
        val sid = "AZps"
        val abid = BehandlingId("AZrupr")
        val auid = dpUId(sid, "132733037", StønadTypeDagpenger.DAGPENGER)
        val atid = "019aeea6-bf92-7a7a-a2c0-d22dfde2c60c"

        val buid = dpUId(sid, "132733485", StønadTypeDagpenger.DAGPENGER)
        val bbid = BehandlingId("AZruzh")
        val btid = "019aeece-1848-79bb-bda5-369b7a16ec67"

        val cbid = BehandlingId("AZsfGC")
        val cuid = dpUId(sid, "132735021", StønadTypeDagpenger.DAGPENGER)
        val ctid = "019b1f18-2199-7395-b8f3-82c497ce2941"

        val a =
            JsonSerde.jackson.readValue<DpUtbetaling>("""{"sakId":"$sid","behandlingId":"$abid","ident":"12345678910","vedtakstidspunktet":"2025-12-05T14:57:21.107354","utbetalinger":[{"meldeperiode":"132733037","dato":"2025-11-10","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-11","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-12","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-13","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-14","sats":911,"utbetaltBeløp":366,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"}]}""")
        TestRuntime.topics.dp.produce(atid, a.asBytes())
        TestRuntime.topics.status.assertThat().has(atid) {
            Dp.mottatt {
                linje(abid, 10.nov, 13.nov, 911u, 364u)
                linje(abid, 14.nov, 14.nov, 911u, 366u)
            }
        }
        val aoppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(atid)
            .with(atid) { oppdrag ->
                oppdrag.assertBasics("NY", "DP", sid, expectedLines = 2)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = abid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 364,
                    vedtakssats = 911
                )
                oppdrag.oppdrag110.oppdragsLinje150s[1].let {
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(abid.toString(), it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(366, it.sats.toLong())
                    assertEquals(911, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(atid)

        val expectedUtbetalingA = utbetaling(
            action = Action.CREATE,
            uid = auid,
            originalKey = atid,
            sakId = SakId(sid),
            behandlingId = abid,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(10.nov, 13.nov, 364u, 911u)
            periode(14.nov, 14.nov, 366u, 911u)
        }

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(auid.toString())
            .hasHeader(auid.toString(), "hash_key" to hashOppdrag(aoppdrag))
            .with(auid.toString()) {
                assertUtbetaling(expectedUtbetalingA, it)
            }
        kvitterOk(atid, aoppdrag, listOf(auid))
        TestRuntime.topics.utbetalinger.assertThat().has(auid.toString())
        TestRuntime.topics.saker.produce(SakKey(SakId(sid), Fagsystem.DAGPENGER), setOf(auid))

        val b = JsonSerde.jackson.readValue<DpUtbetaling>(
            """{"sakId":"$sid","behandlingId":"$bbid","ident":"12345678910","vedtakstidspunktet":"2025-12-08T07:09:38.510701","utbetalinger":[{"meldeperiode":"132733037","dato":"2025-11-10","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-11","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-12","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-13","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-14","sats":911,"utbetaltBeløp":366,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-17","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-18","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-19","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-20","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-21","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-24","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-25","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-26","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-27","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-28","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"}]}"""
        )
        TestRuntime.topics.dp.produce(btid, b.asBytes())
        TestRuntime.topics.status.assertThat().has(btid) {
            Dp.mottatt {
                linje(bbid, 17.nov, 28.nov, 911u, 911u)
            }
        }
        val boppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(btid)
            .with(btid) { oppdrag ->
                oppdrag.assertBasics("ENDR", "DP", sid, expectedLines = 1)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bbid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 911,
                    vedtakssats = 911
                )
            }
            .get(btid)

        val expectedUtbetalingB = expectedUtbetalingA.copy(
            uid = buid,
            originalKey = btid,
            førsteUtbetalingPåSak = false,
            behandlingId = bbid,
            perioder = listOf(periode(17.nov, 28.nov, 911u, 911u)),
        )

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(buid.toString())
            .hasHeader(buid.toString(), "hash_key" to hashOppdrag(boppdrag))
            .with(buid.toString()) {
                assertUtbetaling(expectedUtbetalingB, it)
            }
        kvitterOk(btid, boppdrag, listOf(buid))
        TestRuntime.topics.utbetalinger.assertThat().has(buid.toString())
        TestRuntime.topics.saker.produce(SakKey(SakId(sid), Fagsystem.DAGPENGER), setOf(auid, buid))

        val c = JsonSerde.jackson.readValue<DpUtbetaling>(
            """{"sakId":"$sid","behandlingId":"$cbid","ident":"12345678910","vedtakstidspunktet":"2025-12-15T09:26:40.032951","utbetalinger":[{"meldeperiode":"132733037","dato":"2025-11-10","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-11","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-12","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-13","sats":911,"utbetaltBeløp":364,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733037","dato":"2025-11-14","sats":911,"utbetaltBeløp":366,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-17","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-18","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-19","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-20","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-21","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-24","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-25","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-26","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-27","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132733485","dato":"2025-11-28","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-01","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-02","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-03","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-04","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-05","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-08","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-09","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-10","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-11","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"},{"meldeperiode":"132735021","dato":"2025-12-12","sats":911,"utbetaltBeløp":911,"utbetalingstype":"Dagpenger","rettighetstype":"Ordinær"}]}"""
        )
        TestRuntime.topics.dp.produce(ctid, c.asBytes())
        TestRuntime.topics.status.assertThat().has(ctid) {
            Dp.mottatt {
                linje(cbid, 1.des25, 12.des25, 911u, 911u)
            }
        }
        val coppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(ctid)
            .with(ctid) { oppdrag ->
                oppdrag.assertBasics("ENDR", "DP", sid, expectedLines = 1)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = cbid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 911,
                    vedtakssats = 911
                )
            }
            .get(ctid)

        val expectedUtbetalingC = expectedUtbetalingB.copy(
            uid = cuid,
            originalKey = ctid,
            behandlingId = cbid,
            perioder = listOf(periode(1.des25, 12.des25, 911u, 911u)),
        )

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(cuid.toString())
            .hasHeader(cuid.toString(), "hash_key" to hashOppdrag(coppdrag))
            .with(cuid.toString()) {
                assertUtbetaling(expectedUtbetalingC, it)
            }
        kvitterOk(ctid, coppdrag, listOf(cuid))
        TestRuntime.topics.utbetalinger.assertThat().has(cuid.toString())
    }

    @Test
    fun `update - changing fom tom while keeping meldeperiode`() {
        val sid = SakId("$nextInt")
        var bid = BehandlingId("$nextInt")
        var tid = UUID.randomUUID().toString()
        val uid = dpUId(sid.id, "1-15 aug", StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(tid) {
            Dp.utbetaling(sid.id, bid.id) {
                meldekort("1-15 aug", 1.aug, 15.aug, 200u, 200u)
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat().has(tid) {
            Dp.mottatt {
                linje(bid, 1.aug, 15.aug, 200u, 200u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid.toString())
        var oppdrag = TestRuntime.topics.oppdrag.assertThat().has(tid).get(tid)
        kvitterOk(tid, oppdrag, listOf(uid))
        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid))

        bid = BehandlingId("$nextInt")
        tid = UUID.randomUUID().toString()
        TestRuntime.topics.dp.produce(tid) {
            Dp.utbetaling(sid.id, bid.id) {
                meldekort("1-15 aug", 2.aug, 16.aug, 200u, 200u)
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat().has(tid) {
            Dp.mottatt {
                linje(bid, 1.aug, 15.aug, 200u, 0u)
                linje(bid, 2.aug, 16.aug, 200u, 200u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid.toString())
        oppdrag = TestRuntime.topics.oppdrag.assertThat().has(tid).get(tid)
        // val mapper = libs.xml.XMLMapper<Oppdrag>()
        // println(mapper.writeValueAsString(oppdrag))
        kvitterOk(tid, oppdrag, listOf(uid))
        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid))
    }

    @Test
    fun `update - changing both meldeperiode and fom tom`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        var tid = UUID.randomUUID().toString()
        var uid1 = dpUId(sid.id, "1-15 aug", StønadTypeDagpenger.DAGPENGER)
        var uid2 = dpUId(sid.id, "2-13 sep", StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(tid) {
            Dp.utbetaling(sid.id, bid.id) {
                meldekort("1-15 aug", 1.aug, 15.aug, 200u, 200u)
                meldekort("2-13 sep", 2.sep, 13.sep, 200u, 200u)
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat().has(tid) {
            Dp.mottatt {
                linje(bid, 1.aug, 15.aug, 200u, 200u)
                linje(bid, 2.sep, 13.sep, 200u, 200u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        var oppdrag = TestRuntime.topics.oppdrag.assertThat().has(tid).get(tid)
        kvitterOk(tid, oppdrag, listOf(uid1, uid2))
        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .has(uid2.toString())
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2))

        val bid2 = BehandlingId("$nextInt")
        tid = UUID.randomUUID().toString()
        uid1 = dpUId(sid.id, "2-16 aug", StønadTypeDagpenger.DAGPENGER)
        uid2 = dpUId(sid.id, "3-12 sep", StønadTypeDagpenger.DAGPENGER)
        TestRuntime.topics.dp.produce(tid) {
            Dp.utbetaling(sid.id, bid2.id) {
                meldekort("2-16 aug", 2.aug, 16.aug, 200u, 200u)
                meldekort("3-12 sep", 3.sep, 12.sep, 200u, 200u)
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat().has(tid) {
            Dp.mottatt {
                linje(bid2, 1.aug, 15.aug, 200u, 0u)
                linje(bid2, 2.aug, 16.aug, 200u, 200u)
                linje(bid2, 2.sep, 13.sep, 200u, 0u)
                linje(bid2, 3.sep, 12.sep, 200u, 200u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .has(uid2.toString())
        oppdrag = TestRuntime.topics.oppdrag.assertThat().has(tid).get(tid)
        kvitterOk(tid, oppdrag, listOf(uid1, uid2))
        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .has(uid2.toString())
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2))
    }

    @Test
    fun `update - changing meldeperiode while keeping fom tom`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        var tid = UUID.randomUUID().toString()
        var uid1 = dpUId(sid.id, "1-15 aug", StønadTypeDagpenger.DAGPENGER)
        var uid2 = dpUId(sid.id, "2-13 sep", StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(tid) {
            Dp.utbetaling(sid.id, bid.id) {
                meldekort("1-15 aug", 1.aug, 15.aug, 200u, 200u)
                meldekort("2-13 sep", 2.sep, 13.sep, 200u, 200u)
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat().has(tid) {
            Dp.mottatt {
                linje(bid, 1.aug, 15.aug, 200u, 200u)
                linje(bid, 2.sep, 13.sep, 200u, 200u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .has(uid2.toString())
        var oppdrag = TestRuntime.topics.oppdrag.assertThat().has(tid).get(tid)
        kvitterOk(tid, oppdrag, listOf(uid1, uid2))
        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .has(uid2.toString())
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1, uid2)
        }

        val bid2 = BehandlingId("$nextInt")
        tid = UUID.randomUUID().toString()
        uid1 = dpUId(sid.id, "2-16 aug", StønadTypeDagpenger.DAGPENGER)
        uid2 = dpUId(sid.id, "3-12 sep", StønadTypeDagpenger.DAGPENGER)
        TestRuntime.topics.dp.produce(tid) {
            Dp.utbetaling(sid.id, bid2.id) {
                meldekort("2-16 aug", 1.aug, 15.aug, 200u, 200u)
                meldekort("3-12 sep", 2.sep, 13.sep, 200u, 200u)
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat().has(tid) {
            Dp.mottatt {
                linje(bid2, 1.aug, 15.aug, 200u, 200u)
                linje(bid2, 1.aug, 15.aug, 200u, 0u)
                linje(bid2, 2.sep, 13.sep, 200u, 200u)
                linje(bid2, 2.sep, 13.sep, 200u, 0u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .has(uid2.toString())
        oppdrag = TestRuntime.topics.oppdrag.assertThat().has(tid).get(tid)
        kvitterOk(tid, oppdrag, listOf(uid1, uid2))
        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .has(uid2.toString())
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2))
    }

    @Test
    fun `create - two meldekort create two utbetalinger with single oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGER)
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.DAGPENGER)

        val expectedUtbetaling1 = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            originalKey = transactionId,
            sakId = sid,
            behandlingId = bid,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(7.jun21, 18.jun21, 553u, 1077u)
        }

        val expectedUtbetaling2 = expectedUtbetaling1.copy(
            uid = uid2,
            perioder = listOf(periode(7.jul21, 20.jul21, 779u, 2377u))
        )
        TestRuntime.topics.dp.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                meldekort(meldeperiode1, 7.jun21, 18.jun21, 1077u, 553u)
                meldekort(meldeperiode2, 7.jul21, 20.jul21, 2377u, 779u)
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat().has(transactionId) {
            Dp.mottatt {
                linje(bid, 7.jun21, 18.jun21, 1077u, 553u)
                linje(bid, 7.jul21, 20.jul21, 2377u, 779u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId, size = 1)
            .with(transactionId, index = 0) { oppdrag ->
                oppdrag.assertBasics("NY", "DP", sid.id, expectedLines = 2)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 553,
                    vedtakssats = 1077,
                    refDelytelseId = null
                )
                oppdrag.oppdrag110.oppdragsLinje150s[1].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 779,
                    vedtakssats = 2377,
                    refDelytelseId = null
                )
            }
            .get(transactionId)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .hasHeader(uid1.toString(), "hash_key" to hashOppdrag(oppdrag))
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling1, it)
            }
            .has(uid2.toString())
            .hasHeader(uid2.toString(), "hash_key" to hashOppdrag(oppdrag))
            .with(uid2.toString()) {
                assertUtbetaling(expectedUtbetaling2, it)
            }

        kvitterOk(transactionId, oppdrag, listOf(uid1, uid2))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling1, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                assertUtbetaling(expectedUtbetaling2, it)
            }
    }

    @Test
    fun `create - multiple meldekort with multiple klassekoder`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGER)
        val uid2 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGERFERIE)
        val uid3 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.DAGPENGER)
        val uid4 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.DAGPENGERFERIE)

        val expectedUtbetaling1 = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            originalKey = transactionId,
            sakId = sid,
            behandlingId = bid,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(7.jun21, 18.jun21, 1000u, 1000u)
        }

        val expectedUtbetaling2 = expectedUtbetaling1.copy(
            uid = uid2,
            stønad = StønadTypeDagpenger.DAGPENGERFERIE,
            periodetype = Periodetype.EN_GANG,
            perioder = listOf(periode(7.jun21, 18.jun21, 100u, 100u))
        )

        val expectedUtbetaling3 = expectedUtbetaling1.copy(
            uid = uid3,
            perioder = listOf(periode(7.jul21, 20.jul21, 600u, 600u))
        )

        val expectedUtbetaling4 = expectedUtbetaling1.copy(
            uid = uid4,
            stønad = StønadTypeDagpenger.DAGPENGERFERIE,
            periodetype = Periodetype.EN_GANG,
            perioder = listOf(periode(7.jul21, 20.jul21, 300u, 300u))
        )

        TestRuntime.topics.dp.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                meldekort(meldeperiode1, 7.jun21, 18.jun21, 1000u, 1000u, Utbetalingstype.Dagpenger)
                meldekort(meldeperiode1, 7.jun21, 18.jun21, 100u, 100u, Utbetalingstype.DagpengerFerietillegg)
                meldekort(meldeperiode2, 7.jul21, 20.jul21, 600u, 600u, Utbetalingstype.Dagpenger)
                meldekort(meldeperiode2, 7.jul21, 20.jul21, 300u, 300u, Utbetalingstype.DagpengerFerietillegg)
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(transactionId) {
            Dp.mottatt {
                linje(bid, 7.jun21, 18.jun21, 1000u, 1000u)
                linje(bid, 7.jun21, 18.jun21, 100u, 100u, "DAGPENGERFERIE")
                linje(bid, 7.jul21, 20.jul21, 600u, 600u)
                linje(bid, 7.jul21, 20.jul21, 300u, 300u, "DAGPENGERFERIE")
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.assertBasics("NY", "DP", sid.id, expectedLines = 4)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 1000,
                    vedtakssats = 1000,
                    refDelytelseId = null,
                    typeSats = Periodetype.UKEDAG.satstype
                )
                oppdrag.oppdrag110.oppdragsLinje150s[1].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "DAGPENGERFERIE",
                    sats = 100,
                    vedtakssats = 100,
                    refDelytelseId = null,
                    typeSats = Periodetype.EN_GANG.satstype,
                )
                oppdrag.oppdrag110.oppdragsLinje150s[2].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 600,
                    vedtakssats = 600,
                    refDelytelseId = null,
                    typeSats = Periodetype.UKEDAG.satstype
                )
                oppdrag.oppdrag110.oppdragsLinje150s[3].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "DAGPENGERFERIE",
                    sats = 300,
                    vedtakssats = 300,
                    refDelytelseId = null,
                    typeSats = Periodetype.EN_GANG.satstype
                )
            }
            .get(transactionId)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .hasHeader(uid1.toString(), "hash_key" to hashOppdrag(oppdrag))
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling1, it)
            }
            .has(uid2.toString())
            .hasHeader(uid2.toString(), "hash_key" to hashOppdrag(oppdrag))
            .with(uid2.toString()) {
                assertUtbetaling(expectedUtbetaling2, it)
            }
            .has(uid3.toString())
            .hasHeader(uid3.toString(), "hash_key" to hashOppdrag(oppdrag))
            .with(uid3.toString()) {
                assertUtbetaling(expectedUtbetaling3, it)
            }
            .has(uid4.toString())
            .hasHeader(uid4.toString(), "hash_key" to hashOppdrag(oppdrag))
            .with(uid4.toString()) {
                assertUtbetaling(expectedUtbetaling4, it)
            }

        kvitterOk(transactionId, oppdrag, listOf(uid1, uid2, uid3, uid4))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling1, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                assertUtbetaling(expectedUtbetaling2, it)
            }
            .has(uid3.toString())
            .with(uid3.toString()) {
                assertUtbetaling(expectedUtbetaling3, it)
            }
            .has(uid4.toString())
            .with(uid4.toString()) {
                assertUtbetaling(expectedUtbetaling4, it)
            }
    }

    @Test
    fun `create - three meldekort create three utbetalinger with single oppdrag`() {
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
                meldekort(meldeperiode1, 7.jun21, 20.jun21, 1077u, 553u)
                meldekort(meldeperiode2, 7.jul21, 20.jul21, 2377u, 779u)
                meldekort(meldeperiode3, 7.aug21, 20.aug21, 3133u, 3000u)
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat().has(transactionId) {
            Dp.mottatt {
                linje(bid, 7.jun21, 18.jun21, 1077u, 553u)
                linje(bid, 7.jul21, 20.jul21, 2377u, 779u)
                linje(bid, 9.aug21, 20.aug21, 3133u, 3000u)
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.assertBasics("NY", "DP", sid.id, expectedLines = 3)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 553,
                    vedtakssats = 1077
                )
                oppdrag.oppdrag110.oppdragsLinje150s[1].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 779,
                    vedtakssats = 2377
                )
                oppdrag.oppdrag110.oppdragsLinje150s[2].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 3000,
                    vedtakssats = 3133
                )
            }
            .get(transactionId)

        val hashKey = hashOppdrag(oppdrag)

        val expectedUtbetaling1 = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            originalKey = transactionId,
            sakId = sid,
            behandlingId = bid,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(7.jun21, 18.jun21, 553u, 1077u)
        }

        val expectedUtbetaling2 = expectedUtbetaling1.copy(
            uid = uid2,
            perioder = listOf(periode(7.jul21, 20.jul21, 779u, 2377u))
        )

        val expectedUtbetaling3 = expectedUtbetaling1.copy(
            uid = uid3,
            perioder = listOf(periode(9.aug21, 20.aug21, 3000u, 3133u))
        )

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .hasHeader(uid1.toString(), "hash_key" to hashKey)
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling1, it)
            }
            .has(uid2.toString())
            .hasHeader(uid2.toString(), "hash_key" to hashKey)
            .with(uid2.toString()) {
                assertUtbetaling(expectedUtbetaling2, it)
            }
            .has(uid3.toString())
            .hasHeader(uid3.toString(), "hash_key" to hashKey)
            .with(uid3.toString()) {
                assertUtbetaling(expectedUtbetaling3, it)
            }

        kvitterOk(transactionId, oppdrag, listOf(uid1, uid2, uid3))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString(), size = 1)
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling1, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                assertUtbetaling(expectedUtbetaling2, it)
            }
            .has(uid3.toString())
            .with(uid3.toString()) {
                assertUtbetaling(expectedUtbetaling3, it)
            }
    }

    @Test
    fun `create - adding new meldekort to existing sak`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId1 = UUID.randomUUID().toString()
        val transactionId2 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGER)
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.DAGPENGER)

        val existingUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId1,
            stønad = StønadTypeDagpenger.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            fagsystem = Fagsystem.DAGPENGER,
        ) {
            periode(3.jun, 14.jun, 100u, 100u)
        }

        val expectedUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid2,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId2,
            førsteUtbetalingPåSak = false,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(17.jun, 28.jun, 200u, 200u)
        }

        TestRuntime.topics.utbetalinger.produce(uid1.toString(), existingUtbetaling)
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1))
        TestRuntime.topics.dp.produce(transactionId2) {
            Dp.utbetaling(sid.id, bid.id, vedtakstidspunkt = 14.jun.atStartOfDay()) {
                meldekort(meldeperiode1, 3.jun, 14.jun, 100u, 100u)
                meldekort(meldeperiode2, 17.jun, 28.jun, 200u, 200u)
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(transactionId2) {
            Dp.mottatt {
                linje(bid, 17.jun, 28.jun, 200u, 200u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId2)
            .with(transactionId2) { oppdrag ->
                oppdrag.assertBasics("ENDR", "DP", sid.id, expectedLines = 1)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertNull(oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 200,
                    vedtakssats = 200,
                    refDelytelseId = null
                )
            }
            .get(transactionId2)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid2.toString())
            .hasHeader(uid2.toString(), "hash_key" to hashOppdrag(oppdrag))
            .with(uid2.toString()) {
                assertUtbetaling(expectedUtbetaling, it)
            }

        kvitterOk(transactionId2, oppdrag, listOf(uid2))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid2.toString())
            .with(uid2.toString()) {
                assertUtbetaling(expectedUtbetaling, it)
            }
    }

    @Test
    fun `update - changing meldekort on existing sak`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId1 = UUID.randomUUID().toString()
        val transactionId2 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGER)
        val periodeId = PeriodeId()

        val utbetaling = utbetaling(
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

        val expectedUtbetaling = utbetaling.copy(
            action = Action.UPDATE,
            originalKey = transactionId2,
            førsteUtbetalingPåSak = false,
            perioder = listOf(periode(3.jun, 14.jun, 80u, 100u))
        )

        TestRuntime.topics.utbetalinger.produce(uid1.toString(), utbetaling)
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1))
        TestRuntime.topics.dp.produce(transactionId2) {
            Dp.utbetaling(sid.id, bid.id, vedtakstidspunkt = 14.jun.atStartOfDay()) {
                meldekort(meldeperiode1, 3.jun, 14.jun, 100u, 80u)
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat().has(transactionId2) {
            Dp.mottatt {
                linje(bid, 3.jun, 14.jun, 100u, 80u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId2)
            .with(transactionId2) { oppdrag ->
                oppdrag.assertBasics("ENDR", "DP", sid.id, expectedLines = 1)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 80,
                    vedtakssats = 100,
                    refDelytelseId = periodeId.toString()
                )
            }
            .get(transactionId2)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .hasHeader(uid1.toString(), "hash_key" to hashOppdrag(oppdrag))
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling, it)
            }

        kvitterOk(transactionId2, oppdrag, listOf(uid1))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling, it)
            }
    }

    @Test
    fun `opphør - canceling meldekort`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val bid2 = BehandlingId("$nextInt")
        val transactionId1 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGERFERIE)

        TestRuntime.topics.utbetalinger.produce(uid1.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = transactionId1,
                stønad = StønadTypeDagpenger.DAGPENGERFERIE,
                lastPeriodeId = PeriodeId(),
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(2.jun, 13.jun, 100u, 100u)
            }
        }
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1))

        TestRuntime.topics.dp.produce(transactionId1) {
            Dp.utbetaling(
                sakId = sid.id,
                behandlingId = bid2.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                // empty
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(transactionId1) {
            Dp.mottatt {
                linje(bid2, 2.jun, 13.jun, 100u, 0u, "DAGPENGERFERIE")
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId1)
            .with(transactionId1) { oppdrag ->
                oppdrag.assertBasics("ENDR", "DP", sid.id, expectedLines = 1)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                //assertEquals(periodeId.toString(), oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                assertNull(oppdrag.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
                    assertEquals(2.jun, it.datoStatusFom.toLocalDate())
                    //assertEquals(periodeId.toString(), it.refDelytelseId)
                    assertNull(it.refDelytelseId)
                    assertEquals("ENDR", it.kodeEndringLinje)
                    assertEquals(bid2.id, it.henvisning)
                    assertEquals("DAGPENGERFERIE", it.kodeKlassifik)
                    assertEquals(100, it.sats.toLong())
                    assertEquals(100, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId1)

        val expectedUtbetaling = utbetaling(
            action = Action.DELETE,
            uid = uid1,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId1,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGERFERIE,
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(2.jun, 13.jun, 100u, 100u)
        }

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .hasHeader(uid1.toString(), "hash_key" to hashOppdrag(oppdrag))
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling, it)
            }

        kvitterOk(transactionId1, oppdrag, listOf(uid1))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling, it)
            }
    }

    @Test
    fun `edge case - resending cancelled meldeperiode`() {
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
                meldekort(meldeperiode, 3.jun, 13.jun, 100u, 100u)
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat().has(tid1) {
            Dp.mottatt {
                linje(bid1, 3.jun, 13.jun, 100u, 100u)
            }
        }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(tid1)
            .get(tid1)

        val expectedUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            behandlingId = bid1,
            originalKey = tid1,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(3.jun, 13.jun, 100u, 100u)
        }

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .hasHeader(uid.toString(), "hash_key" to hashOppdrag(oppdrag))
            .with(uid.toString()) {
                assertUtbetaling(expectedUtbetaling, it)
            }

        kvitterOk(tid1, oppdrag, listOf(uid))
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid))
        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.dp.produce(tid2) {
            Dp.utbetaling(sid.id, bid2.id) {}.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(tid2) {
            Dp.mottatt {
                // egentlig bid2, men vi har bare informasjon om den fra bid1
                linje(bid2, 3.jun, 13.jun, 100u, 0u)
            }
        }

        val oppdrag2 = TestRuntime.topics.oppdrag.assertThat()
            .has(tid2)
            .get(tid2)

        val expectedDelete = utbetaling(
            action = Action.DELETE,
            uid = uid,
            sakId = sid,
            behandlingId = bid1, // egentlig bid2
            originalKey = tid1,  // egentlig tid2
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(3.jun, 13.jun, 100u, 100u)
        }

        val pendingDelete = TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .hasHeader(uid.toString(), "hash_key" to hashOppdrag(oppdrag2))
            .with(uid.toString()) {
                assertUtbetaling(expectedDelete, it)
            }
            .get(uid.toString())

        kvitterOk(tid3, oppdrag2, listOf(uid))
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER), setOf())
        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.dp.produce(tid3) {
            Dp.utbetaling(sid.id, bid3.id) {
                meldekort(meldeperiode, 3.jun, 13.jun, 100u, 100u)
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(tid3) {
            Dp.mottatt {
                linje(bid3, 3.jun, 13.jun, 100u, 100u)
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.oppdrag.assertThat()
            .has(tid3)
            .with(tid3) { oppdrag ->
                oppdrag.assertBasics("ENDR", "DP", sid.id, expectedLines = 1)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
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
    fun `edge case - multiple meldekort with mixed operations`() {
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
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2))
        TestRuntime.topics.dp.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                meldekort("132460781", 2.sep, 13.sep, 600u)
                meldekort("132462765", 30.sep, 10.okt, 600u)
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat().has(transactionId) {
            Dp.mottatt {
                linje(bid, 2.sep, 13.sep, 600u, 600u)
                linje(bid, 16.sep, 27.sep, 600u, 0u)
                linje(bid, 30.sep, 10.okt, 600u, 600u)
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.assertBasics("ENDR", "DP", sid.id, expectedLines = 3)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(pid1.toString(), it.refDelytelseId) // kjede på forrige
                    assertEquals("NY", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("DAGPENGER", it.kodeKlassifik)
                    assertEquals(600, it.sats.toLong())
                    assertEquals(600, it.vedtakssats157.vedtakssats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[1].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "DAGPENGER",
                    sats = 600,
                    vedtakssats = 600
                )
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

        val hashKey = hashOppdrag(oppdrag)

        val expectedUtbetaling1 = utbetaling(
            action = Action.UPDATE,
            uid = uid1,
            originalKey = transactionId,
            sakId = sid,
            behandlingId = bid,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            førsteUtbetalingPåSak = false,
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(2.sep, 13.sep, 600u, 600u)
        }

        val expectedUtbetaling2 = expectedUtbetaling1.copy(
            action = Action.DELETE,
            uid = uid2,
            perioder = listOf(periode(16.sep, 27.sep, 600u, 600u))
        )

        val expectedUtbetaling3 = expectedUtbetaling1.copy(
            action = Action.CREATE,
            uid = uid3,
            perioder = listOf(periode(30.sep, 10.okt, 600u, 600u))
        )

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .hasHeader(uid1.toString(), "hash_key" to hashKey)
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling1, it)
            }
            .has(uid2.toString())
            .hasHeader(uid2.toString(), "hash_key" to hashKey)
            .with(uid2.toString()) {
                assertUtbetaling(expectedUtbetaling2, it)
            }
            .has(uid3.toString())
            .hasHeader(uid3.toString(), "hash_key" to hashKey)
            .with(uid3.toString()) {
                assertUtbetaling(expectedUtbetaling3, it)
            }

        kvitterOk(transactionId, oppdrag, listOf(uid1, uid2, uid3))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling1, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                assertUtbetaling(expectedUtbetaling2, it)
            }
            .has(uid3.toString())
            .with(uid3.toString()) {
                assertUtbetaling(expectedUtbetaling3, it)
            }
    }

    @Test
    fun `mapping - sorting oppdragslinjer by fom date`() {
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

        TestRuntime.topics.dp.produce(transactionId, dpUtbetaling.asBytes())

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
            .hasHeader(uid.toString(), "hash_key" to hashOppdrag(oppdrag))

        TestRuntime.topics.status.assertThat().has(transactionId)

        kvitterOk(transactionId, oppdrag, listOf(uid))

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
    fun `aggregate - building state over time with multiple meldeperioder`() {
        val sid = SakId("$nextInt")
        val bid1 = BehandlingId("$nextInt")
        val tid1 = UUID.randomUUID().toString()
        val m1 = "734734234"
        val uid1 = dpUId(sid.id, m1, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(tid1) {
            Dp.utbetaling(sid.id, bid1.id) {
                meldekort(m1, 2.sep, 13.sep, 300u, 300u)
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(tid1) {
            Dp.mottatt {
                linje(bid1, 2.sep, 13.sep, 300u, 300u)
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag1 = TestRuntime.topics.oppdrag.assertThat()
            .has(tid1)
            .with(tid1) { oppdrag ->
                oppdrag.assertBasics("NY", "DP", sid.id, expectedLines = 1)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid1,
                    kodeKlassifik = "DAGPENGER",
                    sats = 300,
                    vedtakssats = 300
                )
            }
            .get(tid1)

        val hashKey1 = hashOppdrag(oppdrag1)

        val expectedUtbetaling1 = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            originalKey = tid1,
            sakId = sid,
            behandlingId = bid1,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(2.sep, 13.sep, 300u, 300u)
        }

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .hasHeader(uid1.toString(), "hash_key" to hashKey1)
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling1, it)
            }

        kvitterOk(tid1, oppdrag1, listOf(uid1))
        // TestRuntime.topics.oppdrag.produce(tid1, mapOf("uids" to "$uid1")) {
        //     kvitterOk(oppdrag1)
        // }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling1, it)
            }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1))

        // ny meldeperiode
        val bid2 = BehandlingId("$nextInt")
        val tid2 = UUID.randomUUID().toString()
        val m2 = "487938984"
        val uid2 = dpUId(sid.id, m2, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(tid2) {
            Dp.utbetaling(sid.id, bid2.id) {
                meldekort(m1, 2.sep, 13.sep, 300u, 300u)
                meldekort(m2, 16.sep, 27.sep, 300u, 300u)
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(tid2) {
            Dp.mottatt {
                linje(bid2, 16.sep, 27.sep, 300u, 300u)
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag2 = TestRuntime.topics.oppdrag.assertThat()
            .has(tid2)
            .with(tid2) { oppdrag ->
                oppdrag.assertBasics("ENDR", "DP", sid.id, expectedLines = 1)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid2,
                    kodeKlassifik = "DAGPENGER",
                    sats = 300,
                    vedtakssats = 300
                )
            }
            .get(tid2)

        val hashKey2 = hashOppdrag(oppdrag2)

        val expectedUtbetaling2 = utbetaling(
            action = Action.CREATE,
            uid = uid2,
            originalKey = tid2,
            førsteUtbetalingPåSak = false,
            sakId = sid,
            behandlingId = bid2,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(16.sep, 27.sep, 300u, 300u)
        }

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid2.toString())
            .hasHeader(uid2.toString(), "hash_key" to hashKey2)
            .with(uid2.toString()) {
                assertUtbetaling(expectedUtbetaling2, it)
            }

        kvitterOk(tid2, oppdrag2, listOf(uid2))
        // TestRuntime.topics.oppdrag.produce(tid2, mapOf("uids" to "$uid2")) {
        //     kvitterOk(oppdrag2)
        // }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid2.toString())
            .with(uid2.toString()) {
                assertUtbetaling(expectedUtbetaling2, it)
            }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2))

        // ny meldeperiode
        val bid3 = BehandlingId("$nextInt")
        val tid3 = UUID.randomUUID().toString()
        val m3 = "898597468"
        val uid3 = dpUId(sid.id, m3, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(tid3) {
            Dp.utbetaling(sid.id, bid3.id) {
                meldekort(m1, 2.sep, 13.sep, 300u, 300u)
                meldekort(m2, 16.sep, 27.sep, 300u, 300u)
                meldekort(m3, 30.sep, 10.okt, 300u, 300u)
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(tid3) {
            Dp.mottatt {
                linje(bid3, 30.sep, 10.okt, 300u, 300u)
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag3 = TestRuntime.topics.oppdrag.assertThat()
            .has(tid3)
            .with(tid3) { oppdrag ->
                oppdrag.assertBasics("ENDR", "DP", sid.id, expectedLines = 1)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid3,
                    kodeKlassifik = "DAGPENGER",
                    sats = 300,
                    vedtakssats = 300
                )
            }
            .get(tid3)

        val hashKey3 = hashOppdrag(oppdrag3)

        val expectedUtbetaling3 = utbetaling(
            action = Action.CREATE,
            uid = uid3,
            originalKey = tid3,
            førsteUtbetalingPåSak = false,
            sakId = sid,
            behandlingId = bid3,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(30.sep, 10.okt, 300u, 300u)
        }

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid3.toString())
            .hasHeader(uid3.toString(), "hash_key" to hashKey3)
            .with(uid3.toString()) {
                assertUtbetaling(expectedUtbetaling3, it)
            }

        kvitterOk(tid3, oppdrag3, listOf(uid3))
        // TestRuntime.topics.oppdrag.produce(tid3, mapOf("uids" to "$uid3")) {
        //     kvitterOk(oppdrag3)
        // }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid3.toString())
            .with(uid3.toString()) {
                assertUtbetaling(expectedUtbetaling3, it)
            }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2, uid3))
    }

    @Test
    fun `create - meldekort med 0 beløp`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.DAGPENGER)

        val expectedUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid,
            originalKey = transactionId,
            sakId = sid,
            behandlingId = bid,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 10), 553u, 1077u)
        }

        TestRuntime.topics.dpIntern.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                meldekort("132460781", 1.jun21, 6.jun21, 1077u, 0u)
                meldekort("132460781", 7.jun21, 10.jun21, 1077u, 553u)
                meldekort("132460781", 11.jun21, 14.jun21, 1077u, 0u)
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat().has(transactionId) {
            Dp.mottatt {
                linje(bid, 7.jun21, 10.jun21, 1077u, 553u)
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertUtbetaling(expectedUtbetaling, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.assertBasics("NY", "DP", sid.id, expectedLines = 1)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
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

        kvitterOk(transactionId, oppdrag, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertUtbetaling(expectedUtbetaling, it)
            }
    }

    @Test
    fun `create - multiple meldekort create utbetalinger with single oppdrag (1 meldekort)`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.DAGPENGER)

        val expectedUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid,
            originalKey = transactionId,
            sakId = sid,
            behandlingId = bid,
            fagsystem = Fagsystem.DAGPENGER,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeDagpenger.DAGPENGER,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("dagpenger"),
            saksbehandlerId = Navident("dagpenger"),
            personident = Personident("12345678910")
        ) {
            periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
        }

        TestRuntime.topics.dpIntern.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                meldekort("132460781", 7.jun21, 18.jun21, 1077u, 553u)
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat().has(transactionId) {
            Dp.mottatt {
                linje(bid, 7.jun21, 18.jun21, 1077u, 553u)
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertUtbetaling(expectedUtbetaling, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.assertBasics("NY", "DP", sid.id, expectedLines = 1)
                assertEquals("dagpenger", oppdrag.oppdrag110.saksbehId)
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

        kvitterOk(transactionId, oppdrag, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertUtbetaling(expectedUtbetaling, it)
            }
    }

    @Test
    fun `mapping - nokkelAvstemming set to today at 10h10m`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dpIntern.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                meldekort("132460781", 6.jun21, 18.jun21, 1077u, 553u)
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(transactionId)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid.toString())

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                assertNotNull(oppdrag.oppdrag110.avstemming115)
                assertEquals(
                    LocalDateTime.now().withHour(10).withMinute(10).withSecond(0).withNano(0),
                    LocalDateTime.parse(
                        oppdrag.oppdrag110.avstemming115.nokkelAvstemming,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
                    )
                )

            }
            .get(transactionId)

        kvitterOk(transactionId, oppdrag, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
    }

    @Test
    fun `edge case - fagsystem header propagated on success`() {
        val transactionId = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val meldeperiode = "132460781"
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(transactionId) {
            Dp.utbetaling(sid.id, bid.id) {
                meldekort(meldeperiode, 1.jun, 18.jun, 1077u, 553u)
            }.asBytes()
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
            .hasHeader(uid.toString(), "hash_key" to hashOppdrag(oppdrag))
    }

    @Test
    fun `edge case - fagsystem header propagated on error`() {
        val transactionId = UUID.randomUUID().toString()
        TestRuntime.topics.dp.produce(transactionId) {
            Dp.utbetaling(sakId = "too long sak id 123456789012345") {
                meldekort("132460781", 1.jun, 18.jun, 1077u, 553u)
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .with(transactionId) { reply -> assertEquals(Status.FEILET, reply.status) }
            .hasHeader(transactionId, FS_KEY to "DAGPENGER")
    }

    @Test
    fun `status - FEILET ved deserialiseringsfeil`() {
        val transactionId = UUID.randomUUID().toString()

        TestRuntime.topics.dp.produce(transactionId) {
            """{ "ugyldig-json": """.toByteArray()
        }

        val status = TestRuntime.topics.status.readValue()
        assertEquals(Status.FEILET, status.status)
        assertNotNull(status.error)
    }

    @Test
    fun `status - FEILET ved prosesseringsfeil`() {
        val transactionId = UUID.randomUUID().toString()

        TestRuntime.topics.dp.produce(transactionId) {
            Dp.utbetaling(
                sakId = "too long sak id 123456789012345",
                behandlingId = "beh-1",
            ) {
                meldekort("periode-1", LocalDate.now(), LocalDate.now(), 100u)
            }.asBytes()
        }

        val status = TestRuntime.topics.status.readValue()
        assertEquals(Status.FEILET, status.status)
        assertNotNull(status.error)
    }
}
