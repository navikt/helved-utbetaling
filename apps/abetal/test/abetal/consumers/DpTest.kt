package abetal.consumers

import abetal.*
import com.fasterxml.jackson.module.kotlin.readValue
import libs.kafka.JsonSerde
import models.*
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

internal class DpTest {

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
                  "rettighetstype": "Ordinær",
                  "utbetalingstype": "Dagpenger"
                },
                {
                  "meldeperiode": "2025-08-01/2025-08-14",
                  "dato": "2025-08-02",
                  "sats": 1000,
                  "utbetaltBeløp": 1000,
                  "rettighetstype": "Ordinær",
                  "utbetalingstype": "Dagpenger"
                },
                {
                  "meldeperiode": "2025-08-01/2025-08-14",
                  "dato": "2025-08-03",
                  "sats": 1000,
                  "utbetaltBeløp": 1000,
                  "rettighetstype": "Ordinær",
                  "utbetalingstype": "Dagpenger"
                }
              ],
              "vedtakstidspunktet": "2025-08-27T10:00:00Z",
              "saksbehandler": "dagpenger",
              "beslutter": "dagpenger"
            }""".trimIndent())
        val uid = "9aa7f092-704a-1d53-8b98-0ece9cabb5e4"
        val transaction1 = UUID.randomUUID().toString()
        TestRuntime.topics.dp.produce(transaction1) { utbet }
        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)
        TestRuntime.topics.status.assertThat().has(transaction1)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid)
        val oppdrag = TestRuntime.topics.oppdrag.assertThat().has(transaction1).with(transaction1) {
            assertEquals("1", it.oppdrag110.kodeAksjon)
            assertEquals("NY", it.oppdrag110.kodeEndring)
            assertEquals("DP", it.oppdrag110.kodeFagomraade)
            assertEquals("rsid3", it.oppdrag110.fagsystemId)
            assertEquals("MND", it.oppdrag110.utbetFrekvens)
            assertEquals("15898099536", it.oppdrag110.oppdragGjelderId)
            assertEquals("dagpenger", it.oppdrag110.saksbehId)
            assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
            assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
            val førsteLinje = it.oppdrag110.oppdragsLinje150s[0]
            assertNull(førsteLinje.refDelytelseId)
            assertEquals("NY", førsteLinje.kodeEndringLinje)
            assertEquals("rbid1", førsteLinje.henvisning)
            assertEquals("DPORAS", førsteLinje.kodeKlassifik)
            assertEquals("DAG", førsteLinje.typeSats)
            assertEquals(1000, førsteLinje.sats.toLong())
            assertEquals(1000, førsteLinje.vedtakssats157.vedtakssats.toLong())
        }.get(transaction1)
        TestRuntime.topics.oppdrag.produce(transaction1) { oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } } }
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
                  "rettighetstype": "Ordinær",
                  "utbetalingstype": "Dagpenger"
                },
                {
                  "meldeperiode": "2025-08-01/2025-08-14",
                  "dato": "2025-08-02",
                  "sats": 1000,
                  "utbetaltBeløp": 900,
                  "rettighetstype": "Ordinær",
                  "utbetalingstype": "Dagpenger"
                },
                {
                  "meldeperiode": "2025-08-01/2025-08-14",
                  "dato": "2025-08-03",
                  "sats": 1000,
                  "utbetaltBeløp": 900,
                  "rettighetstype": "Ordinær",
                  "utbetalingstype": "Dagpenger"
                }
              ],
              "vedtakstidspunktet": "2025-08-27T10:00:00Z",
              "saksbehandler": "R123456",
              "beslutter": "R123456"
            }""".trimIndent())
        val transaction2 = UUID.randomUUID().toString()
        TestRuntime.topics.dp.produce(transaction2) { dryrun }
        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)
        TestRuntime.topics.simulering.assertThat().has(transaction2).with(transaction2) {
            assertEquals("ENDR", it.request.oppdrag.kodeEndring)
            assertEquals("DP", it.request.oppdrag.kodeFagomraade)
            assertEquals("rsid3", it.request.oppdrag.fagsystemId)
            assertEquals("MND", it.request.oppdrag.utbetFrekvens)
            assertEquals("15898099536", it.request.oppdrag.oppdragGjelderId)
            assertEquals("R123456", it.request.oppdrag.saksbehId)
            assertEquals(1, it.request.oppdrag.oppdragslinjes.size)
        }
    }

    @Test
    fun `1 meldekort i 1 utbetalinger blir til 1 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestRuntime.topics.dp.produce(originalKey) {
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

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "DPORAS"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
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
                    assertEquals("DPORAS", a.kodeKlassifik)
                    assertEquals(553, a.sats.toLong())
                    assertEquals(1077, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                }
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
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
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.saker.assertThat()
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
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestRuntime.topics.dp.produce(originalKey) {
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

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "DPORAS"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, 2377u, 779u, "DPORAS"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.id.toString())
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
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
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
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey, size = 1)
            .with(originalKey, index = 0) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(2, it.oppdrag110.oppdragsLinje150s.size)
                val førsteLinje = it.oppdrag110.oppdragsLinje150s[0]
                assertNull(førsteLinje.refDelytelseId)
                assertEquals("NY", førsteLinje.kodeEndringLinje)
                assertEquals(bid.id, førsteLinje.henvisning)
                assertEquals("DPORAS", førsteLinje.kodeKlassifik)
                assertEquals(553, førsteLinje.sats.toLong())
                assertEquals(1077, førsteLinje.vedtakssats157.vedtakssats.toLong())
                val andreLinje = it.oppdrag110.oppdragsLinje150s[1]
                assertEquals("NY", andreLinje.kodeEndringLinje)
                assertEquals(bid.id, andreLinje.henvisning)
                assertEquals("DPORAS", andreLinje.kodeKlassifik)
                assertEquals(779, andreLinje.sats.toLong())
                assertEquals(2377, andreLinje.vedtakssats157.vedtakssats.toLong())
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
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
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
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
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 2)
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 0) {
                assertEquals(it, setOf(uid1))
            }
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 1) {
                assertEquals(it, setOf(uid1, uid2))
            }
    }

    @Test
    fun `2 meldekort i ett med 2 klassekoder hver blir til 4 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid2 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG)
        val uid3 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.PERMITTERING_ORDINÆR)
        val uid4 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.PERMITTERING_ORDINÆR_FERIETILLEGG)

        TestRuntime.topics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 1000u,
                    utbetaltBeløp = 1000u,
                    rettighetstype = Rettighetstype.Ordinær,
                    utbetalingstype = Utbetalingstype.Dagpenger,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 100u,
                    utbetaltBeløp = 100u,
                    rettighetstype = Rettighetstype.Ordinær,
                    utbetalingstype = Utbetalingstype.DagpengerFerietillegg,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 600u,
                    utbetaltBeløp = 600u,
                    rettighetstype = Rettighetstype.Permittering,
                    utbetalingstype = Utbetalingstype.Dagpenger,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 300u,
                    utbetaltBeløp = 300u,
                    rettighetstype = Rettighetstype.Permittering,
                    utbetalingstype = Utbetalingstype.DagpengerFerietillegg,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1000u, 1000u, "DPORAS"),
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, 100u, 100u, "DPORASFE"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, 600u, 600u, "DPPEASFE1"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, 300u, 300u, "DPPEAS"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.id.toString())
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
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 1000u, 1000u)
                }
                assertEquals(expected, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG,
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
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.PERMITTERING_ORDINÆR,
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
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.PERMITTERING_ORDINÆR_FERIETILLEGG,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 300u, 300u)
                }
                assertEquals(expected, it)
            }
        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(4, it.oppdrag110.oppdragsLinje150s.size)
                val førsteLinje = it.oppdrag110.oppdragsLinje150s[0]
                assertNull(førsteLinje.refDelytelseId)
                assertEquals("NY", førsteLinje.kodeEndringLinje)
                assertEquals(bid.id, førsteLinje.henvisning)
                assertEquals("DPORAS", førsteLinje.kodeKlassifik)
                assertEquals(1000, førsteLinje.sats.toLong())
                assertEquals(1000, førsteLinje.vedtakssats157.vedtakssats.toLong())
                val andreLinje = it.oppdrag110.oppdragsLinje150s[1]
                assertEquals("NY", andreLinje.kodeEndringLinje)
                assertEquals(bid.id, andreLinje.henvisning)
                assertEquals("DPORASFE", andreLinje.kodeKlassifik)
                assertEquals(100, andreLinje.sats.toLong())
                assertEquals(100, andreLinje.vedtakssats157.vedtakssats.toLong())
                val tredjeLinje = it.oppdrag110.oppdragsLinje150s[2]
                assertEquals("NY", tredjeLinje.kodeEndringLinje)
                assertEquals(bid.id, tredjeLinje.henvisning)
                assertEquals("DPPEASFE1", tredjeLinje.kodeKlassifik)
                assertEquals(600, tredjeLinje.sats.toLong())
                assertEquals(600, tredjeLinje.vedtakssats157.vedtakssats.toLong())
                val fjerdeLinje = it.oppdrag110.oppdragsLinje150s[3]
                assertEquals("NY", fjerdeLinje.kodeEndringLinje)
                assertEquals(bid.id, fjerdeLinje.henvisning)
                assertEquals("DPPEAS", fjerdeLinje.kodeKlassifik)
                assertEquals(300, fjerdeLinje.sats.toLong())
                assertEquals(300, fjerdeLinje.vedtakssats157.vedtakssats.toLong())
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
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
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 1000u, 1000u)
                }
                assertEquals(expected, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG,
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
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.PERMITTERING_ORDINÆR,
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
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.PERMITTERING_ORDINÆR_FERIETILLEGG,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 300u, 300u)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 4)
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 0) {
                assertEquals(it, setOf(uid1))
            }
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 1) {
                assertEquals(it, setOf(uid1, uid2))
            }
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 2) {
                assertEquals(it, setOf(uid1, uid2, uid3))
            }
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 3) {
                assertEquals(it, setOf(uid1, uid2, uid3, uid4))
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
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid3 = dpUId(sid.id, meldeperiode3, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestRuntime.topics.dp.produce(originalKey) {
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

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "DPORAS"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, 2377u, 779u, "DPORAS"),
                    DetaljerLinje(bid.id, 9.aug21, 20.aug21, 3133u, 3000u, "DPORAS"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.id.toString())
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
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
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
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
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
                    periode(LocalDate.of(2021, 8, 9), LocalDate.of(2021, 8, 20), 3000u, 3133u)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(3, it.oppdrag110.oppdragsLinje150s.size)

                val linje1 = it.oppdrag110.oppdragsLinje150s[0]
                assertNull(linje1.refDelytelseId)
                assertEquals("NY", linje1.kodeEndringLinje)
                assertEquals(bid.id, linje1.henvisning)
                assertEquals("DPORAS", linje1.kodeKlassifik)
                assertEquals(553, linje1.sats.toLong())
                assertEquals(1077, linje1.vedtakssats157.vedtakssats.toLong())

                val linje2 = it.oppdrag110.oppdragsLinje150s[1]
                assertNull(linje2.refDelytelseId)
                assertEquals("NY", linje2.kodeEndringLinje)
                assertEquals(bid.id, linje2.henvisning)
                assertEquals("DPORAS", linje2.kodeKlassifik)
                assertEquals(779, linje2.sats.toLong())
                assertEquals(2377, linje2.vedtakssats157.vedtakssats.toLong())

                val linje3 = it.oppdrag110.oppdragsLinje150s[2]
                assertNull(linje3.refDelytelseId)
                assertEquals("NY", linje3.kodeEndringLinje)
                assertEquals(bid.id, linje3.henvisning)
                assertEquals("DPORAS", linje3.kodeKlassifik)
                assertEquals(3000, linje3.sats.toLong())
                assertEquals(3133, linje3.vedtakssats157.vedtakssats.toLong())
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
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
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
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
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
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
                    periode(LocalDate.of(2021, 8, 9), LocalDate.of(2021, 8, 20), 3000u, 3133u)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.saker.assertThat()
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
        val uid1 = dpUId(
            sid.id,
            meldeperiode1,
            StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR
        ) // 16364e1c-7615-6b30-882b-d7d19ea96279
        val uid2 = dpUId(
            sid.id,
            meldeperiode2,
            StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR
        ) // 6fa69f14-a3eb-1457-7859-b3676f59da9d

        TestRuntime.topics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                )
            }
        }
        TestRuntime.topics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 2377u,
                    utbetaltBeløp = 779u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "DPORAS"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, 2377u, 779u, "DPORAS"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.id.toString())
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
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
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
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(2, it.oppdrag110.oppdragsLinje150s.size)

                val linje1 = it.oppdrag110.oppdragsLinje150s[0]
                assertNull(linje1.refDelytelseId)
                assertEquals("NY", linje1.kodeEndringLinje)
                assertEquals(bid.id, linje1.henvisning)
                assertEquals("DPORAS", linje1.kodeKlassifik)
                assertEquals(553, linje1.sats.toLong())
                assertEquals(1077, linje1.vedtakssats157.vedtakssats.toLong())

                val linje2 = it.oppdrag110.oppdragsLinje150s[1]
                assertNull(linje2.refDelytelseId)
                assertEquals("NY", linje2.kodeEndringLinje)
                assertEquals(bid.id, linje2.henvisning)
                assertEquals("DPORAS", linje2.kodeKlassifik)
                assertEquals(779, linje2.sats.toLong())
                assertEquals(2377, linje2.vedtakssats157.vedtakssats.toLong())
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
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
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
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
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 2)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1))
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2), index = 1)
    }

    @Test
    // TODO: Se TODO på Oppdrag.plus(other: Oppdrag) i AggregatService ()
    fun `2 meldekort med 2 behandlinger for samme person blir til 2 utbetalinger med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid1 = BehandlingId("$nextInt")
        val bid2 = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestRuntime.topics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid1.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                )
            }
        }
        TestRuntime.topics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid2.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                ) + 
                Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 2377u,
                    utbetaltBeløp = 779u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER,
                linjer = listOf(
                    DetaljerLinje(bid1.id, 7.jun21, 18.jun21, 1077u, 553u, "DPORAS"),
                    DetaljerLinje(bid2.id, 7.jul21, 20.jul21, 2377u, 779u, "DPORAS"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid1,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
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
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid2,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(2, it.oppdrag110.oppdragsLinje150s.size)

                val linje1 = it.oppdrag110.oppdragsLinje150s[0]
                assertNull(linje1.refDelytelseId)
                assertEquals("NY", linje1.kodeEndringLinje)
                assertEquals(bid1.id, linje1.henvisning)
                assertEquals("DPORAS", linje1.kodeKlassifik)
                assertEquals(553, linje1.sats.toLong())
                assertEquals(1077, linje1.vedtakssats157.vedtakssats.toLong())

                val linje2 = it.oppdrag110.oppdragsLinje150s[1]
                assertNull(linje2.refDelytelseId)
                assertEquals("NY", linje2.kodeEndringLinje)
                assertEquals(bid2.id, linje2.henvisning)
                assertEquals("DPORAS", linje2.kodeKlassifik)
                assertEquals(779, linje2.sats.toLong())
                assertEquals(2377, linje2.vedtakssats157.vedtakssats.toLong())
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
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
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid1,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
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
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid2,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 2)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1))
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2), index = 1)
    }

    @Test
    fun `nytt meldekort på eksisterende sak`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey1 = UUID.randomUUID().toString()
        val originalKey2 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey1,
                stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
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

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.dp.produce(originalKey2) {
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

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 17.jun, 28.jun, 200u, 200u, "DPORAS"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey2)
            .has(originalKey2, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = originalKey2,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(17.jun, 28.jun, 200u, 200u)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey2)
            .with(originalKey2) {
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
                    assertEquals("DPORAS", a.kodeKlassifik)
                    assertEquals(200, a.sats.toLong())
                    assertEquals(200, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                }
            }
            .get(originalKey2)

        TestRuntime.topics.oppdrag.produce(originalKey2) {
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
                    originalKey = originalKey2,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(17.jun, 28.jun, 200u, 200u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 2)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2), index = 1)
    }

    @Test
    fun `endre meldekort på eksisterende sak`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey1 = UUID.randomUUID().toString()
        val originalKey2 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey1,
                stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
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

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.dp.produce(originalKey2) {
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

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 3.jun, 14.jun, 100u, 80u, "DPORAS"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey2)
            .has(originalKey2, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = originalKey2,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 14.jun, 80u, 100u)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey2)
            .with(originalKey2) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)

                val førsteLinje = it.oppdrag110.oppdragsLinje150s[0]
                assertEquals(periodeId.toString(), førsteLinje.refDelytelseId)
                assertEquals("NY", førsteLinje.kodeEndringLinje)
                assertEquals(bid.id, førsteLinje.henvisning)
                assertEquals("DPORAS", førsteLinje.kodeKlassifik)
                assertEquals(80, førsteLinje.sats.toLong())
                assertEquals(100, førsteLinje.vedtakssats157.vedtakssats.toLong())
            }
            .get(originalKey2)

        TestRuntime.topics.oppdrag.produce(originalKey2) {
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
                    originalKey = originalKey2,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 14.jun, 80u, 100u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 2)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1), index = 0)
    }

    @Test
    fun `opphør på meldekort`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey1 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey1,
                stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(2.jun, 13.jun, 100u)
            }
        }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1)
        }

        TestRuntime.topics.dp.produce(originalKey1) {
            Dp.utbetaling(
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
            detaljer = Detaljer(Fagsystem.DAGPENGER, listOf(DetaljerLinje(bid.id, 2.jun, 13.jun, 100u, 0u, "DPORAS")))
        )

        TestRuntime.topics.status.assertThat()
            .has(originalKey1)
            .has(originalKey1, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.DELETE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = originalKey1,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = 14.jun.atStartOfDay(),
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.jun, 13.jun, 100u, 100u)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey1)
            .with(originalKey1) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)

                val førsteLinje = it.oppdrag110.oppdragsLinje150s[0]
                assertEquals(TkodeStatusLinje.OPPH, førsteLinje.kodeStatusLinje)
                assertEquals(2.jun, førsteLinje.datoStatusFom.toLocalDate())
                assertEquals(periodeId.toString(), førsteLinje.refDelytelseId)
                assertEquals("NY", førsteLinje.kodeEndringLinje)
                assertEquals(bid.id, førsteLinje.henvisning)
                assertEquals("DPORAS", førsteLinje.kodeKlassifik)
                assertEquals(100, førsteLinje.sats.toLong())
                assertEquals(100, førsteLinje.vedtakssats157.vedtakssats.toLong())
            }
            .get(originalKey1)

        TestRuntime.topics.oppdrag.produce(originalKey1) {
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
                    originalKey = originalKey1,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = 14.jun.atStartOfDay(),
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.jun, 13.jun, 100u, 100u)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 2)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(), index = 1)

    }

    @Test
    fun `3 meldekort med ulike operasjoner`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val uid1 = dpUId(sid.id, "132460781", StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid2 = dpUId(sid.id, "232460781", StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid3 = dpUId(sid.id, "132462765", StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val pid1 = PeriodeId()
        val pid2 = PeriodeId()

        TestRuntime.topics.utbetalinger.produce(uid1.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey,
                stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                lastPeriodeId = pid1,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.sep.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(2.sep, 13.sep, 500u) // 1-14
            }
        }
        TestRuntime.topics.utbetalinger.produce(uid2.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid2,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey,
                stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                førsteUtbetalingPåSak = false,
                lastPeriodeId = pid2,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.sep.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(16.sep, 27.sep, 600u) // 15-28
            }
        }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1, uid2)
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort("132460781", 2.sep, 13.sep, 600u) +
                        Dp.meldekort("132462765", 30.sep, 10.okt, 600u) // 29-12
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER,
                linjer = listOf(
                    DetaljerLinje(bid.id, 2.sep, 13.sep, 600u, 600u, "DPORAS"),
                    DetaljerLinje(bid.id, 30.sep, 10.okt, 600u, 600u, "DPORAS"),
                    DetaljerLinje(bid.id, 16.sep, 27.sep, 600u, 0u, "DPORAS"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid1,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 600u)
                }
                assertEquals(expected, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.DELETE,
                    uid = uid2,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(16.sep, 27.sep, 600u)
                }
                assertEquals(expected, it)
            }
            .has(uid3.toString())
            .with(uid3.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(30.sep, 10.okt, 600u)
                }
                assertEquals(expected, it)
            }
        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(3, it.oppdrag110.oppdragsLinje150s.size)

                val linje1 = it.oppdrag110.oppdragsLinje150s[0]
                assertEquals(pid1.toString(), linje1.refDelytelseId) // kjede på forrige
                assertEquals("NY", linje1.kodeEndringLinje)
                assertEquals(bid.id, linje1.henvisning)
                assertEquals("DPORAS", linje1.kodeKlassifik)
                assertEquals(600, linje1.sats.toLong())
                assertEquals(600, linje1.vedtakssats157.vedtakssats.toLong())

                val linje2 = it.oppdrag110.oppdragsLinje150s[1]
                assertNull(linje2.refDelytelseId)
                assertEquals("NY", linje2.kodeEndringLinje)
                assertEquals(bid.id, linje2.henvisning)
                assertEquals("DPORAS", linje2.kodeKlassifik)
                assertEquals(600, linje2.sats.toLong())
                assertEquals(600, linje2.vedtakssats157.vedtakssats.toLong())

                // FIXME: er opphøret alltid sist, eller er det tilfeldig hvor i lista den havner
                val linje3 = it.oppdrag110.oppdragsLinje150s[2]
                assertEquals(pid2.toString(), linje3.refDelytelseId) // kjede på forrige
                assertEquals(TkodeStatusLinje.OPPH, linje3.kodeStatusLinje)
                // assertEquals(2.jun, linje3.datoStatusFom.toLocalDate())
                assertEquals("NY", linje3.kodeEndringLinje)
                assertEquals(bid.id, linje3.henvisning)
                assertEquals("DPORAS", linje3.kodeKlassifik)
                assertEquals(600, linje3.sats.toLong())
                assertEquals(600, linje3.vedtakssats157.vedtakssats.toLong())
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
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
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 600u)
                }
                assertEquals(expected, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.DELETE,
                    uid = uid2,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(16.sep, 27.sep, 600u)
                }
                assertEquals(expected, it)
            }
            .has(uid3.toString())
            .with(uid3.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(30.sep, 10.okt, 600u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 5)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2), index = 1)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2), index = 2)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2, uid3), index = 3)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid3), index = 4)
    }

    @Test
    @Disabled
    fun `en meldeperiode som endres 3 ganger samtidig skal feile`() {
        val sid = SakId("$nextInt")
        val bid1 = BehandlingId("$nextInt")
        val bid2 = BehandlingId("$nextInt")
        val bid3 = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestRuntime.topics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid1.id) {
                Dp.meldekort(meldeperiode, 2.sep, 13.sep, 300u, 300u)
            }
        }
        TestRuntime.topics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid2.id) {
                Dp.meldekort(meldeperiode, 2.sep, 13.sep, 300u, 300u)
                Dp.meldekort(meldeperiode, 16.sep, 27.sep, 300u, 300u)
            }
        }
        TestRuntime.topics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid3.id) {
                Dp.meldekort(meldeperiode, 2.sep, 13.sep, 300u, 300u)
                Dp.meldekort(meldeperiode, 16.sep, 27.sep, 300u, 300u)
                Dp.meldekort(meldeperiode, 30.sep, 10.okt, 300u, 300u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER,
                linjer = listOf(
                    DetaljerLinje(bid1.id, 2.sep, 13.sep, 300u, 300u, "DPORAS"),
                    DetaljerLinje(bid2.id, 16.sep, 27.sep, 300u, 300u, "DPORAS"),
                    DetaljerLinje(bid3.id, 30.sep, 10.okt, 300u, 300u, "DPORAS"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString(), 1)
            .with(uid.toString(), index = 0) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid3,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 300u, 300u) +
                            periode(16.sep, 27.sep, 300u, 300u) +
                            periode(30.sep, 10.okt, 300u, 300u)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(3, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                val l1 = it.oppdrag110.oppdragsLinje150s[0]
                assertNull(l1.refDelytelseId)
                assertEquals("NY", l1.kodeEndringLinje)
                assertEquals(bid1.id, l1.henvisning)
                assertEquals("DPORAS", l1.kodeKlassifik)
                assertEquals(300, l1.sats.toLong())
                assertEquals(300, l1.vedtakssats157.vedtakssats.toLong())
                val l2 = it.oppdrag110.oppdragsLinje150s[1]
                assertNull(l1.refDelytelseId)
                assertEquals("NY", l2.kodeEndringLinje)
                assertEquals(bid2.id, l2.henvisning)
                assertEquals("DPORAS", l2.kodeKlassifik)
                assertEquals(300, l2.sats.toLong())
                assertEquals(300, l2.vedtakssats157.vedtakssats.toLong())
                val l3 = it.oppdrag110.oppdragsLinje150s[2]
                assertNull(l1.refDelytelseId)
                assertEquals("NY", l3.kodeEndringLinje)
                assertEquals(bid3.id, l3.henvisning)
                assertEquals("DPORAS", l3.kodeKlassifik)
                assertEquals(300, l3.sats.toLong())
                assertEquals(300, l3.vedtakssats157.vedtakssats.toLong())
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
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
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid3,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 300u, 300u) +
                    periode(16.sep, 27.sep, 300u, 300u) +
                    periode(30.sep, 10.okt, 300u, 300u)
                }
                assertEquals(expected, it)
            }


                TestRuntime.topics.saker.assertThat()
                    .has(SakKey(sid, Fagsystem.DAGPENGER), size = 1)
                    .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid), index = 0)
            }

        // Dette støtter vi ikke lengre
        @Test
        @Disabled
        fun `3 saker blir til 3 utbetalinger med 3 oppdrag`() {
            val sid1 = SakId("$nextInt")
            val sid2 = SakId("$nextInt")
            val sid3 = SakId("$nextInt")
            val bid1 = BehandlingId("$nextInt")
            val bid2 = BehandlingId("$nextInt")
            val bid3 = BehandlingId("$nextInt")
            val originalKey = "12345678910"
            val meldeperiode1 = "100000000"
            val meldeperiode2 = "200000000"
            val meldeperiode3 = "300000000"
            val uid1 = dpUId(sid1.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
            val uid2 = dpUId(sid2.id, meldeperiode2, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
            val uid3 = dpUId(sid3.id, meldeperiode3, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

            TestRuntime.topics.dp.produce(originalKey) {
                Dp.utbetaling(sid1.id, bid1.id) {
                    Dp.meldekort(meldeperiode1, 2.sep, 13.sep, 300u, 300u)
                }
            }
            TestRuntime.topics.dp.produce(originalKey) {
                Dp.utbetaling(sid2.id, bid2.id) {
                    Dp.meldekort(meldeperiode2, 16.sep, 27.sep, 300u, 300u)
                }
            }
            TestRuntime.topics.dp.produce(originalKey) {
                Dp.utbetaling(sid3.id, bid3.id) {
                    Dp.meldekort(meldeperiode3, 30.sep, 10.okt, 300u, 300u)
                }
            }

            TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

            val mottatt1 = StatusReply(
                Status.MOTTATT,
                Detaljer(Fagsystem.DAGPENGER, listOf(DetaljerLinje(bid1.id, 2.sep, 13.sep, 300u, 300u, "DPORAS")))
            )
            val mottatt2 = StatusReply(
                Status.MOTTATT,
                Detaljer(Fagsystem.DAGPENGER, listOf(DetaljerLinje(bid2.id, 16.sep, 27.sep, 300u, 300u, "DPORAS")))
            )
            val mottatt3 = StatusReply(
                Status.MOTTATT,
                Detaljer(Fagsystem.DAGPENGER, listOf(DetaljerLinje(bid3.id, 30.sep, 10.okt, 300u, 300u, "DPORAS")))
            )

            TestRuntime.topics.status.assertThat()
                .has(originalKey, 3)
                .has(originalKey, mottatt1, index = 0)
                .has(originalKey, mottatt2, index = 1)
                .has(originalKey, mottatt3, index = 2)

            TestRuntime.topics.utbetalinger.assertThat().isEmpty()

            TestRuntime.topics.pendingUtbetalinger.assertThat()
                .has(uid1.toString())
                .with(uid1.toString()) {
                    val expected = utbetaling(
                        action = Action.CREATE,
                        uid = uid1,
                        originalKey = originalKey,
                        sakId = sid1,
                        behandlingId = bid1,
                        fagsystem = Fagsystem.DAGPENGER,
                        lastPeriodeId = it.lastPeriodeId,
                        stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                        vedtakstidspunkt = it.vedtakstidspunkt,
                        beslutterId = Navident("dagpenger"),
                        saksbehandlerId = Navident("dagpenger"),
                        personident = Personident("12345678910")
                    ) {
                        periode(2.sep, 13.sep, 300u, 300u)
                    }
                    assertEquals(expected, it)
                }
                .with(uid2.toString()) {
                    val expected = utbetaling(
                        action = Action.CREATE,
                        uid = uid2,
                        originalKey = originalKey,
                        sakId = sid2,
                        behandlingId = bid2,
                        fagsystem = Fagsystem.DAGPENGER,
                        lastPeriodeId = it.lastPeriodeId,
                        stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                        vedtakstidspunkt = it.vedtakstidspunkt,
                        beslutterId = Navident("dagpenger"),
                        saksbehandlerId = Navident("dagpenger"),
                        personident = Personident("12345678910")
                    ) {
                        periode(16.sep, 27.sep, 300u, 300u)
                    }
                    assertEquals(expected, it)
                }
                .with(uid3.toString()) {
                    val expected = utbetaling(
                        action = Action.CREATE,
                        uid = uid3,
                        originalKey = originalKey,
                        sakId = sid3,
                        behandlingId = bid3,
                        fagsystem = Fagsystem.DAGPENGER,
                        lastPeriodeId = it.lastPeriodeId,
                        stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                        vedtakstidspunkt = it.vedtakstidspunkt,
                        beslutterId = Navident("dagpenger"),
                        saksbehandlerId = Navident("dagpenger"),
                        personident = Personident("12345678910")
                    ) {
                        periode(30.sep, 10.okt, 300u, 300u)
                    }
                    assertEquals(expected, it)
                }

            val assertOppdrag = TestRuntime.topics.oppdrag.assertThat()
            assertOppdrag.has(originalKey, size = 3)
                .with(originalKey, index = 0) {
                    assertEquals("1", it.oppdrag110.kodeAksjon)
                    assertEquals("NY", it.oppdrag110.kodeEndring)
                    assertEquals("DP", it.oppdrag110.kodeFagomraade)
                    assertEquals(sid1.id, it.oppdrag110.fagsystemId)
                    assertEquals("MND", it.oppdrag110.utbetFrekvens)
                    assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                    assertEquals("dagpenger", it.oppdrag110.saksbehId)
                    assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                    assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                    val l1 = it.oppdrag110.oppdragsLinje150s[0]
                    assertNull(l1.refDelytelseId)
                    assertEquals("NY", l1.kodeEndringLinje)
                    assertEquals(bid1.id, l1.henvisning)
                    assertEquals("DPORAS", l1.kodeKlassifik)
                    assertEquals(300, l1.sats.toLong())
                    assertEquals(300, l1.vedtakssats157.vedtakssats.toLong())
                }
                .with(originalKey, index = 1) {
                    assertEquals("1", it.oppdrag110.kodeAksjon)
                    assertEquals("NY", it.oppdrag110.kodeEndring)
                    assertEquals("DP", it.oppdrag110.kodeFagomraade)
                    assertEquals(sid2.id, it.oppdrag110.fagsystemId)
                    assertEquals("MND", it.oppdrag110.utbetFrekvens)
                    assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                    assertEquals("dagpenger", it.oppdrag110.saksbehId)
                    assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                    assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                    val l1 = it.oppdrag110.oppdragsLinje150s[0]
                    assertNull(l1.refDelytelseId)
                    assertEquals("NY", l1.kodeEndringLinje)
                    assertEquals(bid2.id, l1.henvisning)
                    assertEquals("DPORAS", l1.kodeKlassifik)
                    assertEquals(300, l1.sats.toLong())
                    assertEquals(300, l1.vedtakssats157.vedtakssats.toLong())
                }
                .with(originalKey, index = 2) {
                    assertEquals("1", it.oppdrag110.kodeAksjon)
                    assertEquals("NY", it.oppdrag110.kodeEndring)
                    assertEquals("DP", it.oppdrag110.kodeFagomraade)
                    assertEquals(sid3.id, it.oppdrag110.fagsystemId)
                    assertEquals("MND", it.oppdrag110.utbetFrekvens)
                    assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                    assertEquals("dagpenger", it.oppdrag110.saksbehId)
                    assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                    assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                    val l1 = it.oppdrag110.oppdragsLinje150s[0]
                    assertNull(l1.refDelytelseId)
                    assertEquals("NY", l1.kodeEndringLinje)
                    assertEquals(bid3.id, l1.henvisning)
                    assertEquals("DPORAS", l1.kodeKlassifik)
                    assertEquals(300, l1.sats.toLong())
                    assertEquals(300, l1.vedtakssats157.vedtakssats.toLong())
                }
            val oppdrag1 = assertOppdrag.get(originalKey, index = 0)
            val oppdrag2 = assertOppdrag.get(originalKey, index = 1)
            val oppdrag3 = assertOppdrag.get(originalKey, index = 2)

            TestRuntime.topics.oppdrag.produce(originalKey) {
                oppdrag1.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
            }
            TestRuntime.topics.oppdrag.produce(originalKey) {
                oppdrag2.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
            }
            TestRuntime.topics.oppdrag.produce(originalKey) {
                oppdrag3.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
            }

            TestRuntime.topics.utbetalinger.assertThat()
                .has(uid1.toString())
                .with(uid1.toString()) {
                    val expected = utbetaling(
                        action = Action.CREATE,
                        uid = uid1,
                        originalKey = originalKey,
                        sakId = sid1,
                        behandlingId = bid1,
                        fagsystem = Fagsystem.DAGPENGER,
                        lastPeriodeId = it.lastPeriodeId,
                        stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                        vedtakstidspunkt = it.vedtakstidspunkt,
                        beslutterId = Navident("dagpenger"),
                        saksbehandlerId = Navident("dagpenger"),
                        personident = Personident("12345678910")
                    ) {
                        periode(2.sep, 13.sep, 300u, 300u)
                    }
                    assertEquals(expected, it)
                }
                .with(uid2.toString()) {
                    val expected = utbetaling(
                        action = Action.CREATE,
                        uid = uid2,
                        originalKey = originalKey,
                        sakId = sid2,
                        behandlingId = bid2,
                        fagsystem = Fagsystem.DAGPENGER,
                        lastPeriodeId = it.lastPeriodeId,
                        stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                        vedtakstidspunkt = it.vedtakstidspunkt,
                        beslutterId = Navident("dagpenger"),
                        saksbehandlerId = Navident("dagpenger"),
                        personident = Personident("12345678910")
                    ) {
                        periode(16.sep, 27.sep, 300u, 300u)
                    }
                    assertEquals(expected, it)
                }
                .with(uid3.toString()) {
                    val expected = utbetaling(
                        action = Action.CREATE,
                        uid = uid3,
                        originalKey = originalKey,
                        sakId = sid3,
                        behandlingId = bid3,
                        fagsystem = Fagsystem.DAGPENGER,
                        lastPeriodeId = it.lastPeriodeId,
                        stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                        vedtakstidspunkt = it.vedtakstidspunkt,
                        beslutterId = Navident("dagpenger"),
                        saksbehandlerId = Navident("dagpenger"),
                        personident = Personident("12345678910")
                    ) {
                        periode(30.sep, 10.okt, 300u, 300u)
                    }
                    assertEquals(expected, it)
                }
            TestRuntime.topics.saker.assertThat()
                .has(SakKey(sid1, Fagsystem.DAGPENGER))
                .has(SakKey(sid1, Fagsystem.DAGPENGER), setOf(uid1))
                .has(SakKey(sid2, Fagsystem.DAGPENGER))
                .has(SakKey(sid2, Fagsystem.DAGPENGER), setOf(uid2))
                .has(SakKey(sid3, Fagsystem.DAGPENGER))
                .has(SakKey(sid3, Fagsystem.DAGPENGER), setOf(uid3))
        }

        @Test
        fun `simuler 1 meldekort i 1 utbetalinger`() {
            val sid = SakId("$nextInt")
            val bid = BehandlingId("$nextInt")
            val originalKey = UUID.randomUUID().toString()
            val meldeperiode = "132460781"
            val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

            TestRuntime.topics.dp.produce(originalKey) {
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

            TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

            TestRuntime.topics.status.assertThat().isEmpty()
            TestRuntime.topics.utbetalinger.assertThat().isEmpty()
            TestRuntime.topics.oppdrag.assertThat().isEmpty()
            TestRuntime.topics.saker.assertThat().isEmpty()
            TestRuntime.topics.simulering.assertThat()
                .hasTotal(1)
                .has(originalKey)
                .with(originalKey) {
                    assertEquals("12345678910", it.request.oppdrag.oppdragGjelderId)
                    assertEquals("NY", it.request.oppdrag.kodeEndring)
                    assertEquals("DP", it.request.oppdrag.kodeFagomraade)
                    assertEquals(sid.id, it.request.oppdrag.fagsystemId)
                    assertEquals("MND", it.request.oppdrag.utbetFrekvens)
                    assertEquals("12345678910", it.request.oppdrag.oppdragGjelderId)
                    assertEquals("dagpenger", it.request.oppdrag.saksbehId)
                    assertEquals(1, it.request.oppdrag.oppdragslinjes.size)
                    assertNull(it.request.oppdrag.oppdragslinjes[0].refDelytelseId)
                    val l1 = it.request.oppdrag.oppdragslinjes[0]
                    assertEquals("NY", l1.kodeEndringLinje)
                    assertEquals("DPORAS", l1.kodeKlassifik)
                    assertEquals(553, l1.sats.toLong())
                }
        }

        @Test
        fun `test 1 meldekort i 1 utbetalinger blir til 1 utbetaling med 1 oppdrag`() {
            val sid = SakId("$nextInt")
            val bid = BehandlingId("$nextInt")
            val originalKey = UUID.randomUUID().toString()
            val meldeperiode = "132460781"
            val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

            TestRuntime.topics.dpUtbetalinger.produce(originalKey) {
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

            TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

            val mottatt = StatusReply(
                Status.MOTTATT,
                Detaljer(
                    ytelse = Fagsystem.DAGPENGER,
                    linjer = listOf(
                        DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "DPORAS"),
                    )
                )
            )
            TestRuntime.topics.status.assertThat()
                .has(originalKey)
                .has(originalKey, mottatt)

            TestRuntime.topics.utbetalinger.assertThat().isEmpty()

            val oppdrag = TestRuntime.topics.oppdrag.assertThat()
                .has(originalKey)
                .with(originalKey) {
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
                        assertEquals("DPORAS", a.kodeKlassifik)
                        assertEquals(553, a.sats.toLong())
                        assertEquals(1077, a.vedtakssats157.vedtakssats.toLong())
                        assertEquals(a.delytelseId, b.refDelytelseId)
                    }
                }
                .get(originalKey)

            TestRuntime.topics.oppdrag.produce(originalKey) {
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
                        periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
                    }
                    assertEquals(expected, it)
                }
            TestRuntime.topics.saker.assertThat()
                .has(SakKey(sid, Fagsystem.DAGPENGER), size = 1)
                .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid), index = 0)
        }
    }

