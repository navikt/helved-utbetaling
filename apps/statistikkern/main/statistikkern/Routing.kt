package statistikkern

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes

fun probes(meters: PrometheusMeterRegistry) = routes(
    "/actuator/metric" bind Method.GET to {
        Response(Status.OK).body(meters.scrape())
    },
    "/actuator/ready" bind Method.GET to {
        Response(Status.OK)
    },
    "/actuator/live" bind Method.GET to {
        Response(Status.OK)
    }
)