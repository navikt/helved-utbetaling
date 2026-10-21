package abetal

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.Streams
import libs.kafka.StreamsMock
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.server.SunHttpLoom
import org.http4k.server.asServer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.*

class RoutingTest {
    @Test
    fun `health metrics`() {
        var ready = false
        var live = true
        val kafka = object : Streams by StreamsMock() {
            override fun ready() = ready
            override fun live() = live
        }
        val meters = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        try {
            val handler = probes(kafka, meters)
            assertEquals(Status.LOCKED, handler(Request(Method.GET, "/actuator/ready")).status)
            assertEquals(Status.OK, handler(Request(Method.GET, "/actuator/live")).status)
            ready = true
            live = false
            assertEquals(Status.OK, handler(Request(Method.GET, "/actuator/ready")).status)
            assertEquals(Status.LOCKED, handler(Request(Method.GET, "/actuator/live")).status)
        } finally {
            meters.close()
        }
    }

    @Test
    fun `prometheus metrics`() {
        val meters = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        try {
            meters.counter("abetal_test").increment()
            probes(StreamsMock(), meters).asServer(SunHttpLoom(0)).start().use { server ->
                HttpClient.newHttpClient().use { client ->
                    val request = HttpRequest.newBuilder(URI("http://localhost:${server.port()}/actuator/metric")).build()
                    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                    assertEquals(200, response.statusCode())
                    assertEquals("text/plain; version=0.0.4; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow())
                    assertContains(response.body(), "abetal_test_total 1.0")
                }
            }
        } finally {
            meters.close()
        }
    }
}
