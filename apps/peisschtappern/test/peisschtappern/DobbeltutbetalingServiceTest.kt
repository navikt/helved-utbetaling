package peisschtappern

import libs.kotlinx.KotlinxJson
import models.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DobbeltutbetalingServiceTest {

    @Test
    fun `finn returnerer tom liste når ingen linjer er duplikater`() {
        val daos = listOf(
            statusDao(linjer = listOf(linje()))
        )
        val result = DobbeltutbetalingService.finn(daos)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `finn returnerer suspect når samme linje finnes i to statusmeldinger`() {
        val daos = listOf(
            statusDao(key = "k1", offset = 1, linjer = listOf(linje())),
            statusDao(key = "k2", offset = 2, linjer = listOf(linje())),
        )
        val result = DobbeltutbetalingService.finn(daos)

        assertEquals(1, result.size)
        assertEquals(2, result.single().kilder.size)
    }

    @Test
    fun `finn ignorerer linjer med beløp 0`() {
        val daos = listOf(
            statusDao(key = "k1", offset = 1, linjer = listOf(linje(beløp = 0u))),
            statusDao(key = "k2", offset = 2, linjer = listOf(linje(beløp = 0u))),
        )
        val result = DobbeltutbetalingService.finn(daos)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `finn skiller på behandlingId - ulike behandlinger er ikke duplikater`() {
        val daos = listOf(
            statusDao(key = "k1", offset = 1, linjer = listOf(linje(behandlingId = "beh-1"))),
            statusDao(key = "k2", offset = 2, linjer = listOf(linje(behandlingId = "beh-2"))),
        )
        val result = DobbeltutbetalingService.finn(daos)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `finn akkumulerer alle kilder på samme suspect`() {
        val daos = listOf(
            statusDao(key = "k1", offset = 1, linjer = listOf(linje())),
            statusDao(key = "k2", offset = 2, linjer = listOf(linje())),
            statusDao(key = "k3", offset = 3, linjer = listOf(linje())),
        )
        val result = DobbeltutbetalingService.finn(daos)

        assertEquals(1, result.size)
        assertEquals(3, result.single().kilder.size)
    }

    @Test
    fun `finn håndterer to ulike duplikater i samme batch`() {
        val daos = listOf(
            statusDao(key = "k1", offset = 1, linjer = listOf(linje(behandlingId = "beh-1"), linje(behandlingId = "beh-2"))),
            statusDao(key = "k2", offset = 2, linjer = listOf(linje(behandlingId = "beh-1"), linje(behandlingId = "beh-2"))),
        )
        val result = DobbeltutbetalingService.finn(daos)

        assertEquals(2, result.size)
        result.forEach { assertEquals(2, it.kilder.size) }
    }

    private fun statusDao(
        key: String = UUID.randomUUID().toString(),
        partition: Int = 0,
        offset: Long = 1L,
        linjer: List<DetaljerLinje> = emptyList(),
    ): Daos {
        val statusReply = StatusReply(
            status = Status.OK,
            detaljer = Detaljer(ytelse = Fagsystem.AAP, linjer = linjer),
        )
        val now = Instant.now().toEpochMilli()
        return Daos(
            version = "v1",
            topic_name = Topics.status.name,
            key = key,
            value = KotlinxJson.encodeToString(statusReply),
            partition = partition,
            offset = offset,
            timestamp_ms = now,
            stream_time_ms = now,
            system_time_ms = now,
            trace_id = null,
        )
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
