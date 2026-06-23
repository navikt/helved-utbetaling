package urskog

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.jdbc.concurrency.transaction
import models.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import urskog.oppdrag.TestData
import urskog.oppdrag.nov
import urskog.oppdrag.seq
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Read-side barrier test: verifies Status.OK is never produced before the gating
 * conditions hold. Production correlates kvittering to the persisted oppdrag row
 * strictly via XML hash; mutating mmel onto the same instance changes the hash, so
 * in this test path the kvittering cannot resolve its row and the retry budget is
 * exhausted into Status.FEILET. The test pins the invariant that matters for the
 * barrier: Status.OK MUST NOT appear on the status topic when sakerAck is false.
 *
 * Steps:
 *   1. Oppdrag arrives first (with uids header) -> DaoOppdrag row inserted, sakerAck=false
 *   2. Pending utbetaling arrives -> pendingIsReady=true, oppdrag sent to MQ -> Status.HOS_OPPDRAG
 *   3. Kvittering (with mmel) arrives -> barrier does not resolve oppdrag row
 *      -> routed to Topics.retryKvittering -> exhausted to Status.FEILET (NEVER Status.OK)
 *   4. Utbetaling aggregated through saker pipeline -> markSakerAck fires -> sakerAck=true
 */
class StatusBarrierTest {

    @AfterEach
    fun cleanup() {
        TestRuntime.mq.reset()
        TestRuntime.topics.status.assertThat()
        TestRuntime.topics.oppdrag.assertThat()
        TestRuntime.topics.pendingUtbetalinger.assertThat()
        TestRuntime.topics.retryKvittering.assertThat()
        TestRuntime.topics.utbetalinger.assertThat()
        TestRuntime.topics.saker.assertThat()
    }

    @Test
    fun `status OK gated until both pendingIsReady and sakerAck are true`() {
        val transaction = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val sakIdStr = "$seq"
        val sakId = SakId(sakIdStr)
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = sakIdStr,
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        TestRuntime.topics.oppdrag.produce(transaction, mapOf("uids" to uid.toString())) {
            oppdrag
        }
        assertEquals(0, TestRuntime.mq.sentOppdrag().size)

        val hashKey = DaoOppdrag.hash(oppdrag)
        TestRuntime.topics.pendingUtbetalinger.produce(uid.toString(), mapOf("hash_key" to hashKey)) {
            TestData.utbetaling(uid = uid, sakId = sakId, originalKey = transaction, fagsystem = Fagsystem.AAP)
        }
        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .has(transaction, value = StatusReply(Status.HOS_OPPDRAG, Detaljer(ytelse = Fagsystem.AAP, linjer = listOf(
                DetaljerLinje(bid, 1.nov, 14.nov, null, 1000u, "AAPOR"),
            ))))
        assertEquals(1, TestRuntime.mq.sentOppdrag().size)

        val daoBefore = runBlocking {
            withContext(TestRuntime.context) {
                transaction { DaoOppdrag.findWithLockOrLegacy(hashKey, oppdrag) }
            }
        }
        assertNotNull(daoBefore)
        assertEquals(false, daoBefore.sakerAck, "sakerAck must still be false BEFORE saker aggregate catches up")

        oppdrag.mmel = TestData.ok()
        // I produksjon setter urskog MQ-consumer alltid "uids"-header på kvittering -- abetal-origin
        // har ikke-tom uids, utsjekk-origin har tom uids. Test simulerer abetal-flyten.
        TestRuntime.topics.oppdrag.produce(transaction, mapOf("maxRetries" to "2", "uids" to uid.toString(), "barrierMaxWaitMs" to "0")) { oppdrag }

        val statusAfterKvittering = TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
        statusAfterKvittering.with(transaction) { reply ->
            assertNotEquals(Status.OK, reply.status, "Barrier must not leak Status.OK while sakerAck is false")
        }

        TestRuntime.topics.retryKvittering.assertThat()

        TestRuntime.topics.utbetalinger.produce(uid.toString()) {
            TestData.utbetaling(uid = uid, sakId = sakId, originalKey = transaction, fagsystem = Fagsystem.AAP)
        }
        TestRuntime.topics.saker.assertThat()

        val daoAfter = runBlocking {
            withContext(TestRuntime.context) {
                transaction { DaoOppdrag.findWithLockOrLegacy(hashKey, oppdrag) }
            }
        }
        assertNotNull(daoAfter)
        assertEquals(true, daoAfter.sakerAck, "sakerAck must flip to true after saker aggregate catches up")
    }

