package abetal

import abetal.models.dpUId
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import models.*
import no.trygdeetaten.skjema.oppdrag.Mmel
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

internal class AbetalTest {

    @Test
    fun `with positive kvittering pending utbetalinger is persisted`() {
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

        TestRuntime.topics.saker.assertThat().isEmpty()

        //
        // SIMULATE A KVITTERING REKEYED JOINED AND PRODUCED TO OPPDRAG
        //
        TestRuntime.topics.oppdrag.produce(originalKey) { oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" }}}
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
            .with(SakKey(sid, Fagsystem.DAGPENGER)) {
                assertEquals(it, setOf(uid1))
            }
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 1) {
                assertEquals(it, setOf(uid1, uid2))
            }
    }

    @Test
    fun `lagrer ny sakId`() {
        val key = UUID.randomUUID().toString()
        val meldeperiode = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

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

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.status.assertThat().has(key)
        val oppdrag = TestRuntime.topics.oppdrag.assertThat().has(key).get(key)
        TestRuntime.topics.saker.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.oppdrag.produce(key) { oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } } }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER))
            .with(SakKey(sid, Fagsystem.DAGPENGER)) {
                assertEquals(uid, it.single())
            }

    }

    @Test
    fun `setter første utbetaling på sak til NY`() {
        val key = UUID.randomUUID().toString()
        val meldeperiode = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

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

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .with(key) { assertEquals("NY", it.oppdrag110.kodeEndring) }
            .get(key)
        TestRuntime.topics.status.assertThat().has(key)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.oppdrag.produce(key) { oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } } }
        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.saker.assertThat().has(SakKey(sid, Fagsystem.DAGPENGER))
    }

    @Test
    fun `setter andre utbetaling på sak til ENDR`() {
        val key = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val meldeperiode1 = UUID.randomUUID().toString()
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        val meldeperiode2 = UUID.randomUUID().toString()
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestRuntime.topics.utbetalinger.produce("$uid1") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = key,
                stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(1.jan, 2.jan, 100u)
            }
        }

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

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .with(key) { assertEquals("ENDR", it.oppdrag110.kodeEndring) }
            .get(key)
        TestRuntime.topics.status.assertThat().has(key)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid2.toString())
        TestRuntime.topics.oppdrag.produce(key) { oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } } }
        TestRuntime.topics.utbetalinger.assertThat().has(uid2.toString())
        TestRuntime.topics.saker.assertThat().has(SakKey(sid, Fagsystem.DAGPENGER), 3)
    }

    @Test
    fun `is idempotent`() {
        val key = UUID.randomUUID().toString()
        val meldeperiode = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestRuntime.topics.utbetalinger.produce("$uid") {
            utbetaling(
                action = Action.CREATE,
                uid = uid,
                sakId = sid,
                behandlingId = bid,
                originalKey = key,
                stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 2.jan.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(1.jan, 2.jan, 100u)
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

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.status.assertThat()
            .has(key)
            .with(key) {
                val expected = StatusReply(
                    Status.FEILET,
                    null,
                    ApiError(409, "periods already exists", "${DOC}opprett_en_utbetaling")
                )
                assertEquals(expected, it)
            }
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
            vedtakstidspunkt = java.time.LocalDateTime.now(),
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
            utbet.validate(null)
        }
        assertEquals("periode strekker seg over årsskifte", err.msg)
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
            vedtakstidspunkt = java.time.LocalDateTime.now(),
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
            utbet.validate(null)
        }
        assertEquals("sakId kan være maks 30 tegn langt", err.msg)
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
            vedtakstidspunkt = java.time.LocalDateTime.now(),
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
            utbet.validate(null)
        }
        assertEquals("behandlingId kan være maks 30 tegn langt", err.msg)
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
            behandlingId = BehandlingId("012345678901234567890123456789123"),
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = java.time.LocalDateTime.now(),
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
            utbet.validate(null)
        }
        assertEquals("kan ikke sende inn duplikate perioder", err.msg)
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
            vedtakstidspunkt = java.time.LocalDateTime.now(),
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
            utbet.validate(null)
        }
        assertEquals("kan ikke sende inn duplikate perioder", err.msg)
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
            vedtakstidspunkt = java.time.LocalDateTime.now(),
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
            utbet.validate(null)
        }
        assertEquals("fom må være før eller lik tom", err.msg)
    }

    @Test
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
            vedtakstidspunkt = java.time.LocalDateTime.now(),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = Navident("123"),
            saksbehandlerId = Navident("123"),
            periodetype = Periodetype.DAG,
            avvent = null,
            perioder = listOf(
                Utbetalingsperiode(
                    fom = java.time.LocalDate.now().nesteVirkedag(),
                    tom = java.time.LocalDate.now().nesteVirkedag(),
                    beløp = 100u,
                ),
            ),
        )

        val err = assertThrows<ApiError> {
            utbet.validate(null)
        }
        assertEquals("fremtidige utbetalinger er ikke støttet for periode dag/ukedag", err.msg)
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
            vedtakstidspunkt = java.time.LocalDateTime.now(),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = Navident("123"),
            saksbehandlerId = Navident("123"),
            periodetype = Periodetype.DAG,
            avvent = null,
            perioder = buildList<Utbetalingsperiode> {
                for (i in 1L..1001L) {
                    add(
                        Utbetalingsperiode(
                            fom = java.time.LocalDate.now().minusDays(i),
                            tom = java.time.LocalDate.now().minusDays(i),
                            beløp = 100u,
                        )
                    )
                }
            },
        )

        val err = assertThrows<ApiError> {
            utbet.validate(null)
        }
        assertEquals("DAG støtter maks periode på 1000 dager", err.msg)
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
            vedtakstidspunkt = java.time.LocalDateTime.now(),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = Navident("123"),
            saksbehandlerId = Navident("123"),
            periodetype = Periodetype.DAG,
            avvent = null,
            perioder = listOf(),
        )

        val err = assertThrows<ApiError> {
            utbet.validate(null)
        }
        assertEquals("perioder kan ikke være tom", err.msg)
    }

    @Test
    fun `get utbetaling from api`() {
        val key = UUID.randomUUID().toString()
        val meldeperiode = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

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

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .with(key) { assertEquals("NY", it.oppdrag110.kodeEndring) }
            .get(key)
        TestRuntime.topics.status.assertThat().has(key)
        TestRuntime.topics.pendingUtbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.oppdrag.produce(key) { oppdrag.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } } }
        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
        TestRuntime.topics.saker.assertThat().has(SakKey(sid, Fagsystem.DAGPENGER))
        val res = runBlocking {
            TestRuntime.ktor.httpClient.get("/api/utbetalinger/$uid") {
                accept(ContentType.Application.Json)
            }
        }

        assertEquals(HttpStatusCode.OK, res.status)
    }
}

