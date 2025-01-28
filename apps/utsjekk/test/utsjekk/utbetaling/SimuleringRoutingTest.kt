package utsjekk.utbetaling

import TestRuntime
import httpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.ApiError

class SimuleringRoutingTest {

  @Test
  fun `kan simulere enkel utbetaling`() = runTest {
    val utbetaling =
        UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            listOf(UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u)),
        )

    val uid = UUID.randomUUID()
    val res =
        httpClient.post("/utbetalinger/$uid/simuler") {
          bearerAuth(TestRuntime.azure.generateToken())
          contentType(ContentType.Application.Json)
          setBody(utbetaling)
        }

    assertEquals(HttpStatusCode.OK, res.status)
  }

  @Test
  fun `can simulate Utbetaling with multiple MND`() =
      runTest() {
        val utbetaling =
            UtbetalingApi.dagpenger(
                vedtakstidspunkt = 1.feb,
                listOf(
                    UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u),
                    UtbetalingsperiodeApi(1.mar, 31.mar, 24_000u),
                ),
            )

        val uid = UUID.randomUUID()
        val res =
            httpClient.post("/utbetalinger/$uid/simuler") {
              bearerAuth(TestRuntime.azure.generateToken())
              contentType(ContentType.Application.Json)
              setBody(utbetaling)
            }

        assertEquals(HttpStatusCode.OK, res.status)
      }

  @Test
  fun `bad request with different kinds of utbetalingsperiode`() =
      runTest() {
        val utbetaling =
            UtbetalingApi.dagpenger(
                vedtakstidspunkt = 1.feb,
                listOf(
                    UtbetalingsperiodeApi(1.feb, 29.feb, 24_000u), // <-- must be a single day or..
                    UtbetalingsperiodeApi(
                        1.mar, 1.mar, 800u), // <-- must be sent in a different request
                ),
            )
        val uid = UUID.randomUUID()
        val res =
            httpClient.post("/utbetalinger/$uid/simuler") {
              bearerAuth(TestRuntime.azure.generateToken())
              contentType(ContentType.Application.Json)
              setBody(utbetaling)
            }

        assertEquals(HttpStatusCode.BadRequest, res.status)
        val error = res.body<ApiError.Response>()
        assertEquals(error.msg, "inkonsistens blant datoene i periodene.")
        assertEquals(error.doc, "https://navikt.github.io/utsjekk-docs/utbetalinger/perioder")
      }
}
