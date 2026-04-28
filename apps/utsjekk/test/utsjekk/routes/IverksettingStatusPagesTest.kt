package utsjekk.routes

import TestData
import TestRuntime
import httpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import libs.jdbc.concurrency.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import models.StønadTypeTilleggsstønader
import utsjekk.iverksetting.*
import utsjekk.utbetaling.UtbetalingId
import java.time.LocalDateTime
import java.util.UUID

class IverksettingStatusPagesTest {

    @Test
    fun `migrate route gir not found nar iverksetting resultat mangler`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()

        transaction {
            IverksettingDao(iverksetting, LocalDateTime.now()).insert(UtbetalingId(UUID.randomUUID()))
        }

        val res = httpClient.post("/api/iverksetting/v2/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(
                sakId = iverksetting.sakId.id,
                behandlingId = iverksetting.behandlingId.id,
                iverksettingId = iverksetting.iverksettingId?.id,
                meldeperiode = null,
                uidToStønad = UUID.randomUUID() to StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
            ))
        }

        assertEquals(HttpStatusCode.NotFound, res.status)
        assertTrue(res.bodyAsText().contains("\"statusCode\":404"))
    }
}
