package peisschtappern

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.auth.JwksClient
import libs.auth.JwtVerifier
import libs.kafka.Streams
import libs.kafka.StreamsMock
import libs.kotlinx.KotlinxJson
import models.ApiError
import models.badRequest
import org.http4k.core.*
import java.net.http.HttpClient
import kotlin.test.*

class HttpTest {
    @Test
    fun `probes reflect Kafka health without authentication`() {
        val kafka = object : Streams by StreamsMock() {
            override fun ready() = false
            override fun live() = true
        }
        val meters = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        try {
            val handler = probes(kafka, meters)
            assertEquals(Status.LOCKED, handler(Request(Method.GET, "/actuator/ready")).status)
            assertEquals(Status.OK, handler(Request(Method.GET, "/actuator/live")).status)
            assertEquals(Status.OK, handler(Request(Method.GET, "/actuator/metric")).status)
        } finally {
            meters.close()
        }
    }

    @Test
    fun `authentication verifies tokens and carries audit claims`() {
        AzureFake().use { azure ->
            HttpClient.newHttpClient().use { http ->
                val verifier = JwtVerifier(JwksClient(azure.config.jwks, http), azure.config)
                var audit: Audit? = null
                val handler = azureAuthFilter(verifier).then { request ->
                    audit = Audit.from(request, "retting")
                    Response(Status.OK)
                }

                val request = Request(Method.GET, "/api/topics")
                assertEquals(Status.UNAUTHORIZED, handler(request).status)
                assertEquals(Status.UNAUTHORIZED, handler(request.header("Authorization", "Bearer invalid")).status)
                assertEquals(Status.UNAUTHORIZED, handler(request.header("Authorization", "Basic ${azure.generateToken()}")).status)
                assertNull(audit)
                assertEquals(Status.OK, handler(request.header("Authorization", "Bearer ${azure.generateToken()}")).status)
                assertEquals(Audit("T123456", "test", "test@nav.no", "retting"), audit)
            }
        }
    }

    @Test
    fun `malformed JSON returns structured bad request`() {
        val handler = errorFilter.then { request ->
            request.receive<EndreUtbetalingRequest>()
            Response(Status.OK)
        }
        for (body in listOf("{", "{}")) {
            val response = handler(Request(Method.POST, "/endre-utbetaling").body(body))
            assertEquals(Status.BAD_REQUEST, response.status)
            assertEquals(400, KotlinxJson.decodeFromString<ApiError>(response.bodyString()).statusCode)
        }
    }

    @Test
    fun `domain errors retain status and JSON body`() {
        val handler = errorFilter.then { badRequest("reason er påkrevd") }
        val response = handler(Request(Method.POST, "/endre-utbetaling"))
        val error = KotlinxJson.decodeFromString<ApiError>(response.bodyString())
        assertEquals(Status.BAD_REQUEST, response.status)
        assertEquals("reason er påkrevd", error.msg)
    }
}
