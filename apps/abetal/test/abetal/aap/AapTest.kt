package abetal.aap

import abetal.*
import abetal.aap.linje
import models.*
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AapTest : ConsumerTestBase() {

    @Test
    fun `create - two meldekort create two utbetalinger with single oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        val expectedUtbetaling1 = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            originalKey = transactionId,
            sakId = sid,
            behandlingId = bid,
            fagsystem = Fagsystem.AAP,
            lastPeriodeId = PeriodeId(),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("kelvin"),
            saksbehandlerId = Navident("kelvin"),
            personident = Personident("12345678910")
        ) {
            periode(7.jun, 18.jun, 553u, 1077u)
        }

        val expectedUtbetaling2 = expectedUtbetaling1.copy(
            uid = uid2,
            perioder = listOf(periode(8.jul, 19.jul, 779u, 2377u))
        ) 

        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid.id) {
                meldekort(meldeperiode1, 7.jun, 18.jun, 553u, 1077u)
                meldekort(meldeperiode2, 7.jul, 20.jul, 779u, 2377u)
            }
        }
        TestRuntime.topics.status.assertThat().has(transactionId) {
            Aap.mottatt {
                linje(bid, 7.jun, 18.jun, 1077u, 553u)
                linje(bid, 8.jul, 19.jul, 2377u, 779u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId, size = 1)
            .with(transactionId, index = 0) { oppdrag ->
                oppdrag.assertBasics("NY", "AAP", sid.id, expectedLines = 2)
                assertEquals("kelvin", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "AAPOR",
                    sats = 553,
                    vedtakssats = 1077,
                    refDelytelseId = null
                )
                oppdrag.oppdrag110.oppdragsLinje150s[1].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "AAPOR",
                    sats = 779,
                    vedtakssats = 2377
                )
            }
            .get(transactionId)

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
    fun `create - three meldekort create three utbetalinger with single oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val meldeperiode3 = "132462765"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid3 = aapUId(sid.id, meldeperiode3, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        val expectedUtbetaling1 = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            originalKey = transactionId,
            sakId = sid,
            behandlingId = bid,
            fagsystem = Fagsystem.AAP,
            lastPeriodeId = PeriodeId(), // placeholder
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            vedtakstidspunkt = LocalDateTime.now(),
            beslutterId = Navident("kelvin"),
            saksbehandlerId = Navident("kelvin"),
            personident = Personident("12345678910")
        ) {
            periode(7.jun, 20.jun, 553u, 1077u)
        }
        val expectedUtbetaling2 = expectedUtbetaling1.copy(
            uid = uid2,
            perioder = listOf(periode(8.jul, 19.jul, 779u, 2377u))
        )
        val expectedUtbetaling3 = expectedUtbetaling1.copy(
            uid = uid3,
            perioder = listOf(periode(7.aug, 20.aug, 3000u, 3133u)),
        )
        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid.id) {
                meldekort(meldeperiode1, 7.jun, 20.jun, 553u, 1077u)
                meldekort(meldeperiode2, 8.jul, 19.jul, 779u, 2377u)
                meldekort(meldeperiode3, 7.aug, 20.aug, 3000u, 3133u)
            }
        }
        TestRuntime.topics.status.assertThat().has(transactionId) {
            Aap.mottatt {
                linje(bid, 7.jun, 20.jun, 1077u, 553u)
                linje(bid, 8.jul, 19.jul, 2377u, 779u)
                linje(bid, 7.aug, 20.aug, 3133u, 3000u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.assertBasics("NY", "AAP", sid.id, expectedLines = 3)
                assertEquals("kelvin", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "AAPOR",
                    sats = 553,
                    vedtakssats = 1077,
                    refDelytelseId = null
                )
                oppdrag.oppdrag110.oppdragsLinje150s[1].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "AAPOR",
                    sats = 779,
                    vedtakssats = 2377,
                    refDelytelseId = null
                )
                oppdrag.oppdrag110.oppdragsLinje150s[2].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "AAPOR",
                    sats = 3000,
                    vedtakssats = 3133,
                    refDelytelseId = null
                )
            }
            .get(transactionId)

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
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        val existingUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId1,
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("kelvin"),
            saksbehandlerId = Navident("kelvin"),
            fagsystem = Fagsystem.AAP,
        ) {
            periode(3.jun, 14.jun, 100u, 100u)
        }

        val expectedUtbetaling = existingUtbetaling.copy(
            uid = uid2,
            originalKey = transactionId2,
            førsteUtbetalingPåSak = false,
            perioder = listOf(periode(17.jun, 28.jun, 200u, 200u))
        ) 

        TestRuntime.topics.utbetalinger.produce(uid1.toString(), existingUtbetaling)
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.AAP), setOf(uid1))
        TestRuntime.topics.aap.produce(transactionId2) {
            Aap.utbetaling(sid.id, bid.id, vedtakstidspunkt = 14.jun.atStartOfDay()) {
                meldekort(meldeperiode1, 3.jun, 14.jun, 100u, 100u)
                meldekort(meldeperiode2, 17.jun, 28.jun, 200u, 200u)
            }
        }

        TestRuntime.topics.status.assertThat().has(transactionId2) {
            Aap.mottatt {
                linje(bid, 17.jun, 28.jun, 200u, 200u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId2)
            .with(transactionId2) { oppdrag ->
                oppdrag.assertBasics("ENDR", "AAP", sid.id, expectedLines = 1)
                assertEquals("kelvin", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "AAPOR",
                    sats = 200,
                    vedtakssats = 200,
                    refDelytelseId = null
                )
            }
            .get(transactionId2)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid2.toString())
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
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val periodeId = PeriodeId()

        val utbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId1,
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            lastPeriodeId = periodeId,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("kelvin"),
            saksbehandlerId = Navident("kelvin"),
            fagsystem = Fagsystem.AAP,
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
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.AAP), setOf(uid1))
        TestRuntime.topics.aap.produce(transactionId2) {
            Aap.utbetaling(sid.id, bid.id, vedtakstidspunkt = 14.jun.atStartOfDay()) {
                meldekort(meldeperiode1, 3.jun, 14.jun, 80u, 100u)
            }
        }
        TestRuntime.topics.status.assertThat().has(transactionId2) {
            Aap.mottatt {
                linje(bid, 3.jun, 14.jun, 100u, 80u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId2)
            .with(transactionId2) { oppdrag ->
                oppdrag.assertBasics("ENDR", "AAP", sid.id, expectedLines = 1)
                assertEquals("kelvin", oppdrag.oppdrag110.saksbehId)
                oppdrag.oppdrag110.oppdragsLinje150s[0].assertLine(
                    kodeEndringLinje = "NY",
                    behandlingId = bid,
                    kodeKlassifik = "AAPOR",
                    sats = 80,
                    vedtakssats = 100,
                    refDelytelseId = periodeId.toString()
                )
            }
            .get(transactionId2)

        kvitterOk(transactionId2, oppdrag, listOf(uid1))

        TestRuntime.topics.utbetalinger.assertThat().has(uid1.toString()) 
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertUtbetaling(expectedUtbetaling, it)
            }
    }

    @Test
    fun `opphør - canceling meldekort`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId1 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val periodeId = PeriodeId()

        val existingUtbetaling = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId1,
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            lastPeriodeId = periodeId,
            personident = Personident("12345678910"),
            vedtakstidspunkt = 14.jun.atStartOfDay(),
            beslutterId = Navident("kelvin"),
            saksbehandlerId = Navident("kelvin"),
            fagsystem = Fagsystem.AAP,
        ) {
            periode(2.jun, 13.jun, 100u, 100u)
        }

        val expectedUtbetaling = existingUtbetaling.copy(
            action = Action.DELETE,
        )

        TestRuntime.topics.utbetalinger.produce(uid1.toString(), existingUtbetaling)
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.AAP), setOf(uid1))
        TestRuntime.topics.aap.produce(transactionId1) {
            Aap.utbetaling(sid.id, bid.id, vedtakstidspunkt = 14.jun.atStartOfDay()) {
                // Empty - opphør
            }
        }

        TestRuntime.topics.status.assertThat().has(transactionId1) {
            Aap.mottatt {
                linje(bid, 2.jun, 13.jun, 100u, 0u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId1)
            .with(transactionId1) { oppdrag ->
                oppdrag.assertBasics("ENDR", "AAP", sid.id, expectedLines = 1)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
                    assertEquals(2.jun, it.datoStatusFom.toLocalDate())
                    assertEquals("AAPOR", it.kodeKlassifik)
                    assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                }
            }
            .get(transactionId1)

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
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
    fun `edge case - multiple meldekort with mixed operations`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val transactionId = UUID.randomUUID().toString()
        val uid1 = aapUId(sid.id, "132460781", StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, "232460781", StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid3 = aapUId(sid.id, "132462765", StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        val utbetaling1 = utbetaling(
            action = Action.CREATE,
            uid = uid1,
            sakId = sid,
            behandlingId = bid,
            originalKey = transactionId,
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            lastPeriodeId = PeriodeId(),
            personident = Personident("12345678910"),
            vedtakstidspunkt = 14.sep.atStartOfDay(),
            beslutterId = Navident("kelvin"),
            saksbehandlerId = Navident("kelvin"),
            fagsystem = Fagsystem.AAP,
        ) {
            periode(2.sep, 13.sep, 500u, 500u)
        }

        val utbetaling2 = utbetaling1.copy(
            uid = uid2,
            førsteUtbetalingPåSak = false,
            perioder = listOf(periode(16.sep, 27.sep, 600u, 600u))
        ) 

        TestRuntime.topics.utbetalinger.produce(uid1.toString(), utbetaling1)
        TestRuntime.topics.utbetalinger.produce(uid2.toString(), utbetaling2)
        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.AAP), setOf(uid1, uid2))
        TestRuntime.topics.aap.produce(transactionId) {
            Aap.utbetaling(sid.id, bid.id) {
                meldekort("132460781", 2.sep, 13.sep, 600u, 600u)
                meldekort("132462765", 30.sep, 10.okt, 600u, 600u)
            }
        }
        TestRuntime.topics.status.assertThat().has(transactionId) {
            Aap.mottatt {
                linje(bid, 2.sep, 13.sep, 600u, 600u)
                linje(bid, 30.sep, 10.okt, 600u, 600u)
                linje(bid, 16.sep, 27.sep, 600u, 0u)
            }
        }
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(transactionId)
            .with(transactionId) { oppdrag ->
                oppdrag.assertBasics("ENDR", "AAP", sid.id, expectedLines = 3)
                oppdrag.oppdrag110.oppdragsLinje150s[0].let {
                    assertEquals(utbetaling1.lastPeriodeId.toString(), it.refDelytelseId)
                }
                oppdrag.oppdrag110.oppdragsLinje150s[2].let {
                    assertNull(it.refDelytelseId)
                    assertEquals(TkodeStatusLinje.OPPH, it.kodeStatusLinje)
                }
            }
            .get(transactionId)

        kvitterOk(transactionId, oppdrag, listOf(uid1, uid2, uid3))

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString()).with(uid1.toString()) { assertEquals(Action.UPDATE, it.action) }
            .has(uid2.toString()).with(uid2.toString()) { assertEquals(Action.DELETE, it.action) }
            .has(uid3.toString()).with(uid3.toString()) { assertEquals(Action.CREATE, it.action) }
    }
}
