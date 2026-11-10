package simulering

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.Streams
import libs.kafka.StreamsMock
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ActuatorRoutesTest {
    @Test
    fun `probes reflect Kafka lifecycle independently`() {
        var ready = false
        var live = true
        val kafka = object : Streams by StreamsMock() {
            override fun ready() = ready
            override fun live() = live
        }
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        try {
            val routes = actuatorRoutes(registry, kafka)
            assertEquals(Status.SERVICE_UNAVAILABLE, routes(Request(Method.GET, "/actuator/ready")).status)
            assertEquals(Status.OK, routes(Request(Method.GET, "/actuator/live")).status)

            ready = true
            assertEquals(Status.OK, routes(Request(Method.GET, "/actuator/ready")).status)

            ready = false
            live = false
            assertEquals(Status.SERVICE_UNAVAILABLE, routes(Request(Method.GET, "/actuator/ready")).status)
            assertEquals(Status.SERVICE_UNAVAILABLE, routes(Request(Method.GET, "/actuator/live")).status)
        } finally {
            registry.close()
        }
    }
}
