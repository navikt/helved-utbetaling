package simulering

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.Streams
import org.http4k.core.*
import org.http4k.lens.BiDiBodyLens
import org.http4k.routing.bind
import org.http4k.routing.routes
import simulering.v1.SimuleringServiceV1
import simulering.v1.rest

private val simRequestLens: BiDiBodyLens<rest.SimuleringRequest> = KotlinxJson.autoBody<rest.SimuleringRequest>().toLens()
private val responseLens: BiDiBodyLens<rest.SimuleringResponse> = KotlinxJson.autoBody<rest.SimuleringResponse>().toLens()

fun simuleringRoutes(service: SimuleringServiceV1) = routes(
    "/simuler/legacy" bind Method.POST to { req ->
        val request = simRequestLens(req)
        val result = service.simuler(request)
        Response(Status.OK).with(responseLens of result)
    },
)

fun actuatorRoutes(prometheus: PrometheusMeterRegistry, kafka: Streams) = routes(
    "/actuator/metrics" bind Method.GET to {
        Response(Status.OK).body(prometheus.scrape())
    },
    "/actuator/live" bind Method.GET to {
        if (kafka.live()) Response(Status.OK).body("live") else Response(Status.SERVICE_UNAVAILABLE)
    },
    "/actuator/ready" bind Method.GET to {
        if (kafka.ready()) Response(Status.OK).body("ready") else Response(Status.SERVICE_UNAVAILABLE)
    },
)
