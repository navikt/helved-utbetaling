package urskog

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.Streams
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes

fun probes(kafka: Streams, micrometer: PrometheusMeterRegistry) = routes(
    "/actuator/metric" bind Method.GET to {
        Response(Status.OK)
            .header("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
            .body(micrometer.scrape())
    },
    "/actuator/ready" bind Method.GET to {
        Response(if (kafka.ready()) Status.OK else Status.LOCKED)
    },
    "/actuator/live" bind Method.GET to {
        Response(if (kafka.live()) Status.OK else Status.LOCKED)
    }
)
