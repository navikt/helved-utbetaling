package peisschtappern

import kotlinx.coroutines.test.runTest
import libs.jdbc.concurrency.transaction
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MigrationsTest {
    @Test
    fun `lagrer OK-status for oppdrag ved insert`() = runTest(TestRuntime.context){
        val sakId = "asljcnqw"
        saveOppdrag(value = TestData.oppdragXml(sakId, "00"))

        val dao = transaction {
            Daos.findOppdrag(sakId, "TILTPENG").single()
        }

        assertEquals("OK", dao.status)
    }

    @Test
    fun `lagrer FEILET-status for oppdrag ved insert`() = runTest(TestRuntime.context){
        val sakId = "lknsdvbowubc"
        saveOppdrag(value = TestData.oppdragXml(sakId, "08"))

        val dao = transaction {
            Daos.findOppdrag(sakId, "TILTPENG").single()
        }

        assertEquals("FEILET", dao.status)
    }

    @Test
    fun `lagrer ikke status for oppdrag uten kvittering`() = runTest(TestRuntime.context){
        val sakId = "dascolkjnv"
        saveOppdrag(value = TestData.oppdragXml(sakId))

        val dao = transaction {
            Daos.findOppdrag(sakId, "TILTPENG").single()
        }

        assertNull(dao.status)
    }

    @Test
    fun `lagrer OK-status for statusmelding ved insert`() = runTest(TestRuntime.context){
        val key = UUID.randomUUID().toString()
        saveStatus(key, status("OK"))

        val dao = transaction {
            Daos.findStatusByKeys(listOf(key)).single()
        }

        assertEquals("OK", dao.status)
    }

    @Test
    fun `lagrer FEILET-status for statusmelding ved insert`() = runTest(TestRuntime.context){
        val key = UUID.randomUUID().toString()
        saveStatus(key, status("FEILET"))

        val dao = transaction {
            Daos.findStatusByKeys(listOf(key)).single()
        }

        assertEquals("FEILET", dao.status)
    }

    private suspend fun saveStatus(
        key: String = UUID.randomUUID().toString(),
        value: String = status(),
        timestamp: Long = Instant.now().toEpochMilli(),
        commitHash: String = "test",
        offset: Long = 1,) {
        val dao = Daos(
            topic_name = Channel.Status.topic.name,
            version = "v1",
            key = key,
            value = value,
            partition = 0,
            offset = offset,
            timestamp_ms = timestamp,
            stream_time_ms = timestamp,
            system_time_ms = timestamp,
            trace_id = null,
            commit = commitHash
        )

        transaction {
            dao.insert(Channel.Status.table)
        }
    }

    private fun status(status: String? = "OK") = """
        {
            "status": "$status",
            "detaljer": [],
            "error": null
        }
    """.trimIndent()

    private suspend fun saveOppdrag(
        key: String = UUID.randomUUID().toString(),
        value: String = TestData.oppdragXml(),
        timestamp: Long = Instant.now().toEpochMilli(),
        commitHash: String = "test",
        offset: Long = 1,
    ) {
        val dao = Daos(
            topic_name = Channel.Oppdrag.topic.name,
            version = "v1",
            key = key,
            value = value,
            partition = 0,
            offset = offset,
            timestamp_ms = timestamp,
            stream_time_ms = timestamp,
            system_time_ms = timestamp,
            trace_id = null,
            commit = commitHash
        )

        transaction {
            dao.insert(Channel.Oppdrag.table)
        }
    }
}