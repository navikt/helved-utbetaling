package routes

import TestData
import TestRuntime
import fakes.counter
import httpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IverksettingRouteTest {

    @Test
    fun `iverksetter ikke når kill switch for ytelsen er skrudd på`() = runTest {
        TestRuntime.unleash.disable(Fagsystem.DAGPENGER)

        val iverksett = TestData.enIverksettDto()

        val res = httpClient.post("/api/iverksetting") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(iverksett)
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, res.status)
        assertEquals("Iverksetting er skrudd av for fagsystem ${Fagsystem.DAGPENGER}", res.bodyAsText())

        println(counter.get())
    }
}
