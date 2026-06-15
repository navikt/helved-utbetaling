package abetal.valp

import abetal.*
import libs.kafka.KotlinxSerializer
import kotlinx.serialization.serializer
import libs.kafka.KotlinxDeserializer
import models.*
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class ValpTest : ConsumerTestBase() {

    @Test
    fun `can serialize and deserialize`() {
        val original = Valp.utbetaling(
            uid = UtbetalingId(UUID.randomUUID()),
            sakId = "123",
            behandlingId = "123",
            besluttetTidspunkt = 14.jun.atStartOfDay().toInstant(ZoneOffset.UTC),
            belop = 0u,
        ) {
            ValpUtbetaling.Periode(3.jun, 7.jun)
        }
        val bytes = KotlinxSerializer(serializer<ValpUtbetaling>()).serialize("topic", original)
        val decoded = KotlinxDeserializer(serializer<ValpUtbetaling>()).deserialize("topic", bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `create - utbetaling on internal topic`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.valpIntern.produce(transactionId) {
            Valp.utbetaling(uid, sid.id, bid.id, belop = 1077u) {
                ValpUtbetaling.Periode(
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                )
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(transactionId)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .get(transactionId)

        kvitterOk(transactionId, oppdrag, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
    }

    @Test
    fun `update - modifying existing utbetaling`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val periodeId = PeriodeId()

        val existingUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            behandlingId = BehandlingId("$nextInt"),
            originalKey = UUID.randomUUID().toString(),
            stønad = StønadTypeValp.HOYERE_UTDANNING_STUDIEREISE,
            periodetype = Periodetype.EN_GANG,
            lastPeriodeId = periodeId,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 6.jun.atStartOfDay(),
            beslutterId = Navident("teamvalp"),
            saksbehandlerId = Navident("teamvalp"),
            fagsystem = Fagsystem.VALP,
        ) {
            periode(3.jun, 6.jun, 1000u, null)
        }
        TestRuntime.topics.utbetalinger.produce(uid.toString(), existingUtbetaling)
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.VALP), setOf(uid))

        TestRuntime.topics.valp.produce(transactionId) {
            Valp.utbetaling(
                uid = uid,
                sakId = sid.id,
                behandlingId = bid.id,
                besluttetTidspunkt = 7.jun.atStartOfDay().toInstant(ZoneOffset.UTC),
                belop = 1500u,
            ) {
                ValpUtbetaling.Periode(3.jun, 7.jun)
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(transactionId) {
            Valp.mottatt {
                linje(bid, 3.jun, 7.jun, 1500u)
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val expected = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId,
            førsteUtbetalingPåSak = false,
            fagsystem = Fagsystem.VALP,
            periodetype = Periodetype.EN_GANG,
            stønad = StønadTypeValp.HOYERE_UTDANNING_STUDIEREISE,
            beslutterId = Navident("teamvalp"),
            saksbehandlerId = Navident("teamvalp"),
            personident = Personident("12345678910")
        ) {
            periode(3.jun, 7.jun, 1500u, null)
        }
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(expected.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt, sistePeriode = it.sistePeriode), it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.assertBasics("ENDR", "TILSOPP", sid.id, expectedLines = 1, utbetFrekvens = "MND")
                assertEquals("teamvalp", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "TTOHOYUDSTUDREIS",
                    sats = 1500,
                    refDelytelseId = periodeId.toString()
                )
            }
            .get(transactionId)

        kvitterOk(transactionId, oppdrag, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(expected.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt, sistePeriode = it.sistePeriode), it)
            }
    }

    @Test
    fun `update - extending periode on existing utbetaling`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val periodeId = PeriodeId()

        val existingUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            behandlingId = bid,
            originalKey = UUID.randomUUID().toString(),
            stønad = StønadTypeValp.HOYERE_UTDANNING_STUDIEREISE,
            periodetype = Periodetype.EN_GANG,
            lastPeriodeId = periodeId,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 1.jun.atStartOfDay(),
            beslutterId = Navident("teamvalp"),
            saksbehandlerId = Navident("teamvalp"),
            fagsystem = Fagsystem.VALP,
        ) {
            periode(1.jun, 15.jun, 1500u, null)
        }
        TestRuntime.topics.utbetalinger.produce(uid.toString(), existingUtbetaling)
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.VALP), setOf(uid))

        TestRuntime.topics.valp.produce(transactionId) {
            Valp.utbetaling(
                uid = uid,
                sakId = sid.id,
                behandlingId = bid.id,
                besluttetTidspunkt = 1.jun.atStartOfDay().toInstant(ZoneOffset.UTC),
                belop = 3000u,
            ) {
                ValpUtbetaling.Periode(1.jun, 30.jun)
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(transactionId) {
            Valp.mottatt {
                linje(bid, 1.jun, 30.jun, 3000u)
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val expected = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId,
            førsteUtbetalingPåSak = false,
            fagsystem = Fagsystem.VALP,
            periodetype = Periodetype.EN_GANG,
            stønad = StønadTypeValp.HOYERE_UTDANNING_STUDIEREISE,
            beslutterId = Navident("teamvalp"),
            saksbehandlerId = Navident("teamvalp"),
            personident = Personident("12345678910")
        ) {
            periode(1.jun, 30.jun, 3000u, null)
        }
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(expected.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt, sistePeriode = it.sistePeriode), it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.assertBasics("ENDR", "TILSOPP", sid.id, expectedLines = 1, utbetFrekvens = "MND")
                assertEquals("teamvalp", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "TTOHOYUDSTUDREIS",
                    sats = 3000,
                    refDelytelseId = periodeId.toString()
                )
            }
            .get(transactionId)

        kvitterOk(transactionId, oppdrag, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(expected.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt, sistePeriode = it.sistePeriode), it)
            }
    }

    @Test
    fun `update - changing periode on existing utbetaling`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val periodeId = PeriodeId()

        val existingUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            behandlingId = bid,
            originalKey = UUID.randomUUID().toString(),
            stønad = StønadTypeValp.HOYERE_UTDANNING_STUDIEREISE,
            periodetype = Periodetype.EN_GANG,
            lastPeriodeId = periodeId,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 1.jun.atStartOfDay(),
            beslutterId = Navident("teamvalp"),
            saksbehandlerId = Navident("teamvalp"),
            fagsystem = Fagsystem.VALP,
        ) {
            periode(1.jun, 30.jun, 3000u, null)
        }
        TestRuntime.topics.utbetalinger.produce(uid.toString(), existingUtbetaling)
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.VALP), setOf(uid))

        TestRuntime.topics.valp.produce(transactionId) {
            Valp.utbetaling(
                uid = uid,
                sakId = sid.id,
                behandlingId = bid.id,
                besluttetTidspunkt = 1.jun.atStartOfDay().toInstant(ZoneOffset.UTC),
                belop = 3070u,
            ) {
                ValpUtbetaling.Periode(1.jun, 30.jun)
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(transactionId) {
            Valp.mottatt {
                linje(bid, 1.jun, 30.jun, 3070u)
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val expected = utbetaling(
            action = Action.UPDATE,
            uid = uid,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId,
            førsteUtbetalingPåSak = false,
            fagsystem = Fagsystem.VALP,
            periodetype = Periodetype.EN_GANG,
            stønad = StønadTypeValp.HOYERE_UTDANNING_STUDIEREISE,
            beslutterId = Navident("teamvalp"),
            saksbehandlerId = Navident("teamvalp"),
            personident = Personident("12345678910")
        ) {
            periode(1.jun, 30.jun, 3070u, null)
        }
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(expected.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt, sistePeriode = it.sistePeriode), it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.assertBasics("ENDR", "TILSOPP", sid.id, expectedLines = 1, utbetFrekvens = "MND")
                assertEquals("teamvalp", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "TTOHOYUDSTUDREIS",
                    sats = 3070,
                    refDelytelseId = periodeId.toString()
                )
            }
            .get(transactionId)

        kvitterOk(transactionId, oppdrag, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(expected.copy(lastPeriodeId = it.lastPeriodeId, vedtakstidspunkt = it.vedtakstidspunkt, sistePeriode = it.sistePeriode), it)
            }
    }

    @Test
    fun `opphør - empty periode list cancels utbetaling`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val periodeId = PeriodeId()

        val existingUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId,
            stønad = StønadTypeValp.HOYERE_UTDANNING_STUDIEREISE,
            lastPeriodeId = periodeId,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("teamvalp"),
            saksbehandlerId = Navident("teamvalp"),
            fagsystem = Fagsystem.VALP,
            periodetype = Periodetype.EN_GANG
        ) {
            periode(2.jun, 13.jun, 100u, null)
        }
        TestRuntime.topics.utbetalinger.produce("$uid", existingUtbetaling)

        TestRuntime.topics.valp.produce(transactionId) {
            Valp.utbetaling(
                uid = uid,
                sakId = sid.id,
                behandlingId = bid.id,
                besluttetTidspunkt = 14.jun.atStartOfDay().toInstant(ZoneOffset.UTC),
                belop = 0u,
            ) {
                ValpUtbetaling.Periode(fom = 2.jun, tom = 13.jun)
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(transactionId) {
            Valp.mottatt {
                linje(bid, 2.jun, 13.jun, 0u)
            }
        }

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val expected = utbetaling(
            action = Action.DELETE,
            uid = uid,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId,
            fagsystem = Fagsystem.VALP,
            stønad = StønadTypeValp.HOYERE_UTDANNING_STUDIEREISE,
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("teamvalp"),
            saksbehandlerId = Navident("teamvalp"),
            personident = Personident("12345678910"),
            periodetype = Periodetype.EN_GANG,
        ) {
            periode(2.jun, 13.jun, 100u, null)
        }
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(expected.copy(lastPeriodeId = it.lastPeriodeId, sistePeriode = it.sistePeriode), it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.assertBasics("ENDR", "TILSOPP", sid.id, expectedLines = 1, utbetFrekvens = "MND")
                assertEquals("teamvalp", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
                    assertEquals(2.jun, it.datoStatusFom.toLocalDate())
                    assertNull(it.refDelytelseId)
                    assertEquals("ENDR", it.kodeEndringLinje)
                    assertEquals(bid.id, it.henvisning)
                    assertEquals("TTOHOYUDSTUDREIS", it.kodeKlassifik)
                    assertEquals(100, it.sats.toLong())
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId)

        kvitterOk(transactionId, oppdrag, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                assertEquals(expected.copy(lastPeriodeId = it.lastPeriodeId, sistePeriode = it.sistePeriode), it)
            }
    }

    @Test
    fun `simulation - no changes produces no oppdrag`() {
        val transactionId = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val uid = UtbetalingId(UUID.randomUUID())

        val existingUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId,
            stønad = StønadTypeValp.HOYERE_UTDANNING_STUDIEREISE,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("teamvalp"),
            saksbehandlerId = Navident("teamvalp"),
            fagsystem = Fagsystem.VALP,
        ) {
            periode(1.jan, 2.jan, 100u, null)
        }
        TestRuntime.topics.utbetalinger.produce("$uid", existingUtbetaling)

        TestRuntime.topics.valp.produce(transactionId) {
            Valp.utbetaling(uid, sid.id, bid.id, belop = 100u, dryrun = true) {
                ValpUtbetaling.Periode(
                    fom = 1.jan,
                    tom = 2.jan,
                )
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .with(transactionId) { statusReply ->
                assertEquals(Status.OK, statusReply.status)
            }

        TestRuntime.topics.simulering.assertThat().hasNot(transactionId)
    }


    @Test
    fun `update - changing sakId on utbetaling with duplicate perioder`() {
        val sid1 = SakId("$nextInt")
        val sid2 = SakId("$nextInt")
        val bid1 = BehandlingId("$nextInt")
        val bid2 = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val periodeId = PeriodeId()

        val existingUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid1,
            behandlingId = bid1,
            originalKey = UUID.randomUUID().toString(),
            stønad = StønadTypeValp.HOYERE_UTDANNING_STUDIEREISE,
            periodetype = Periodetype.UKEDAG,
            lastPeriodeId = periodeId,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 6.jun.atStartOfDay(),
            beslutterId = Navident("teamvalp"),
            saksbehandlerId = Navident("teamvalp"),
            fagsystem = Fagsystem.VALP,
        ) {
            periode(8.jun, 10.jun, 500u, null)
        }
        TestRuntime.topics.utbetalinger.produce("$uid", existingUtbetaling)
        TestRuntime.topics.saker.produce(SakKey(sid1, Fagsystem.VALP), setOf(uid))

        TestRuntime.topics.valp.produce(transactionId) {
            Valp.utbetaling(
                uid = uid,
                sakId = sid2.id,
                behandlingId = bid2.id,
                besluttetTidspunkt = 7.jun.atStartOfDay().toInstant(ZoneOffset.UTC),
                belop = 500u,
            ) {
                ValpUtbetaling.Periode(8.jun, 10.jun)
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat().has(transactionId) {
            Valp.feilet(ApiError(400, "Kan ikke endre 'sakId'", DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_SAK_ID.doc))
        }
    }

    @Test
    fun `simulation - error sets simulering flag on status`() {
        val sid1 = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val periodeId = PeriodeId()

        val existingUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid1,
            behandlingId = bid,
            originalKey = UUID.randomUUID().toString(),
            stønad = StønadTypeValp.HOYERE_UTDANNING_STUDIEREISE,
            periodetype = Periodetype.EN_GANG,
            lastPeriodeId = periodeId,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 6.jun.atStartOfDay(),
            beslutterId = Navident("teamvalp"),
            saksbehandlerId = Navident("teamvalp"),
            fagsystem = Fagsystem.VALP,
        ) {
            periode(1.jun, 5.jun, 1000u, null)
        }
        TestRuntime.topics.utbetalinger.produce("$uid", existingUtbetaling)
        TestRuntime.topics.saker.produce(SakKey(sid1, Fagsystem.VALP), setOf(uid))

        TestRuntime.topics.valp.produce(transactionId) {
            Valp.utbetaling(
                uid = uid,
                sakId = sid1.id,
                behandlingId = bid.id,
                belop = 1000u,
                dryrun = true,
            ) {
                ValpUtbetaling.Periode(
                    fom = LocalDate.of(2024, 6, 10),
                    tom = LocalDate.of(2024, 6, 1), // valideringsfeil
                )
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .with(transactionId) { statusReply ->
                assertEquals(Status.FEILET, statusReply.status)
                assertEquals(true, statusReply.simulering)
            }

        TestRuntime.topics.oppdrag.assertThat().hasNot(transactionId)
        TestRuntime.topics.simulering.assertThat().hasNot(transactionId)
    }

    @Test
    fun `simulation - idempotent dryrun sets simulering flag on status`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())

        val existingUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid,
            sakId = sid,
            behandlingId = bid,
            originalKey = UUID.randomUUID().toString(),
            stønad = StønadTypeValp.HOYERE_UTDANNING_STUDIEREISE,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("teamvalp"),
            saksbehandlerId = Navident("teamvalp"),
            fagsystem = Fagsystem.VALP,
        ) {
            periode(1.jan, 2.jan, 100u, null)
        }
        TestRuntime.topics.utbetalinger.produce("$uid", existingUtbetaling)

        TestRuntime.topics.valp.produce(transactionId) {
            Valp.utbetaling(uid, sid.id, bid.id, belop = 100u, dryrun = true) {
                ValpUtbetaling.Periode(fom = 1.jan, tom = 2.jan)
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .with(transactionId) { statusReply ->
                assertEquals(Status.OK, statusReply.status)
                assertEquals(true, statusReply.simulering)
            }

        TestRuntime.topics.oppdrag.assertThat().hasNot(transactionId)
        TestRuntime.topics.simulering.assertThat().hasNot(transactionId)
    }

    @Test
    fun `oppdrag - does not set simulering flag on status`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())

        TestRuntime.topics.valpIntern.produce(transactionId) {
            Valp.utbetaling(uid, sid.id, bid.id, belop = 1077u) {
                ValpUtbetaling.Periode(
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                )
            }.asBytes()
        }

        TestRuntime.topics.status.assertThat()
            .has(transactionId)
            .with(transactionId) { statusReply ->
                assertEquals(Status.MOTTATT, statusReply.status)
                assertEquals(false, statusReply.simulering)
            }

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString())

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .get(transactionId)

        kvitterOk(transactionId, oppdrag, listOf(uid))

        TestRuntime.topics.utbetalinger.assertThat().has(uid.toString())
    }

    @Test
    fun `status - FEILET ved deserialiseringsfeil`() {
        val transactionId = UUID.randomUUID().toString()

        TestRuntime.topics.valp.produce(transactionId) {
            """{ "ugyldig-json": """.toByteArray()
        }

        val status = TestRuntime.topics.status.readValue()
        assertEquals(Status.FEILET, status.status)
        assertNotNull(status.error)
    }

}
