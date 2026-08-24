package peisschtappern

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import libs.jdbc.concurrency.transaction
import libs.jdbc.truncate
import libs.kotlinx.KotlinxJson
import models.*
import org.junit.jupiter.api.AfterEach
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

private var offset: Long = 1
    get() = field++

private val pendingMismatchTimestamp = AtomicLong(Instant.parse("2030-01-01T00:00:00Z").toEpochMilli())

private fun nextPendingMismatchTimestamp(): Long =
    pendingMismatchTimestamp.addAndGet(24 * 60 * 60 * 1_000L)

class DashboardApiTest {
    @AfterEach
    fun resetDashboardData() {
        TestRuntime.jdbc.truncate("peisschtappern.oppdrag")
        TestRuntime.jdbc.truncate("peisschtappern.status")
        TestRuntime.jdbc.truncate("peisschtappern.utbetalinger")
        TestRuntime.jdbc.truncate("peisschtappern.pending_utbetalinger")
        TestRuntime.jdbc.truncate("peisschtappern.avstemming")
        TestRuntime.jdbc.truncate("peisschtappern.korrigerte_feilet_utbetalinger")
    }

    @Test
    fun `can get dashboard`() = runTest(TestRuntime.context) {
        val fom = LocalDate.now().minusDays(3)
        val tom = LocalDate.now()

        saveAvstemming(Fagsystem.AAP, fom, tom)
        saveAvstemming(Fagsystem.TILLEGGSSTØNADER, fom, tom)
        saveAvstemming(Fagsystem.TILTAKSPENGER, fom, tom)
        saveAvstemming(Fagsystem.HISTORISK, fom.minusDays(20), tom.minusDays(20))

        val queryFom = fom.atStartOfDay().toInstant(ZoneOffset.UTC).toString()
        val queryTom = tom.atStartOfDay().toInstant(ZoneOffset.UTC).toString()

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", queryFom)
                parameters.append("tom", queryTom)
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()

        assertEquals(4, dashboard.avstemming.size)
        assertNull(dashboard.avstemming[3].datoAvstemtFom)
        assertNull(dashboard.avstemming[3].datoAvstemtTom)
        assertEquals(LocalDate.now().minusDays(20), dashboard.avstemming[3].sisteAvstemtDato)
    }

    @Test
    fun `kan markere en feilet utbetaling som korrigert`() = runTest(TestRuntime.context) {
        val now = Instant.now()
        val request = KorrigerUtbetalingRequest(
            topic = Channel.Status.topic.name,
            key = UUID.randomUUID().toString(),
            reason = "Utbetalingen er kontrollert og korrigert"
        )

        val korrigertUtbetaling = TestRuntime.ktor.httpClient.post("/api/korriger_utbetaling") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.also { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }.body<Dashboard.KorrigertFeiletUtbetaling>()

        assertEquals(request.topic, korrigertUtbetaling.topic)
        assertEquals(request.key, korrigertUtbetaling.key)
        assertEquals(request.reason, korrigertUtbetaling.reason)
        assertTrue(korrigertUtbetaling.registeredAt >= now.toEpochMilli())

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", now.minusSeconds(60).toString())
                parameters.append("tom", now.plusSeconds(60).toString())
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()

        assertEquals(1, dashboard.korrigerteFeiletUtbetalinger.count { it == korrigertUtbetaling })
    }

    @Test
    fun `kan markere samme feilede utbetaling som korrigert på nytt`() = runTest(TestRuntime.context) {
        val opprinneligRequest = KorrigerUtbetalingRequest(
            topic = Channel.Status.topic.name,
            key = UUID.randomUUID().toString(),
            reason = "Første korrigering"
        )

        val opprinneligKorrigering = TestRuntime.ktor.httpClient.post("/api/korriger_utbetaling") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(opprinneligRequest)
        }.also { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }.body<Dashboard.KorrigertFeiletUtbetaling>()

        val fom = Instant.now()
        while (!Instant.now().isAfter(fom)) Thread.onSpinWait()
        val oppdatertRequest = opprinneligRequest.copy(reason = "Oppdatert forklaring")

