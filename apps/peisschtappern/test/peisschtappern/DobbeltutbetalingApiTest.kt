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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DobbeltutbetalingApiTest {
    private var offset: Long = 1
        get() = field++

    @AfterEach
    fun cleanup() {
        TestRuntime.jdbc.truncate("peisschtappern", "status", "kjent_dobbeltutbetaling")
    }

    @Test
    fun `GET dobbeltutbetalinger returnerer suspects`() = runTest(TestRuntime.context) {
        saveStatus(key = "k1", offset = offset, linjer = listOf(linje()))
        saveStatus(key = "k2", offset = offset, linjer = listOf(linje()))

        val result = TestRuntime.ktor.httpClient.get("/api/brann/dobbeltutbetalinger") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<List<Suspect>>()

        assertEquals(1, result.size)
        assertEquals(2, result.single().kilder.size)
        assertEquals("beh-1", result.single().behandlingId)
    }

    @Test
    fun `GET dobbeltutbetalinger filtrerer ut slukkede`() = runTest(TestRuntime.context) {
        saveStatus(key = "k1", offset = offset, linjer = listOf(linje()))
        saveStatus(key = "k2", offset = offset, linjer = listOf(linje()))

        transaction {
            KjentDobbeltutbetaling(
                behandlingId = "beh-1",
                klassekode = "DAGP",
                fom = LocalDate.of(2026, 1, 1),
                tom = LocalDate.of(2026, 1, 31),
            ).slukk()
        }

        val result = TestRuntime.ktor.httpClient.get("/api/brann/dobbeltutbetalinger") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<List<Suspect>>()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `POST dobbeltutbetalinger lagrer håndtert`() = runTest(TestRuntime.context) {
        val response = TestRuntime.ktor.httpClient.post("/api/brann/dobbeltutbetalinger") {
            bearerAuth(TestRuntime.azure.generateToken())
            parameter("behandlingId", "beh-1")
            parameter("klassekode", "DAGP")
            parameter("fom", "2026-01-01")
            parameter("tom", "2026-01-31")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val kjente = transaction { KjentDobbeltutbetaling.findAll() }
        assertEquals(1, kjente.size)
        with(kjente.single()) {
            assertEquals("beh-1", behandlingId)
            assertEquals("DAGP", klassekode)
            assertEquals(LocalDate.of(2026, 1, 1), fom)
            assertEquals(LocalDate.of(2026, 1, 31), tom)
            assertTrue(håndtertAt != null)
        }
    }

    @Test
    fun `POST dobbeltutbetalinger er idempotent`() = runTest(TestRuntime.context) {
        repeat(3) {
            val response = TestRuntime.ktor.httpClient.post("/api/brann/dobbeltutbetalinger") {
                bearerAuth(TestRuntime.azure.generateToken())
                parameter("behandlingId", "beh-1")
                parameter("klassekode", "DAGP")
                parameter("fom", "2026-01-01")
                parameter("tom", "2026-01-31")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        val kjente = transaction { KjentDobbeltutbetaling.findAll() }
        assertEquals(1, kjente.size)
        assertTrue(kjente.single().håndtertAt != null)
    }

    @Test
    fun `GET dobbeltutbetalinger filtrerer ut håndterte`() = runTest(TestRuntime.context) {
        saveStatus(key = "k1", offset = offset, linjer = listOf(linje()))
        saveStatus(key = "k2", offset = offset, linjer = listOf(linje()))

        TestRuntime.ktor.httpClient.post("/api/brann/dobbeltutbetalinger") {
            bearerAuth(TestRuntime.azure.generateToken())
            parameter("behandlingId", "beh-1")
            parameter("klassekode", "DAGP")
            parameter("fom", "2026-01-01")
            parameter("tom", "2026-01-31")
        }

        val uslukkede = TestRuntime.ktor.httpClient.get("/api/brann/dobbeltutbetalinger") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<List<Suspect>>()

        assertTrue(uslukkede.isEmpty())
    }

    @Test
    fun `håndtering fjerner dobbeltutbetalingen fra uhåndterte`() = runTest(TestRuntime.context) {
        saveStatus(key = "k1", offset = offset, linjer = listOf(linje()))
        saveStatus(key = "k2", offset = offset, linjer = listOf(linje()))

        TestRuntime.ktor.httpClient.post("/api/brann/dobbeltutbetalinger") {
            bearerAuth(TestRuntime.azure.generateToken())
            parameter("behandlingId", "beh-1")
            parameter("klassekode", "DAGP")
            parameter("fom", "2026-01-01")
            parameter("tom", "2026-01-31")
        }

        val now = Instant.now().toEpochMilli()
        val uhåndterte = transaction {
            DobbeltutbetalingService.finnUhåndterte(now - 60_000, now + 60_000)
        }
        assertTrue(uhåndterte.isEmpty())
    }

    @Test
    fun `POST dobbeltutbetalinger slukk er idempotent`() = runTest(TestRuntime.context) {
        repeat(3) {
            val response = TestRuntime.ktor.httpClient.post("/api/brann/dobbeltutbetalinger/slukk") {
                bearerAuth(TestRuntime.azure.generateToken())
                parameter("behandlingId", "beh-1")
                parameter("klassekode", "DAGP")
                parameter("fom", "2026-01-01")
                parameter("tom", "2026-01-31")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        val kjente = transaction { KjentDobbeltutbetaling.findAll() }
        assertEquals(1, kjente.size)
        assertTrue(kjente.single().slukketAt != null)
    }

    @Test
    fun `slukking skjuler brannen men ikke dobbeltutbetalingen i dashboardet`() = runTest(TestRuntime.context) {
        saveStatus(key = "k1", offset = offset, linjer = listOf(linje()))
        saveStatus(key = "k2", offset = offset, linjer = listOf(linje()))

        val response = TestRuntime.ktor.httpClient.post("/api/brann/dobbeltutbetalinger/slukk") {
            bearerAuth(TestRuntime.azure.generateToken())
            parameter("behandlingId", "beh-1")
            parameter("klassekode", "DAGP")
            parameter("fom", "2026-01-01")
            parameter("tom", "2026-01-31")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val kjente = transaction { KjentDobbeltutbetaling.findAll() }
        assertTrue(kjente.single().slukketAt != null)

        val uvarslede = TestRuntime.ktor.httpClient.get("/api/brann/dobbeltutbetalinger") {
            bearerAuth(TestRuntime.azure.generateToken())
        }.body<List<Suspect>>()
        assertTrue(uvarslede.isEmpty())

        val now = Instant.now().toEpochMilli()
        val uhåndterte = transaction {
            DobbeltutbetalingService.finnUhåndterte(now - 60_000, now + 60_000)
        }
        assertEquals(1, uhåndterte.size)
    }

    private suspend fun saveStatus(
        key: String = UUID.randomUUID().toString(),
        offset: Long = 1,
        linjer: List<DetaljerLinje> = emptyList(),
    ) {
        val now = Instant.now().toEpochMilli()
        val statusReply = StatusReply(
            status = Status.OK,
            detaljer = Detaljer(ytelse = Fagsystem.AAP, linjer = linjer),
        )
        val dao = Daos(
            version = "v1",
            topic_name = Topics.status.name,
            key = key,
            value = KotlinxJson.encodeToString(statusReply),
            partition = 0,
            offset = offset,
            timestamp_ms = now,
            stream_time_ms = now,
            system_time_ms = now,
            trace_id = null,
        )
        transaction {
            dao.insert(Channel.Status.table)
        }
    }

    private fun linje(
        behandlingId: String = "beh-1",
        klassekode: String = "DAGP",
        fom: LocalDate = LocalDate.of(2026, 1, 1),
        tom: LocalDate = LocalDate.of(2026, 1, 31),
        beløp: UInt = 5000u,
    ) = DetaljerLinje(
        behandlingId = behandlingId,
        fom = fom,
        tom = tom,
        beløp = beløp,
        vedtakssats = null,
        klassekode = klassekode,
    )
}
