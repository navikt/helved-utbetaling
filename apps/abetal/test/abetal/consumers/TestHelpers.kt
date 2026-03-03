package abetal.consumers

import abetal.SakKey
import abetal.TestRuntime
import abetal.utbetaling
import models.*
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import no.trygdeetaten.skjema.oppdrag.OppdragsLinje150
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Helper for testing consumer scenarios with less boilerplate.
 */
object TestScenarios {
    
    /**
     * Creates an existing utbetaling in the topic for testing updates/deletes
     */
    fun createExistingUtbetaling(
        uid: UtbetalingId,
        sakId: SakId,
        behandlingId: BehandlingId,
        fagsystem: Fagsystem,
        sakerFagsystem: Fagsystem = fagsystem,
        stønad: Stønadstype,
        periodeId: PeriodeId = PeriodeId(),
        personident: Personident = Personident("12345678910"),
        periodetype: Periodetype = Periodetype.UKEDAG,
        perioder: () -> List<Utbetalingsperiode>
    ): PeriodeId {
        TestRuntime.topics.utbetalinger.produce(uid.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid,
                sakId = sakId,
                behandlingId = behandlingId,
                originalKey = UUID.randomUUID().toString(),
                stønad = stønad,
                lastPeriodeId = periodeId,
                personident = personident,
                periodetype = periodetype,
                vedtakstidspunkt = LocalDateTime.now(),
                beslutterId = Navident(fagsystem.saksbehandler()),
                saksbehandlerId = Navident(fagsystem.saksbehandler()),
                fagsystem = fagsystem,
                perioder = perioder
            )
        }
        TestRuntime.topics.saker.produce(SakKey(sakId, sakerFagsystem)) {
            setOf(uid)
        }
        return periodeId
    }
}

/**
 * Asserts that status has the expected values
 */
fun String.assertStatus(
    expectedStatus: Status = Status.MOTTATT,
    expectedFagsystem: Fagsystem
) {
    TestRuntime.topics.status.assertThat()
        .has(this)
        .with(this) {
            assertEquals(expectedStatus, it.status)
            assertEquals(expectedFagsystem, it.detaljer?.ytelse)
        }
}

/**
 * Asserts status with full details comparison (order-independent for linjer)
 */
fun String.assertStatusWithDetails(
    expectedStatus: Status = Status.MOTTATT,
    expectedDetaljer: Detaljer
) {
    TestRuntime.topics.status.assertThat()
        .has(this)
        .with(this) { actual ->
            assertEquals(expectedStatus, actual.status)
            assertEquals(expectedDetaljer.ytelse, actual.detaljer?.ytelse)
            
            // Sort both lists for order-independent comparison
            val actualLinjer = actual.detaljer?.linjer?.sortedWith(
                compareBy<DetaljerLinje> { it.behandlingId }
                    .thenBy { it.fom }
                    .thenBy { it.tom }
            ) ?: emptyList()
            
            val expectedLinjer = expectedDetaljer.linjer.sortedWith(
                compareBy<DetaljerLinje> { it.behandlingId }
                    .thenBy { it.fom }
                    .thenBy { it.tom }
            )
            
            assertEquals(expectedLinjer, actualLinjer, "DetaljerLinje lists don't match (compared after sorting)")
        }
}

/**
 * Asserts status with custom validation
 */
fun String.assertStatusWith(validation: (StatusReply) -> Unit) {
    TestRuntime.topics.status.assertThat()
        .has(this)
        .with(this) { validation(it) }
}

/**
 * Gets and asserts oppdrag, returns it for further processing
 */
fun String.getOppdrag(
    assertions: (Oppdrag) -> Unit = {}
): Oppdrag {
    return TestRuntime.topics.oppdrag.assertThat()
        .has(this)
        .with(this) { assertions(it) }
        .get(this)
}

/**
 * Gets oppdrag and asserts basics, returns it for acknowledgement
 */