        val oppdatertKorrigering = TestRuntime.ktor.httpClient.post("/api/korriger_utbetaling") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(oppdatertRequest)
        }.also { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }.body<Dashboard.KorrigertFeiletUtbetaling>()

        assertTrue(oppdatertKorrigering.registeredAt > opprinneligKorrigering.registeredAt)

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", fom.toString())
                parameters.append("tom", fom.plusSeconds(60).toString())
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()

        assertEquals(1, dashboard.korrigerteFeiletUtbetalinger.count { it == oppdatertKorrigering })
    }


    @Test
    fun `can find oppdrag without corresponding status messages`() = runTest(TestRuntime.context) {
        val timestamp = Instant.now().minusSeconds(2 * 60 * 60).toEpochMilli()
        val key1 = UUID.randomUUID().toString()
        val key2 = UUID.randomUUID().toString()
        val key3 = UUID.randomUUID().toString()
        val key4 = UUID.randomUUID().toString()
        val key5 = UUID.randomUUID().toString()
        save(Channel.Oppdrag, key = key1, timestamp = timestamp, offset = offset)
        save(Channel.Oppdrag, key = key2, timestamp = timestamp, offset = offset)
        save(Channel.Oppdrag, key = key3, timestamp = timestamp, offset = offset)
        save(Channel.Oppdrag, key = key4, timestamp = timestamp, offset = offset)
        save(Channel.Oppdrag, key = key5, timestamp = timestamp, offset = offset)

        save(Channel.Status, key = key1, timestamp = timestamp, offset = offset)
        save(Channel.Status, key = key3, timestamp = timestamp, offset = offset)
        save(Channel.Status, key = key5, timestamp = timestamp, offset = offset)

        val result = TestRuntime.ktor.httpClient.get("/api/dashboard/oppdrag_uten_status") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<List<OppdragUtenKvittering>>()

        assertEquals(2, result.size)
        assertEquals(setOf(key2, key4), result.map { it.key }.toSet())
    }


    @Test
    fun `dashboard har ingen pending mismatch når tabellene er tomme`() = runTest(TestRuntime.context) {
        val now = nextPendingMismatchTimestamp()

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", Instant.ofEpochMilli(now - 1_000).toString())
                parameters.append("tom", Instant.ofEpochMilli(now + 1_000).toString())
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()

        assertTrue(dashboard.pendingMismatch.isEmpty())
    }

    @Test
    fun `dashboard teller bare feilede statuser i perioden`() = runTest(TestRuntime.context) {
        val now = nextPendingMismatchTimestamp()
        val feilet = KotlinxJson.encodeToString(StatusReply(Status.FEILET))
        val simuleringStengt = """{"status":"FEILET","error":{"msg":"simulering stengt"}}"""

        save(Channel.Status, value = feilet, timestamp = now, offset = offset)
        save(Channel.Status, value = simuleringStengt, timestamp = now + 1_000, offset = offset)
        save(Channel.Status, value = feilet, timestamp = now + 3_000, offset = offset)

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", Instant.ofEpochMilli(now - 1_000).toString())
                parameters.append("tom", Instant.ofEpochMilli(now + 2_000).toString())
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()

        assertEquals(1, dashboard.feiletUtbetalinger.size)
    }


    @Test
    fun `dashboard har ingen pending mismatch når perioder er like`() = runTest(TestRuntime.context) {
        val now = nextPendingMismatchTimestamp()
        val uid = UUID.randomUUID().toString()
        val value = utbetalingJson(uid, listOf(periode()))

        save(Channel.PendingUtbetalinger, key = uid, value = value, timestamp = now, offset = offset)
        save(Channel.Utbetalinger, key = uid, value = value, timestamp = now + 1_000, offset = offset)

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", Instant.ofEpochMilli(now - 1_000).toString())
                parameters.append("tom", Instant.ofEpochMilli(now + 2_000).toString())
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()

        assertTrue(dashboard.pendingMismatch.isEmpty())
    }


    @Test
    fun `dashboard viser pending mismatch når beløp er ulikt`() = runTest(TestRuntime.context) {
        val now = nextPendingMismatchTimestamp()
        val uid = UUID.randomUUID().toString()

        save(Channel.PendingUtbetalinger, key = uid, value = utbetalingJson(uid, listOf(periode(beløp = 2_000u))), timestamp = now, offset = offset)
        save(Channel.Utbetalinger, key = uid, value = utbetalingJson(uid, listOf(periode(beløp = 1_000u))), timestamp = now + 1_000, offset = offset)

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", Instant.ofEpochMilli(now - 1_000).toString())
                parameters.append("tom", Instant.ofEpochMilli(now + 2_000).toString())
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()
        val result = dashboard.pendingMismatch.single()

        assertEquals(uid, result.uid)
        assertEquals("sak-1", result.sakId)
        assertEquals("AAP", result.fagsystem)
    }


    @Test
    fun `dashboard viser pending mismatch når antall perioder er ulikt`() = runTest(TestRuntime.context) {
        val now = nextPendingMismatchTimestamp()
        val uid = UUID.randomUUID().toString()
        val perioder = listOf(periode(), periode(fom = LocalDate.of(2025, 2, 1), tom = LocalDate.of(2025, 2, 28)))

        save(Channel.PendingUtbetalinger, key = uid, value = utbetalingJson(uid, perioder.take(1)), timestamp = now, offset = offset)
        save(Channel.Utbetalinger, key = uid, value = utbetalingJson(uid, perioder), timestamp = now + 1_000, offset = offset)

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", Instant.ofEpochMilli(now - 1_000).toString())
                parameters.append("tom", Instant.ofEpochMilli(now + 2_000).toString())
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()

        assertEquals(1, dashboard.pendingMismatch.size)
    }


    @Test
    fun `dashboard ignorerer pending som kom etter utbetalingen`() = runTest(TestRuntime.context) {
        val now = nextPendingMismatchTimestamp()
        val uid = UUID.randomUUID().toString()

        save(Channel.Utbetalinger, key = uid, value = utbetalingJson(uid, listOf(periode(beløp = 1_000u))), timestamp = now, offset = offset)
        save(Channel.PendingUtbetalinger, key = uid, value = utbetalingJson(uid, listOf(periode(beløp = 2_000u))), timestamp = now + 1_000, offset = offset)

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", Instant.ofEpochMilli(now - 1_000).toString())
                parameters.append("tom", Instant.ofEpochMilli(now + 2_000).toString())
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()

        assertTrue(dashboard.pendingMismatch.isEmpty())
    }


    @Test
    fun `dashboard ignorerer utbetaling uten pending`() = runTest(TestRuntime.context) {
        val now = nextPendingMismatchTimestamp()
        val uid = UUID.randomUUID().toString()

        save(Channel.Utbetalinger, key = uid, value = utbetalingJson(uid, listOf(periode())), timestamp = now, offset = offset)

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", Instant.ofEpochMilli(now - 1_000).toString())
                parameters.append("tom", Instant.ofEpochMilli(now + 1_000).toString())
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()

        assertTrue(dashboard.pendingMismatch.isEmpty())
    }

    @Test
    fun `dashboard ignorerer utbetaling etter tom`() = runTest(TestRuntime.context) {
        val now = nextPendingMismatchTimestamp()
        val uid = UUID.randomUUID().toString()

        save(Channel.PendingUtbetalinger, key = uid, value = utbetalingJson(uid, listOf(periode(beløp = 2_000u))), timestamp = now, offset = offset)
        save(Channel.Utbetalinger, key = uid, value = utbetalingJson(uid, listOf(periode(beløp = 1_000u))), timestamp = now + 2_000, offset = offset)

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", Instant.ofEpochMilli(now - 1_000).toString())
                parameters.append("tom", Instant.ofEpochMilli(now + 1_000).toString())
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()

        assertTrue(dashboard.pendingMismatch.isEmpty())
    }


    @Test
    fun `dashboard bruker nyeste pending før utbetalingen`() = runTest(TestRuntime.context) {
        val now = nextPendingMismatchTimestamp()
        val uid = UUID.randomUUID().toString()

        save(Channel.PendingUtbetalinger, key = uid, value = utbetalingJson(uid, listOf(periode(beløp = 500u))), timestamp = now, offset = offset)
        save(Channel.PendingUtbetalinger, key = uid, value = utbetalingJson(uid, listOf(periode(beløp = 1_000u))), timestamp = now + 1_000, offset = offset)
        save(Channel.Utbetalinger, key = uid, value = utbetalingJson(uid, listOf(periode(beløp = 1_000u))), timestamp = now + 2_000, offset = offset)

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", Instant.ofEpochMilli(now - 1_000).toString())
                parameters.append("tom", Instant.ofEpochMilli(now + 3_000).toString())
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()

        assertTrue(dashboard.pendingMismatch.isEmpty())
    }


    @Test
    fun `dashboard viser pending mismatch når lastPeriodeId er ulik`() = runTest(TestRuntime.context) {
        val now = nextPendingMismatchTimestamp()
        val uid = UUID.randomUUID().toString()

        save(Channel.PendingUtbetalinger, key = uid, value = utbetalingJson(uid, listOf(periode()), "11363#0"), timestamp = now, offset = offset)
        save(Channel.Utbetalinger, key = uid, value = utbetalingJson(uid, listOf(periode()), "11363#1"), timestamp = now + 1_000, offset = offset)

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", Instant.ofEpochMilli(now - 1_000).toString())
                parameters.append("tom", Instant.ofEpochMilli(now + 2_000).toString())
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()

        assertEquals(uid, dashboard.pendingMismatch.single().uid)
    }


    @Test
    fun `dashboard har ingen pending mismatch når lastPeriodeId er lik`() = runTest(TestRuntime.context) {
        val now = nextPendingMismatchTimestamp()
        val uid = UUID.randomUUID().toString()
        val value = utbetalingJson(uid, listOf(periode()), "11363#0")

        save(Channel.PendingUtbetalinger, key = uid, value = value, timestamp = now, offset = offset)
        save(Channel.Utbetalinger, key = uid, value = value, timestamp = now + 1_000, offset = offset)

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", Instant.ofEpochMilli(now - 1_000).toString())
                parameters.append("tom", Instant.ofEpochMilli(now + 2_000).toString())
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()

        assertTrue(dashboard.pendingMismatch.isEmpty())
    }


    @Test
    fun `dashboard rapporterer bare mismatch for riktig uid`() = runTest(TestRuntime.context) {
        val now = nextPendingMismatchTimestamp()
        val mismatchUid = UUID.randomUUID().toString()
        val matchingUid = UUID.randomUUID().toString()

        save(Channel.PendingUtbetalinger, key = mismatchUid, value = utbetalingJson(mismatchUid, listOf(periode(beløp = 2_000u))), timestamp = now, offset = offset)
        save(Channel.Utbetalinger, key = mismatchUid, value = utbetalingJson(mismatchUid, listOf(periode(beløp = 1_000u))), timestamp = now + 1_000, offset = offset)
        val matchingValue = utbetalingJson(matchingUid, listOf(periode()))
        save(Channel.PendingUtbetalinger, key = matchingUid, value = matchingValue, timestamp = now, offset = offset)
        save(Channel.Utbetalinger, key = matchingUid, value = matchingValue, timestamp = now + 1_000, offset = offset)

        val dashboard = TestRuntime.ktor.httpClient.get("/api/dashboard") {
            url {
                parameters.append("fom", Instant.ofEpochMilli(now - 1_000).toString())
                parameters.append("tom", Instant.ofEpochMilli(now + 2_000).toString())
            }
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<Dashboard>()

        assertEquals(mismatchUid, dashboard.pendingMismatch.single().uid)
    }


    private fun periode(
        fom: LocalDate = LocalDate.of(2025, 1, 1),
        tom: LocalDate = LocalDate.of(2025, 1, 31),
        beløp: UInt = 1_000u
    ) = Utbetalingsperiode(fom, tom, beløp)

    private fun utbetalingJson(
        uid: String,
        perioder: List<Utbetalingsperiode>,
        lastPeriodeId: String = "11363#0"
    ): String = KotlinxJson.encodeToString(
        Utbetaling(
            dryrun = false,
            originalKey = "key-$uid",
            fagsystem = Fagsystem.AAP,
            uid = UtbetalingId(UUID.fromString(uid)),
            action = Action.CREATE,
            førsteUtbetalingPåSak = true,
            sakId = SakId("sak-1"),
            behandlingId = BehandlingId("behandling-1"),
            lastPeriodeId = PeriodeId.decode(lastPeriodeId),
            personident = Personident("12345678910"),
            vedtakstidspunkt = LocalDateTime.of(2025, 1, 1, 12, 0),
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = Navident("Z123456"),
            saksbehandlerId = Navident("Z123456"),
            periodetype = Periodetype.UKEDAG,
            avvent = null,
            perioder = perioder
        )
    )

    private suspend fun saveAvstemming(
        fagsystem: Fagsystem,
        fom: LocalDate,
        tom: LocalDate,
        timestamp: Instant = LocalDate.now().minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
    ) {
        save(
            Channel.Avstemming,
            value = TestData.avstemmingXml(
                fagsystem = fagsystem,
                fom = fom.atStartOfDay(),
                tom = tom.atStartOfDay(),
            ),
            offset = offset,
            timestamp = timestamp.toEpochMilli(),
        )
    }


    private suspend fun save(
        channel: Channel,
        key: String = UUID.randomUUID().toString(),
        value: String = """{ "sakId": "123" }""",
        timestamp: Long = Instant.now().toEpochMilli(),
        commitHash: String = "test",
        offset: Long,
        headers: List<Header> = emptyList(),
    ) {
        val dao = Daos(
            topic_name = channel.topic.name,
            version = "v1",
            key = key,
            value = value,
            partition = 0,
            offset = offset,
            timestamp_ms = timestamp,
            stream_time_ms = timestamp,
            system_time_ms = timestamp,
            trace_id = null,
            commit = commitHash,
            headers = headers,
        )

        transaction {
            dao.insert(channel.table)
        }
    }
}
