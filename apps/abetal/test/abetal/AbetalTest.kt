package abetal

import abetal.models.dpUId
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import models.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

internal class AbetalTest {

    @Test
    fun `lagrer ny sakId`() {
        val key = UUID.randomUUID().toString()
        val meldeperiode = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestTopics.dp.produce(key) {
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

        TestTopics.status.assertThat().has(key)
        TestTopics.oppdrag.assertThat().has(key)
        TestTopics.utbetalinger.assertThat().has(uid.toString())
        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER))
            .with(SakKey(sid, Fagsystem.DAGPENGER)) {
                assertEquals(uid, it.single())
            }

    }

    @Test
    @Disabled
    fun `appender uid på eksisterende sakId`() {
    }

    @Test
    fun `setter første utbetaling på sak til NY`() {
        val key = UUID.randomUUID().toString()
        val meldeperiode = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestTopics.dp.produce(key) { 
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

        TestTopics.oppdrag.assertThat()
            .with(key) {
                assertEquals("NY", it.oppdrag110.kodeEndring)
            }
        TestTopics.status.assertThat().has(key)
        TestTopics.utbetalinger.assertThat().has(uid.toString())
        TestTopics.saker.assertThat().has(SakKey(sid, Fagsystem.DAGPENGER))
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

        TestTopics.utbetalinger.produce("$uid1") {
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

        TestTopics.dp.produce(key) { 
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

        TestTopics.status.assertThat().has(key)
        TestTopics.utbetalinger.assertThat().has(uid2.toString())
        TestTopics.saker.assertThat().has(SakKey(sid, Fagsystem.DAGPENGER), 3)
        TestTopics.oppdrag.assertThat()
            .with(key) {
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
            }
    }

    @Test
    fun `is idempotent`() {
        val key = UUID.randomUUID().toString()
        val meldeperiode = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestTopics.utbetalinger.produce("$uid") {
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

        TestTopics.dp.produce(key) { 
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

        TestTopics.oppdrag.assertThat().isEmpty()
        TestTopics.utbetalinger.assertThat().isEmpty()

        TestTopics.status.assertThat()
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

        TestTopics.dp.produce(key) { 
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

        TestTopics.oppdrag.assertThat()
            .with(key) {
                assertEquals("NY", it.oppdrag110.kodeEndring)
            }
        TestTopics.status.assertThat().has(key)
        TestTopics.utbetalinger.assertThat().has(uid.toString())
        TestTopics.saker.assertThat().has(SakKey(sid, Fagsystem.DAGPENGER))
        val res = runBlocking {
            httpClient.get("/api/utbetalinger/$uid") {
                accept(ContentType.Application.Json)
            }
        }

        assertEquals(HttpStatusCode.OK, res.status)
    }
}

