package abetal

import abetal.dp.*
import models.*
import no.trygdeetaten.skjema.oppdrag.Mmel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.test.*

internal class AbetalTest {

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
    fun `with positive kvittering pending utbetalinger is persisted`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGER)
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.DAGPENGER)

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
        TestRuntime.topics.pendingUtbetalinger.assertThat()
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
                assertEquals(2, it.oppdrag110.oppdragsLinje150s.size)
                val førsteLinje = it.oppdrag110.oppdragsLinje150s[0]
                assertNull(førsteLinje.refDelytelseId)
                assertEquals("NY", førsteLinje.kodeEndringLinje)
                assertEquals(bid.id, førsteLinje.henvisning)
                assertEquals("DAGPENGER", førsteLinje.kodeKlassifik)
                assertEquals(553, førsteLinje.sats.toLong())
                assertEquals(1077, førsteLinje.vedtakssats157.vedtakssats.toLong())
                val andreLinje = it.oppdrag110.oppdragsLinje150s[1]
                assertEquals("NY", andreLinje.kodeEndringLinje)
                assertEquals(bid.id, andreLinje.henvisning)
                assertEquals("DAGPENGER", andreLinje.kodeKlassifik)
                assertEquals(779, andreLinje.sats.toLong())
                assertEquals(2377, andreLinje.vedtakssats157.vedtakssats.toLong())
            }
            .get(transactionId)

        //
        // SIMULATE A KVITTERING REKEYED JOINED AND PRODUCED TO OPPDRAG
        //
        TestRuntime.topics.oppdrag.produce(transactionId, mapOf("uids" to "$uid1,$uid2")) {
            oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
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
        // TestRuntime.topics.saker.assertThat()
        //     .has(SakKey(sid, Fagsystem.DAGPENGER), size = 2)
        //     .with(SakKey(sid, Fagsystem.DAGPENGER)) {
        //         assertEquals(it, setOf(uid1))
        //     }
        //     .with(SakKey(sid, Fagsystem.DAGPENGER), index = 1) {
        //         assertEquals(it, setOf(uid1, uid2))
        //     }
    }

    @Test
    fun `lagrer ny sakId`() {
        val key = UUID.randomUUID().toString()
        val meldeperiode = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(key) {
            Dp.utbetaling(sid.id) {
                meldekort(
                    meldeperiode = meldeperiode,
                    fom = 1.jan,
                    tom = 2.jan,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                )
            }.asBytes()
        }


        TestRuntime.topics.status.assertThat().has(key)
        val oppdrag = TestRuntime.topics.oppdrag.assertThat().has(key).get(key)
        // TestRuntime.topics.saker.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.oppdrag.produce(key) { oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } } }
        // TestRuntime.topics.saker.assertThat()
        //     .has(SakKey(sid, Fagsystem.DAGPENGER))
        //     .with(SakKey(sid, Fagsystem.DAGPENGER)) {
        //         assertEquals(uid, it.single())
        //     }

    }

    @Test
    fun `setter første utbetaling på sak til NY`() {
        val key = UUID.randomUUID().toString()
        val meldeperiode = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(key) {
            Dp.utbetaling(sid.id) {
                meldekort(
                    meldeperiode = meldeperiode,
                    fom = 1.jan,
                    tom = 2.jan,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                )
            }.asBytes()
        }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .with(key) { assertEquals("NY", it.oppdrag110.kodeEndring) }
            .get(key)
        TestRuntime.topics.status.assertThat().has(key)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.oppdrag.produce(key, mapOf("uids" to "$uid")) {
            oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
        }
        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
        // TestRuntime.topics.saker.assertThat().has(SakKey(sid, Fagsystem.DAGPENGER))
    }

    @Test
    fun `setter andre utbetaling på sak til ENDR`() {
        val key = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val meldeperiode1 = UUID.randomUUID().toString()
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.DAGPENGER)
        val meldeperiode2 = UUID.randomUUID().toString()
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(key) {
            Dp.utbetaling(sid.id) {
                meldekort(
                    meldeperiode = meldeperiode1,
                    fom = 1.jan,
                    tom = 2.jan,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                )
            }.asBytes()
        }
        TestRuntime.topics.status.assertThat().has(key)

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1)
        }
        val oppdrag1 = TestRuntime.topics.oppdrag.assertThat()
            .with(key, 0) { assertEquals("NY", it.oppdrag110.kodeEndring) }
            .get(key)
        TestRuntime.topics.oppdrag.produce(key, mapOf("uids" to "$uid1")) {
            oppdrag1.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
        }

        TestRuntime.topics.utbetalinger.assertThat().has(uid1.toString())

        TestRuntime.topics.dp.produce(key) {
            Dp.utbetaling(sid.id) {
                meldekort(
                    meldeperiode = meldeperiode2,
                    fom = 3.jan,
                    tom = 4.jan,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                )
            }.asBytes()
        }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .with(key, 0) { assertEquals("ENDR", it.oppdrag110.kodeEndring) }
            .get(key)

        TestRuntime.topics.status.assertThat().has(key)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid2.toString())
        TestRuntime.topics.oppdrag.produce(key, mapOf("uids" to "$uid2")) {
            oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
        }
        TestRuntime.topics.utbetalinger.assertThat().has(uid2.toString())
    }

    @Test
    fun `is idempotent`() {
        val key = UUID.randomUUID().toString()
        val meldeperiode = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.utbetalinger.produce("$uid") {
            utbetaling(
                action = Action.CREATE,
                uid = uid,
                sakId = sid,
                behandlingId = bid,
                originalKey = key,
                stønad = StønadTypeDagpenger.DAGPENGER,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 2.jan.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(1.jan, 2.jan, 100u, 100u)
            }
        }

        TestRuntime.topics.dp.produce(key) {
            Dp.utbetaling(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 2.jun.atStartOfDay(),
            ) {
                meldekort(
                    meldeperiode = meldeperiode,
                    fom = 1.jan,
                    tom = 2.jan,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                )
            }.asBytes()
        }

        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.status.assertThat().has(key, StatusReply.ok())
    }

    @Test
    fun `send inn kjede med ny meldeperiode på eksisterende sak `() {
        val sakId = SakId("15507598")
        val behandlingId1 = BehandlingId("AZiYMlhDege3YrU10jE4vw==")
        val behandlingId2 = BehandlingId("AZiYV47lclqGSyZS1/R9mQ==")
        val transactionId1 = UUID.randomUUID().toString()
        val transactionId2 = UUID.randomUUID().toString()
        val meldeperiode1 = "2024-06-10"
        val meldeperiode2 = "2024-06-24"
        val uid1 = dpUId(sakId.id, meldeperiode1, StønadTypeDagpenger.DAGPENGER)
        val uid2 = dpUId(sakId.id, meldeperiode2, StønadTypeDagpenger.DAGPENGER)

        TestRuntime.topics.dp.produce(transactionId1) {
            Dp.utbetaling(
                sakId = sakId.id,
                behandlingId = behandlingId1.id
            ) {
                meldekort(
                    meldeperiode = meldeperiode1,
                    fom = 10.jun,
                    tom = 14.jun,
                    sats = 500u
                )
            }.asBytes()
        }
        val oppdrag1 = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId1)
            .get(transactionId1)
        TestRuntime.topics.oppdrag.produce(transactionId1, mapOf("uids" to "$uid1")) {
            oppdrag1.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
        }

        TestRuntime.topics.utbetalinger.assertThat().has(uid1.toString())
        TestRuntime.topics.saker.produce(SakKey(sakId, Fagsystem.DAGPENGER)) {
            setOf(uid1)
        }

        TestRuntime.topics.dp.produce(transactionId2) {
            Dp.utbetaling(
                sakId = sakId.id,
                behandlingId = behandlingId2.id
            ) {
                val originalDays = Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = 10.jun,
                    tom = 14.jun,
                    sats = 500u
                )
                val newDays = Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = 24.jun,
                    tom = 28.jun,
                    sats = 550u
                )
                addAll(originalDays + newDays)
            }.asBytes()
        }

        TestRuntime.topics.oppdrag.assertThat().has(transactionId2)
            .with(transactionId2) { oppdrag2 ->
                assertEquals("ENDR", oppdrag2.oppdrag110.kodeEndring)
                assertEquals(1, oppdrag2.oppdrag110.oppdragsLinje150s.size)
                val linje = oppdrag2.oppdrag110.oppdragsLinje150s.first()
                assertEquals(behandlingId2.id, linje.henvisning)
                assertEquals(550, linje.sats.toLong())
            }

        TestRuntime.topics.status.assertThat()
            .has(transactionId2)
            .with(transactionId2) {
                assertEquals(Status.MOTTATT, it.status)
                assertNull(it.error)
                assertEquals(1, it.detaljer?.linjer?.size)
                assertEquals(behandlingId2.id, it.detaljer?.linjer?.first()?.behandlingId)
            }

        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid2.toString())
    }

    @Test
    fun `avstemming115 er påkrevd`() {
        val key = UUID.randomUUID().toString()
        val meldeperiode = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")

        TestRuntime.topics.dp.produce(key) {
            Dp.utbetaling(sid.id, bid.id) {
                meldekort(
                    meldeperiode = meldeperiode,
                    fom = 1.jan,
                    tom = 2.jan,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                )
            }.asBytes()
        }

        TestRuntime.topics.oppdrag.assertThat()
            .has(key)
            .with(key, 0) {
                assertEquals("DP", it.oppdrag110.avstemming115.kodeKomponent)
                val todayAtTen = LocalDateTime.now().with(LocalTime.of(10, 10, 0, 0))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS"))
                assertEquals(todayAtTen, it.oppdrag110.avstemming115.nokkelAvstemming)
                val tidspktMelding = LocalDateTime.parse(
                    it.oppdrag110.avstemming115.tidspktMelding,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
                )
                assertTrue(tidspktMelding.isBefore(LocalDateTime.now()))
                assertTrue(tidspktMelding.isAfter(LocalDateTime.now().minusMinutes(1)))
            }

        TestRuntime.topics.status.assertThat()
            .has(key)
            .with(key) {
                StatusReply(
                    Status.MOTTATT, Detaljer(
                        ytelse = Fagsystem.DAGPENGER, linjer = listOf(
                            DetaljerLinje(bid.id, 1.jan, 1.jan, 100u, 100u, "DAGPENGER"),
                        )
                    )
                )
            }
    }

    @Test
    fun `oppdrag med kvittering uten uids produserer ikke nye meldinger`() {
        val key = UUID.randomUUID().toString()
        val uid = randomUtbetalingId()

        val oppdrag = OppdragService.opprett(utbetaling(Action.CREATE, uid) {
            periode(1.jan, 3.jan, 123u)
        }).apply {
            mmel = Mmel().apply { alvorlighetsgrad = "00" }
        }

        TestRuntime.topics.oppdrag.produce(key) { oppdrag }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.retryOppdrag.assertThat().isEmpty()
    }

    @Test
    fun `oppdrag med kvittering og uids produserer til utbetalinger`() {
        val key = UUID.randomUUID().toString()
        val uid = randomUtbetalingId()

        val utbetaling = utbetaling(Action.CREATE, uid) {
            // empty periods
        }

        TestRuntime.topics.pendingUtbetalinger.produce(uid.toString()) { utbetaling }

        val oppdrag = OppdragService.opprett(utbetaling).apply {
            mmel = Mmel().apply { alvorlighetsgrad = "00" }
        }

        TestRuntime.topics.oppdrag.produce(key, mapOf("uids" to uid.toString())) { oppdrag }

        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString(), utbetaling)
        TestRuntime.topics.retryOppdrag.assertThat().isEmpty()
    }

    @Test
    fun `oppdrag med kvittering med uids uten pending prøver på nytt`() {
        val key = UUID.randomUUID().toString()
        val uid = randomUtbetalingId()
        val utbetaling = utbetaling(Action.CREATE, uid) {
            periode(1.jan, 3.jan, 123u)
        }

        val oppdrag = OppdragService.opprett(utbetaling).apply {
            mmel = Mmel().apply { alvorlighetsgrad = "00" }
        }

        TestRuntime.topics.oppdrag.produce(key, mapOf("uids" to uid.toString(), "maxRetries" to "1")) { oppdrag }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.retryOppdrag.assertThat().with(key) {
            assertEquals(oppdrag.oppdrag110.fagsystemId, it.oppdrag110.fagsystemId)
        }
    }

    @Test
    fun `retry med pending produserer til utbetaling`() {
        val key = UUID.randomUUID().toString()
        val uid = randomUtbetalingId()
        val utbetaling = utbetaling(Action.CREATE, uid) {
            periode(1.jan, 3.jan, 123u)
        }

        val oppdrag = OppdragService.opprett(utbetaling).apply {
            mmel = Mmel().apply { alvorlighetsgrad = "00" }
        }

        TestRuntime.topics.pendingUtbetalinger.produce(uid.toString()) { utbetaling }
        TestRuntime.topics.retryOppdrag.produce(key, mapOf("uids" to uid.toString())) { oppdrag }
        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString(), utbetaling)
    }
}
