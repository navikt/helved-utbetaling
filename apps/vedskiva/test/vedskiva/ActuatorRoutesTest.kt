package vedskiva

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.Streams
import libs.kafka.StreamsMock
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals

class ActuatorRoutesTest {
    private val streams = ProbeStreams()
    private val metrics = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val app = actuatorRoutes(metrics, streams)

    @AfterEach
    fun close() {
        metrics.close()
    }

    @Test
    fun `oppstart og rebalansering gir ikke restart`() {
        assertEquals(Status.OK, app(Request(Method.GET, "/actuator/live")).status)
        assertEquals(Status.LOCKED, app(Request(Method.GET, "/actuator/ready")).status)

        streams.isReady = true
        assertEquals(Status.OK, app(Request(Method.GET, "/actuator/ready")).status)

        streams.isReady = false
        assertEquals(Status.LOCKED, app(Request(Method.GET, "/actuator/ready")).status)
        assertEquals(Status.OK, app(Request(Method.GET, "/actuator/live")).status)
    }

    @Test
    fun `stoppet consumer gir feilstatus paa helsesjekkene`() {
        streams.isReady = true
        assertEquals(Status.OK, app(Request(Method.GET, "/actuator/live")).status)
        assertEquals(Status.OK, app(Request(Method.GET, "/actuator/ready")).status)
        assertEquals(Status.OK, app(Request(Method.GET, "/actuator/health")).status)

        streams.isLive = false
        streams.isReady = false

        assertEquals(Status.LOCKED, app(Request(Method.GET, "/actuator/live")).status)
        assertEquals(Status.LOCKED, app(Request(Method.GET, "/actuator/ready")).status)
        assertEquals(Status.LOCKED, app(Request(Method.GET, "/actuator/health")).status)
    }
}

private class ProbeStreams : Streams by StreamsMock() {
    var isLive = true
    var isReady = false

    override fun live() = isLive
    override fun ready() = isReady
}
