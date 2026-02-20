package snickerboa

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import java.util.UUID
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.Streams
import models.AapUtbetaling
import models.DpUtbetaling
import models.HistoriskUtbetaling
import models.TpUtbetaling
import models.TsDto

fun Route.api(correlator: RequestReplyCorrelator) {
    suspend fun ApplicationCall.respond(response: UtbetalingResponse) {
        respond(response.statusCode, response.body)
    }

    post("/abetal/aap"){
        val dto = call.receive<AapUtbetaling>()
        val txId = UUID.randomUUID()
        call.respond(correlator.handleUtbetaling(dto.dryrun, txId) {
            correlator.producers.produceAap(it, dto)
        })
    }

    post("/abetal/dp") {
        val dto = call.receive<DpUtbetaling>()
        val txId = UUID.randomUUID()
        call.respond(correlator.handleUtbetaling(dto.dryrun, txId) {
            correlator.producers.produceDp(it, dto)
        })
    }

    post("/abetal/dp/{transaction_id}") {
        val txId = call.parameters["transaction_id"]?.let { UUID.fromString(it) }
            ?: throw IllegalArgumentException("Ugyldig UUID i path")
        val dto = call.receive<DpUtbetaling>()
        call.respond(correlator.handleUtbetaling(dto.dryrun, txId) {
            correlator.producers.produceDp(it, dto)
        })
    }

    post("/abetal/ts") {
        val dto = call.receive<TsDto>()
        val txId = UUID.randomUUID()
        call.respond(correlator.handleUtbetaling(dto.dryrun, txId) {
            correlator.producers.produceTs(it, dto)
        })
    }

    post("/abetal/tp") {
        val dto = call.receive<TpUtbetaling>()
        val txId = UUID.randomUUID()
        call.respond(correlator.handleUtbetaling(dto.dryrun, txId) {
            correlator.producers.produceTp(it, dto)
        })
    }

    post("/abetal/historisk") {
        val dto = call.receive<HistoriskUtbetaling>()
        val txId = UUID.randomUUID()
        call.respond(correlator.handleUtbetaling(dto.dryrun, txId) {
            correlator.producers.produceHistorisk(it, dto)
        })
    }
}

fun Routing.probes(kafka: Streams, meters: PrometheusMeterRegistry) {
    route("/actuator") {
        get("/metric") {
            call.respond(meters.scrape())
        }
        get("/ready") {
            when (kafka.ready()) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.Locked)
            }
        }
        get("/live") {
            when (kafka.live()) {
                true -> call.respond(HttpStatusCode.OK)
                false -> call.respond(HttpStatusCode.Locked)
            }
        }
    }
}

