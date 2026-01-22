package abetal

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import models.*
import no.trygdeetaten.skjema.oppdrag.Mmel
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.DAGPENGER, 
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "DAGPENGER"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, 2377u, 779u, "DAGPENGER"),
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
            oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" }}
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
                Dp.meldekort(
                    meldeperiode = meldeperiode,
                    fom = 1.jan,
                    tom = 2.jan,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                )
            }
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
                Dp.meldekort(
                    meldeperiode = meldeperiode,
                    fom = 1.jan,
                    tom = 2.jan,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                )
            }
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
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = 1.jan,
                    tom = 2.jan,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                )
            }
        }
        TestRuntime.topics.status.assertThat().has(key)

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1)
        }
        val oppdrag1 = TestRuntime.topics.oppdrag.assertThat()
            .with(key, 0) { assertEquals("NY", it.oppdrag110.kodeEndring) }
            .get(key)
        TestRuntime.topics.oppdrag.produce(key, mapOf("uids" to "$uid1")) { 
            oppdrag1.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" }}
        }

        TestRuntime.topics.utbetalinger.assertThat().has(uid1.toString())

        TestRuntime.topics.dp.produce(key) { 
            Dp.utbetaling(sid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = 3.jan,
                    tom = 4.jan,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                )
            }
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
                Dp.meldekort(
                    meldeperiode = meldeperiode,
                    fom = 1.jan,
                    tom = 2.jan,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                )
            }
        }

        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.status.assertThat().has(key, StatusReply.ok())
    }

    @Test
    fun `error ved årsskifte`() {
        val utbet = Utbetaling(
            dryrun = false,
            originalKey = "123",
            fagsystem = Fagsystem.AAP,
            uid = randomUtbetalingId(),
            action = Action.CREATE,
            førsteUtbetalingPåSak = true,
            sakId = SakId("$nextInt"),
            behandlingId = BehandlingId("$nextInt"),
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = LocalDateTime.now(),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = Navident("123"),
            saksbehandlerId = Navident("123"),
            periodetype = Periodetype.EN_GANG,
            avvent = null,
            perioder = listOf(
                Utbetalingsperiode(31.des, 31.des, 100u),
                Utbetalingsperiode(1.jan, 1.jan, 100u),
            ),
        )

        val err = assertThrows<ApiError> {
            utbet.validate()
        }
        assertEquals("Engangsutbetalinger kan ikke strekke seg over årsskifte", err.msg)
    }

    @Test
    fun `error ved for lang sakId`() {
        val utbet = Utbetaling(
            dryrun = false,
            originalKey = "123",
            fagsystem = Fagsystem.AAP,
            uid = randomUtbetalingId(),
            action = Action.CREATE,
            førsteUtbetalingPåSak = true,
            sakId = SakId("012345678901234567890123456789123"),
            behandlingId = BehandlingId("$nextInt"),
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = LocalDateTime.now(),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = Navident("123"),
            saksbehandlerId = Navident("123"),
            periodetype = Periodetype.EN_GANG,
            avvent = null,
            perioder = listOf(
                Utbetalingsperiode(31.des, 31.des, 100u),
            ),
        )

        val err = assertThrows<ApiError> {
            utbet.validate()
        }
        assertEquals("Sak-ID må være mellom 1 og 25 tegn", err.msg)
    }

    @Test
    fun `error ved for lang behandlingId`() {
        val utbet = Utbetaling(
            dryrun = false,
            originalKey = "123",
            fagsystem = Fagsystem.AAP,
            uid = randomUtbetalingId(),
            action = Action.CREATE,
            førsteUtbetalingPåSak = true,
            sakId = SakId("$nextInt"),
            behandlingId = BehandlingId("012345678901234567890123456789123"),
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = LocalDateTime.now(),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = Navident("123"),
            saksbehandlerId = Navident("123"),
            periodetype = Periodetype.EN_GANG,
            avvent = null,
            perioder = listOf(
                Utbetalingsperiode(1.jan, 1.jan, 100u),
            ),
        )

        val err = assertThrows<ApiError> {
            utbet.validate()
        }
        assertEquals("Behandling-ID må være mellom 1 og 30 tegn", err.msg)
    }

    @Test
    fun `error ved to perioder med samme fom`() {
        val utbet = Utbetaling(
            dryrun = false,
            originalKey = "123",
            fagsystem = Fagsystem.AAP,
            uid = randomUtbetalingId(),
            action = Action.CREATE,
            førsteUtbetalingPåSak = true,
            sakId = SakId("$nextInt"),
            behandlingId = BehandlingId("0123456789012345678901234567"),
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = LocalDateTime.now(),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = Navident("123"),
            saksbehandlerId = Navident("123"),
            periodetype = Periodetype.DAG,
            avvent = null,
            perioder = listOf(
                Utbetalingsperiode(1.jan, 2.jan, 100u),
                Utbetalingsperiode(1.jan, 3.jan, 100u),
            ),
        )

        val err = assertThrows<ApiError> {
            utbet.validate()
        }
        assertEquals("Kan ikke sende inn duplikate perioder", err.msg)
    }

    @Test
    fun `error ved to perioder med samme tom`() {
        val utbet = Utbetaling(
            dryrun = false,
            originalKey = "123",
            fagsystem = Fagsystem.AAP,
            uid = randomUtbetalingId(),
            action = Action.CREATE,
            førsteUtbetalingPåSak = true,
            sakId = SakId("$nextInt"),
            behandlingId = BehandlingId("$nextInt"),
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = LocalDateTime.now(),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = Navident("123"),
            saksbehandlerId = Navident("123"),
            periodetype = Periodetype.DAG,
            avvent = null,
            perioder = listOf(
                Utbetalingsperiode(2.jan, 3.jan, 100u),
                Utbetalingsperiode(1.jan, 3.jan, 100u),
            ),
        )

        val err = assertThrows<ApiError> {
            utbet.validate()
        }
        assertEquals("Kan ikke sende inn duplikate perioder", err.msg)
    }

    @Test
    fun `error ved tom før fom`() {
        val utbet = Utbetaling(
            dryrun = false,
            originalKey = "123",
            fagsystem = Fagsystem.AAP,
            uid = randomUtbetalingId(),
            action = Action.CREATE,
            førsteUtbetalingPåSak = true,
            sakId = SakId("$nextInt"),
            behandlingId = BehandlingId("$nextInt"),
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = LocalDateTime.now(),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = Navident("123"),
            saksbehandlerId = Navident("123"),
            periodetype = Periodetype.DAG,
            avvent = null,
            perioder = listOf(
                Utbetalingsperiode(5.jan, 3.jan, 100u),
            ),
        )

        val err = assertThrows<ApiError> {
            utbet.validate()
        }
        assertEquals("Tom må være >= fom", err.msg)
    }

    @Test
    @Disabled
    //I forbindelse med jul må det gå an å utbetale en meldeperiode på Dagpenger og AAP før datoene i perioden har passert.
    fun `error ved ulovlig fremtidig utbetaling`() {
        val utbet = Utbetaling(
            dryrun = false,
            originalKey = "123",
            fagsystem = Fagsystem.AAP,
            uid = randomUtbetalingId(),
            action = Action.CREATE,
            førsteUtbetalingPåSak = true,
            sakId = SakId("$nextInt"),
            behandlingId = BehandlingId("$nextInt"),
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = LocalDateTime.now(),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = Navident("123"),
            saksbehandlerId = Navident("123"),
            periodetype = Periodetype.DAG,
            avvent = null,
            perioder = listOf(
                Utbetalingsperiode(
                    fom = LocalDate.now().nesteVirkedag(),
                    tom = LocalDate.now().nesteVirkedag(),
                    beløp = 100u,
                ),
            ),
        )

        val err = assertThrows<ApiError> {
            utbet.validate()
        }
        assertEquals("Fremtidige utbetalinger er ikke støttet for dag/ukedag", err.msg)
    }

    @Test
    fun `error ved for lange perioder`() {
        val utbet = Utbetaling(
            dryrun = false,
            originalKey = "123",
            fagsystem = Fagsystem.AAP,
            uid = randomUtbetalingId(),
            action = Action.CREATE,
            førsteUtbetalingPåSak = true,
            sakId = SakId("$nextInt"),
            behandlingId = BehandlingId("$nextInt"),
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = LocalDateTime.now(),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = Navident("123"),
            saksbehandlerId = Navident("123"),
            periodetype = Periodetype.DAG,
            avvent = null,
            perioder = buildList<Utbetalingsperiode> {
                for (i in 1L..1001L) {
                    add(
                        Utbetalingsperiode(
                            fom = LocalDate.now().minusDays(i),
                            tom = LocalDate.now().minusDays(i),
                            beløp = 100u,
                        )
                    )
                }
            },
        )

        val err = assertThrows<ApiError> {
            utbet.validate()
        }
        assertEquals("Utbetalinger kan ikke strekke seg over 1000 dager", err.msg)
    }

    @Test
    @Disabled
    fun `error ved helgedager i perioder med periodetype DAG`() {
        val utbet = Utbetaling(
            dryrun = false,
            originalKey = "123",
            fagsystem = Fagsystem.AAP,
            uid = randomUtbetalingId(),
            action = Action.CREATE,
            førsteUtbetalingPåSak = true,
            sakId = SakId("$nextInt"),
            behandlingId = BehandlingId("$nextInt"),
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = LocalDateTime.now(),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = Navident("123"),
            saksbehandlerId = Navident("123"),
            periodetype = Periodetype.UKEDAG,
            avvent = null,
            perioder = listOf(
                Utbetalingsperiode(
                    fom = LocalDate.of(2024, 1, 5),
                    tom = LocalDate.of(2024, 1, 8),
                    beløp = 100u
                ),
            ),
        )

        val err = assertThrows<ApiError> {
            utbet.validate()
        }
        assertEquals("periodetype DAG kan ikke inneholde helgedager (lørdag/søndag)", err.msg)
    }

    @Test
    fun `error ved manglende perioder`() {
        val utbet = Utbetaling(
            dryrun = false,
            originalKey = "123",
            fagsystem = Fagsystem.AAP,
            uid = randomUtbetalingId(),
            action = Action.CREATE,
            førsteUtbetalingPåSak = true,
            sakId = SakId("$nextInt"),
            behandlingId = BehandlingId("$nextInt"),
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = LocalDateTime.now(),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = Navident("123"),
            saksbehandlerId = Navident("123"),
            periodetype = Periodetype.DAG,
            avvent = null,
            perioder = listOf(),
        )

        val err = assertThrows<ApiError> {
            utbet.validate()
        }
        assertEquals("Mangler perioder", err.msg)
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
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = 10.jun,
                    tom = 14.jun,
                    sats = 500u
                )
            }
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
                originalDays + newDays
            }
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
                Dp.meldekort(
                    meldeperiode = meldeperiode,
                    fom = 1.jan,
                    tom = 2.jan,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                )
            }
        }

        TestRuntime.topics.oppdrag.assertThat()
            .has(key)
            .with(key, 0) {
                assertEquals("DP", it.oppdrag110.avstemming115.kodeKomponent)
                val todayAtTen = LocalDateTime.now().with(LocalTime.of(10, 10, 0, 0)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS"))
                assertEquals(todayAtTen, it.oppdrag110.avstemming115.nokkelAvstemming)
                assertEquals(todayAtTen, it.oppdrag110.avstemming115.tidspktMelding)
            }

        TestRuntime.topics.status.assertThat()
            .has(key)
            .with(key) {
                StatusReply(Status.MOTTATT, Detaljer(ytelse = Fagsystem.DAGPENGER, linjer = listOf(
                    DetaljerLinje(bid.id, 1.jan, 1.jan, 100u, 100u, "DAGPENGER"),
                )))
            }
    }
}

