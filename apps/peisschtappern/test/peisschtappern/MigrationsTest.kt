package peisschtappern

import kotlinx.coroutines.test.runTest
import libs.jdbc.concurrency.transaction
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class MigrationsTest {
    @Test
    fun `lagrer OK-status for oppdrag ved insert`() = runTest(TestRuntime.context) {
        val sakId = "asljcnqw"
        saveOppdrag(value = TestData.oppdragXml(sakId, "00"))

        val dao = transaction {
            Daos.findOppdrag(sakId, "TILTPENG").single()
        }

        assertEquals("OK", dao.status)
    }

    @Test
    fun `lagrer FEILET-status for oppdrag ved insert`() = runTest(TestRuntime.context) {
        val sakId = "lknsdvbowubc"
        saveOppdrag(value = TestData.oppdragXml(sakId, "08"))

        val dao = transaction {
            Daos.findOppdrag(sakId, "TILTPENG").single()
        }

        assertEquals("FEILET", dao.status)
    }

    @Test
    fun `lagrer ikke status for oppdrag uten kvittering`() = runTest(TestRuntime.context) {
        val sakId = "dascolkjnv"
        saveOppdrag(value = TestData.oppdragXml(sakId))

        val dao = transaction {
            Daos.findOppdrag(sakId, "TILTPENG").single()
        }

        assertNull(dao.status)
    }

    @Test
    fun `lagrer OK-status for statusmelding ved insert`() = runTest(TestRuntime.context) {
        val key = UUID.randomUUID().toString()
        saveStatus(key, status("OK"))

        val dao = transaction {
            Daos.findStatusByKeys(listOf(key)).single()
        }

        assertEquals("OK", dao.status)
    }

    @Test
    fun `lagrer FEILET-status for statusmelding ved insert`() = runTest(TestRuntime.context) {
        val key = UUID.randomUUID().toString()
        saveStatus(key, status("FEILET"))

        val dao = transaction {
            Daos.findStatusByKeys(listOf(key)).single()
        }

        assertEquals("FEILET", dao.status)
    }

    @Test
    fun `lagrer fagsystem for avstemminger`() = runTest(TestRuntime.context) {
        val key = UUID.randomUUID().toString()
        saveAvstemming(key, avstemming())

        val dao = transaction {
            Daos.find(Table.avstemming, 1, listOf(key)).single()
        }

        assertEquals("TILTPENG", dao.fagsystem)
    }

    private suspend fun saveAvstemming(
        key: String = UUID.randomUUID().toString(),
        value: String = avstemming(),
        timestamp: Long = Instant.now().toEpochMilli(),
        commitHash: String = "test",
        offset: Long = 1,
    ) {
        val dao = Daos(
            topic_name = Channel.Avstemming.topic.name,
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
            dao.insert(Channel.Avstemming.table)
        }
    }

    private fun avstemming(fagsystem: String = "TILTPENG") = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <ns2:avstemmingsdata xmlns:ns2="http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1">
            <aksjon>
                <aksjonType>AVSL</aksjonType>
                <kildeType>AVLEV</kildeType>
                <avstemmingType>GRSN</avstemmingType>
                <avleverendeKomponentKode>$fagsystem</avleverendeKomponentKode>
                <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                <underkomponentKode>TILTPENG</underkomponentKode>
                <nokkelFom>2026-02-19-00.00.00.000000</nokkelFom>
                <nokkelTom>2026-02-19-23.59.59.999999</nokkelTom>
                <avleverendeAvstemmingId>esuPt-vwRYWbmV9yp-DjQw</avleverendeAvstemmingId>
                <brukerId>$fagsystem</brukerId>
            </aksjon>
        </ns2:avstemmingsdata>
    """.trimIndent()

    private suspend fun saveStatus(
        key: String = UUID.randomUUID().toString(),
        value: String = status(),
        timestamp: Long = Instant.now().toEpochMilli(),
        commitHash: String = "test",
        offset: Long = 1,
    ) {
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