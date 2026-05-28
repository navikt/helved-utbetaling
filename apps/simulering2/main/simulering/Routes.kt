package simulering

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.http4k.core.*
import org.http4k.lens.BiDiBodyLens
import org.http4k.routing.bind
import org.http4k.routing.routes
import simulering.models.rest.UtbetalingsoppdragDto
import simulering.models.rest.rest
import simulering.models.soap.soap.SimulerBeregningRequest

private val oppdragLens: BiDiBodyLens<UtbetalingsoppdragDto> = Jackson.autoBody<UtbetalingsoppdragDto>().toLens()
private val simRequestLens: BiDiBodyLens<rest.SimuleringRequest> = Jackson.autoBody<rest.SimuleringRequest>().toLens()
private val responseLens: BiDiBodyLens<rest.SimuleringResponse> = Jackson.autoBody<rest.SimuleringResponse>().toLens()

fun simuleringRoutes(service: SimuleringService) = routes(
    "/simuler" bind Method.POST to { req ->
        val dto = oppdragLens(req)
        val request = SimulerBeregningRequest.from(dto)
        val result = service.simuler(request)
        Response(Status.OK).with(responseLens of result)
    },
    "/simulering" bind Method.POST to { req ->
        val request = simRequestLens(req)
        val result = service.simuler(request)
        Response(Status.OK).with(responseLens of result)
    },
)

fun actuatorRoutes(prometheus: PrometheusMeterRegistry) = routes(
    "/actuator/metrics" bind Method.GET to {
        Response(Status.OK).body(prometheus.scrape())
    },
    "/actuator/live" bind Method.GET to {
        Response(Status.OK).body("live")
    },
    "/actuator/ready" bind Method.GET to {
        Response(Status.OK).body("ready")
    },
)