    @Test
    fun `abetal kvittering med mmel finner lagret oppdrag-rad via stripped hash`() {
        // Regresjon: hash i kvitteringReadyOrRetry må regnes på mmel-strippet kopi for at
        // oppslaget skal matche raden som T1-grenen lagret (uten mmel). Tidligere brukte
        // barrieren hash(oppdrag-med-mmel) som garantert bommet -> 1000 retries -> FEILET.
        val transaction = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val sakId = SakId("$seq")
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = sakId.id,
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        // T1: produser oppdrag (mmel=null) -> lagres med hash_key = hash(stripped)
        TestRuntime.topics.oppdrag.produce(transaction, mapOf("uids" to uid.toString())) { oppdrag }
        val storedHash = DaoOppdrag.hash(oppdrag)
        TestRuntime.topics.pendingUtbetalinger.produce(uid.toString(), mapOf("hash_key" to storedHash)) {
            TestData.utbetaling(uid = uid, sakId = sakId, originalKey = transaction, fagsystem = Fagsystem.AAP)
        }

        // Produser utbetaling slik at saker-aggregatet kjører og markSakerAck flipper sakerAck=true
        TestRuntime.topics.utbetalinger.produce(uid.toString()) {
            TestData.utbetaling(uid = uid, sakId = sakId, originalKey = transaction, fagsystem = Fagsystem.AAP)
        }
        val daoAfterSaker = runBlocking {
            withContext(TestRuntime.context) {
                transaction { DaoOppdrag.findWithLockOrLegacy(storedHash, oppdrag) }
            }
        }
        assertNotNull(daoAfterSaker)
        assertEquals(true, daoAfterSaker.sakerAck)

        // Kvittering: samme oppdrag-instans, men nå med mmel populert. Topology re-serialiserer
        // til XML med mmel -> barrieren får inn et Oppdrag-objekt der mmel ikke er null.
        // Med fixen regner barrieren hash på mmel-strippet kopi og finner lagret rad -> Status.OK.
        oppdrag.mmel = TestData.ok()
        TestRuntime.topics.oppdrag.produce(transaction, mapOf("maxRetries" to "2", "uids" to uid.toString())) { oppdrag }

        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 2)
            .with(transaction, index = 0) { reply ->
                assertEquals(Status.HOS_OPPDRAG, reply.status, "T1 emitter HOS_OPPDRAG først")
            }
            .with(transaction, index = 1) { reply ->
                assertEquals(Status.OK, reply.status, "Barrier må resolve abetal-kvittering via stripped hash")
            }
        TestRuntime.topics.retryKvittering.assertThat()
    }

    @Test
    fun `TS-saker med sub-fagområde TILLSTBO flipper sakerAck via normalisert fagsystem`() {
        // Regresjon: markSakerAck må normalisere TILLST*-sub-koder (TILLSTBO, TILLSTLM, ...)
        // til TILLEGGSSTØNADER for å matche SakKey-aggregatet i utbetalingToSak. Uten
        // normaliseringen blir sakerAck aldri flippet for TS-saker -> barrier eksauserer.
        val transaction = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val sakId = SakId("$seq")
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = sakId.id,
            fagområde = "TILLSTBO", // TS sub-kode
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "TSBOTILT",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 500L,
                )
            ),
        )

        TestRuntime.topics.oppdrag.produce(transaction, mapOf("uids" to uid.toString())) { oppdrag }
        val storedHash = DaoOppdrag.hash(oppdrag)
        TestRuntime.topics.pendingUtbetalinger.produce(uid.toString(), mapOf("hash_key" to storedHash)) {
            TestData.utbetaling(uid = uid, sakId = sakId, originalKey = transaction, fagsystem = Fagsystem.TILLSTBO)
        }

        // markSakerAck trigges av utbetalinger-topic. SakKey-aggregatet ruller TILLSTBO opp
        // til TILLEGGSSTØNADER. Pending-raden ble lagret med kodeFagomraade=TILLSTBO som
        // mapper til Fagsystem.TILLSTBO -- markSakerAck må normalisere før sammenligning.
        TestRuntime.topics.utbetalinger.produce(uid.toString()) {
            TestData.utbetaling(uid = uid, sakId = sakId, originalKey = transaction, fagsystem = Fagsystem.TILLSTBO)
        }

        val daoAfterSaker = runBlocking {
            withContext(TestRuntime.context) {
                transaction { DaoOppdrag.findWithLockOrLegacy(storedHash, oppdrag) }
            }
        }
        assertNotNull(daoAfterSaker)
        assertEquals(true, daoAfterSaker.sakerAck, "sakerAck må flippe for TS-saker med sub-fagområde")
    }

    @Test
    fun `utsjekk-origin kvittering uten oppdrag-rad og uten uids-header kortsluttes til Terminal`() {
        val transaction = UUID.randomUUID().toString()
        val sakIdStr = "$seq"
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = sakIdStr,
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        // Ingen oppdrag-row insertes (utsjekk-REST flyt: utsjekk produserer rå Oppdrag uten
        // hash_key/uids-header, urskog T1-branch sender til MQ uten å persistere). Kvittering
        // kommer tilbake med mmel populert, men ingen lagret rad finnes -> locked == null.
        oppdrag.mmel = TestData.ok()
        TestRuntime.topics.oppdrag.produce(transaction, mapOf("maxRetries" to "2")) { oppdrag }

        // Forventet: bypass kortslutter til Terminal med Status.OK uten retry-runde.
        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .with(transaction) { reply ->
                assertEquals(Status.OK, reply.status, "utsjekk-origin kvittering uten uids-header må bypasse barrieren")
            }

        TestRuntime.topics.retryKvittering.assertThat()
    }

    @Test
    fun `barrier never leaks Status OK when only pendingIsReady but sakerAck stays false`() {
        val transaction = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val sakIdStr = "$seq"
        val sakId = SakId(sakIdStr)
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = sakIdStr,
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        TestRuntime.topics.oppdrag.produce(transaction, mapOf("uids" to uid.toString())) { oppdrag }

        val hashKey = DaoOppdrag.hash(oppdrag)
        TestRuntime.topics.pendingUtbetalinger.produce(uid.toString(), mapOf("hash_key" to hashKey)) {
            TestData.utbetaling(uid = uid, sakId = sakId, originalKey = transaction, fagsystem = Fagsystem.AAP)
        }
        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .has(transaction, value = StatusReply(Status.HOS_OPPDRAG, Detaljer(ytelse = Fagsystem.AAP, linjer = listOf(
                DetaljerLinje(bid, 1.nov, 14.nov, null, 1000u, "AAPOR"),
            ))))

        oppdrag.mmel = TestData.ok()
        // I produksjon setter urskog MQ-consumer alltid "uids"-header på kvittering -- abetal-origin
        // har ikke-tom uids, utsjekk-origin har tom uids. Test simulerer abetal-flyten.
        TestRuntime.topics.oppdrag.produce(transaction, mapOf("maxRetries" to "2", "uids" to uid.toString(), "barrierMaxWaitMs" to "0")) { oppdrag }

        val statusAfterKvittering = TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
        statusAfterKvittering.with(transaction) { reply ->
            assertNotEquals(Status.OK, reply.status, "Barrier must not leak Status.OK while sakerAck is false")
        }

        TestRuntime.topics.retryKvittering.assertThat()

        val dao = runBlocking {
            withContext(TestRuntime.context) {
                transaction { DaoOppdrag.findWithLockOrLegacy(hashKey, oppdrag) }
            }
        }
        assertNotNull(dao)
        assertEquals(false, dao.sakerAck)
        assertNull(dao.sakerAckAt)
    }

    @Test
    fun `kvittering med alvorlighetsgrad 08 er endelig status og dropper retry mekanisme`() {
        // Regresjon: feil-kvittering (08 = funksjonell feil) skal kortsluttes til Terminal med en
        // gang, uten å vente på pendingReady/sakerReady. Sjekken gjelder kun for å sikre at
        // downstream har tatt igjen før Status.OK -- for feil er det ingenting å vente på fordi
        // abetal ikke promoterer feilet oppdrag til helved.utbetalinger.v1.
        val transaction = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val sakIdStr = "$seq"
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = sakIdStr,
            fagområde = "DP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "DPORAS",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        // Ingen oppdrag-rad lagres, ingen pending, ingen saker-aggregat. Bare en 08-kvittering
        // som kommer rett inn -- skal emittere Status.FEILET med en gang, uten retry-runde.
        oppdrag.mmel = TestData.feilet(alvorlighetsgrad = "08")
        TestRuntime.topics.oppdrag.produce(transaction, mapOf("maxRetries" to "2", "uids" to uid.toString())) { oppdrag }

        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .with(transaction) { reply ->
                assertEquals(Status.FEILET, reply.status, "08 er endelig status, må emittere FEILET umiddelbart")
                assertEquals(400, reply.error?.statusCode, "08 mapper til HTTP 400 via statusReply()")
            }

        TestRuntime.topics.retryKvittering.assertThat()
    }

    @Test
    fun `kvittering med alvorlighetsgrad 12 er endelig status og dropper retry mekanisme`() {
        // Regresjon: 12 = fatal feil -> Terminal med HTTP 500, ingen retry.
        val transaction = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val sakIdStr = "$seq"
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = sakIdStr,
            fagområde = "DP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "DPORAS",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        oppdrag.mmel = TestData.feilet(alvorlighetsgrad = "12")
        TestRuntime.topics.oppdrag.produce(transaction, mapOf("maxRetries" to "2", "uids" to uid.toString())) { oppdrag }

        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .with(transaction) { reply ->
                assertEquals(Status.FEILET, reply.status, "12 er endelig status (fatal feil), må emittere FEILET umiddelbart")
                assertEquals(500, reply.error?.statusCode, "12 mapper til HTTP 500 via statusReply()")
            }

        TestRuntime.topics.retryKvittering.assertThat()
    }

    @Test
    fun `kvittering med alvorlighetsgrad 08 og trailing whitespace fra COBOL behandles som 08`() {
        // Regresjon: OS-XML kan komme fra COBOL med trailing whitespace på alvorlighetsgrad.
        // trimEnd() speiler abetal/Kafka.kt:402-normaliseringen. Uten trim ville "08 " hverken
        // matche "00"/"04" (riktig) eller mappes pent i statusReply() -- begge går i else-grenen,
        // men short-circuit-loggen og oppførselen skal være lik som ren "08".
        val transaction = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val sakIdStr = "$seq"
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = sakIdStr,
            fagområde = "DP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "DPORAS",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        oppdrag.mmel = TestData.feilet(alvorlighetsgrad = "08 ")
        TestRuntime.topics.oppdrag.produce(transaction, mapOf("maxRetries" to "2", "uids" to uid.toString())) { oppdrag }

        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .with(transaction) { reply ->
                assertEquals(Status.FEILET, reply.status, "trimEnd()-normalisert 08 må også kortsluttes til endelig status")
            }

        TestRuntime.topics.retryKvittering.assertThat()
    }

    @Test
    fun `opphør av siste utbetaling på sak flipper sakerAck når saker-aggregat blir tomt`() {
        val transaction = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val sakIdStr = "$seq"
        val sakId = SakId(sakIdStr)
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = sakIdStr,
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        // Seed saker-aggregat med uid (simulerer at utbetaling ble opprettet tidligere)
        TestRuntime.topics.utbetalinger.produce(uid.toString()) {
            TestData.utbetaling(uid = uid, sakId = sakId, originalKey = transaction, fagsystem = Fagsystem.AAP)
        }
        TestRuntime.topics.saker.assertThat()

        // Oppdrag for opphør ankommer med uids-header
        TestRuntime.topics.oppdrag.produce(transaction, mapOf("uids" to uid.toString())) { oppdrag }
        assertEquals(0, TestRuntime.mq.sentOppdrag().size)

        val hashKey = DaoOppdrag.hash(oppdrag)
        TestRuntime.topics.pendingUtbetalinger.produce(uid.toString(), mapOf("hash_key" to hashKey)) {
            TestData.utbetaling(action = Action.DELETE, uid = uid, sakId = sakId, originalKey = transaction, fagsystem = Fagsystem.AAP)
        }
        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .has(transaction, value = StatusReply(Status.HOS_OPPDRAG, Detaljer(ytelse = Fagsystem.AAP, linjer = listOf(
                DetaljerLinje(bid, 1.nov, 14.nov, null, 1000u, "AAPOR"),
            ))))
        assertEquals(1, TestRuntime.mq.sentOppdrag().size)

        val daoBefore = runBlocking {
            withContext(TestRuntime.context) {
                transaction { DaoOppdrag.findWithLockOrLegacy(hashKey, oppdrag) }
            }
        }
        assertNotNull(daoBefore)
        assertEquals(false, daoBefore.sakerAck, "sakerAck must be false before saker aggregate processes DELETE")

        // Opphør: produser DELETE-utbetaling -> saker-aggregat blir tomt -> markSakerAck skal flippe
        TestRuntime.topics.utbetalinger.produce(uid.toString()) {
            TestData.utbetaling(action = Action.DELETE, uid = uid, sakId = sakId, originalKey = transaction, fagsystem = Fagsystem.AAP)
        }
        TestRuntime.topics.saker.assertThat()

        val daoAfter = runBlocking {
            withContext(TestRuntime.context) {
                transaction { DaoOppdrag.findWithLockOrLegacy(hashKey, oppdrag) }
            }
        }
        assertNotNull(daoAfter)
        assertEquals(true, daoAfter.sakerAck, "sakerAck must flip to true when saker aggregate becomes empty (opphør)")

        // Kvittering → barrier resolves → Status.OK
        oppdrag.mmel = TestData.ok()
        TestRuntime.topics.oppdrag.produce(transaction, mapOf("maxRetries" to "2", "uids" to uid.toString())) { oppdrag }

        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .with(transaction) { reply ->
                assertEquals(Status.OK, reply.status, "Barrier must resolve to OK after opphør empties saker aggregate")
            }

        TestRuntime.topics.retryKvittering.assertThat()
    }
}