fun String.getOppdragWithBasics(
    kodeEndring: String,
    kodeFagomraade: String,
    sakId: String,
    ident: String = "12345678910",
    saksbehId: String,
    expectedLines: Int,
    utbetFrekvens: String = "MND",
    additionalAssertions: (Oppdrag) -> Unit = {}
): Oppdrag {
    return getOppdrag { oppdrag ->
        oppdrag.assertBasics(kodeEndring, kodeFagomraade, sakId, ident, expectedLines, utbetFrekvens)
        assertEquals(saksbehId, oppdrag.oppdrag110.saksbehId)
        additionalAssertions(oppdrag)
    }
}

/**
 * Acknowledges an oppdrag with success (multiple UIDs)
 */
fun kvitterOk(transactionId: String, oppdrag: Oppdrag, uids: List<UtbetalingId>) {
    val uidString = uids.joinToString(",") { it.toString() }
    TestRuntime.topics.oppdrag.produce(transactionId, mapOf("uids" to uidString)) {
        oppdrag.apply {
            mmel = Mmel().apply { alvorlighetsgrad = "00" }
        }
    }
}

/**
 * Asserts that utbetalinger topic has the specified UIDs
 */
fun assertUtbetalinger(uids: List<UtbetalingId>) {
    val assertion = TestRuntime.topics.utbetalinger.assertThat()
    uids.forEach { uid ->
        assertion.has(uid.toString())
    }
}

fun assertUtbetaling(expected: Utbetaling, actual: Utbetaling) {
    val expected = expected.copy(
        lastPeriodeId = actual.lastPeriodeId,       // generated runtime
    )
    assertEquals(expected, actual)
}

/**
 * Common assertions for oppdrag basics
 */
fun Oppdrag.assertBasics(
    kodeEndring: String,
    kodeFagomraade: String,
    sakId: String,
    ident: String = "12345678910",
    expectedLines: Int,
    utbetFrekvens: String = "MND"
) {
    assertEquals("1", this.oppdrag110.kodeAksjon)
    assertEquals(kodeEndring, this.oppdrag110.kodeEndring)
    assertEquals(kodeFagomraade, this.oppdrag110.kodeFagomraade)
    assertEquals(sakId, this.oppdrag110.fagsystemId)
    assertEquals(utbetFrekvens, this.oppdrag110.utbetFrekvens)
    assertEquals(ident, this.oppdrag110.oppdragGjelderId)
    assertEquals(expectedLines, this.oppdrag110.oppdragsLinje150s.size)
}

/**
 * Asserts common properties of an oppdrag line
 */
fun OppdragsLinje150.assertLine(
    kodeEndringLinje: String,
    henvisning: String,
    kodeKlassifik: String,
    sats: Long,
    vedtakssats: Long? = null,
    refDelytelseId: String? = null
) {
    assertEquals(kodeEndringLinje, this.kodeEndringLinje)
    assertEquals(henvisning, this.henvisning)
    assertEquals(kodeKlassifik, this.kodeKlassifik)
    assertEquals(sats, this.sats.toLong())
    if (vedtakssats != null) {
        assertEquals(vedtakssats, this.vedtakssats157.vedtakssats.toLong())
    }
    if (refDelytelseId == null) {
        assertNull(this.refDelytelseId)
    } else {
        assertEquals(refDelytelseId, this.refDelytelseId)
    }
    assertEquals(this.datoVedtakFom, this.datoKlassifikFom)
}

/**
 * Extension function to get default saksbehandler for fagsystem
 */
fun Fagsystem.saksbehandler(): String = when (this) {
    Fagsystem.DAGPENGER -> "dagpenger"
    Fagsystem.AAP -> "kelvin"
    Fagsystem.TILLEGGSSTØNADER, Fagsystem.TILLSTPB, Fagsystem.TILLSTDR,
    Fagsystem.TILLSTLM, Fagsystem.TILLSTBO, Fagsystem.TILLSTRS,
    Fagsystem.TILLSTRO, Fagsystem.TILLSTRA, Fagsystem.TILLSTFL -> "ts"
    Fagsystem.TILTAKSPENGER -> "tp"
    Fagsystem.HISTORISK -> "historisk"
}
