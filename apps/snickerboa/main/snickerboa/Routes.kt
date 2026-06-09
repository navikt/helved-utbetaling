package snickerboa

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import java.util.UUID
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import libs.kafka.JsonSerde
import models.AapUtbetaling
import models.DpUtbetaling
import models.HistoriskUtbetaling
import models.TpUtbetaling
import models.TsDto
import models.ValpUtbetaling

fun Route.api(correlator: RequestReplyCorrelator) {
    suspend fun ApplicationCall.respond(response: UtbetalingResponse) {
        when (response) {
            is UtbetalingResponse.Status -> respond(response.statusCode, response.body)
            is UtbetalingResponse.Simulering -> respond(response.statusCode, response.body)
        }
    }

    post("/abetal/aap"){
        val dto = call.receive<AapUtbetaling>()
        val txId = UUID.randomUUID()
        call.respond(correlator.handleUtbetaling(dto.dryrun, txId) {
            correlator.producers.produceAap(it, JsonSerde.json.encodeToString(dto).toByteArray())
        })
    }

    post("/abetal/dp") {
        val dto = call.receive<DpUtbetaling>()
        val txId = UUID.randomUUID()
        call.respond(correlator.handleUtbetaling(dto.dryrun, txId) {
            correlator.producers.produceDp(it, JsonSerde.json.encodeToString(dto).toByteArray())
        })
    }

    post("/abetal/dp/{transaction_id}") {
        val txId = call.parameters["transaction_id"]?.let { UUID.fromString(it) }
            ?: throw IllegalArgumentException("Ugyldig UUID i path")
        val dto = call.receive<DpUtbetaling>()
        call.respond(correlator.handleUtbetaling(dto.dryrun, txId) {
            correlator.producers.produceDp(it, JsonSerde.json.encodeToString(dto).toByteArray())
        })
    }

    post("/abetal/ts") {
        val dto = call.receive<TsDto>()
        val txId = UUID.randomUUID()
        call.respond(correlator.handleUtbetaling(dto.dryrun, txId) {
            correlator.producers.produceTs(it, JsonSerde.json.encodeToString(dto).toByteArray())
        })
    }

    post("/abetal/tp") {
        val dto = call.receive<TpUtbetaling>()
        val txId = UUID.randomUUID()
        call.respond(correlator.handleUtbetaling(dto.dryrun, txId) {
            correlator.producers.produceTp(it, JsonSerde.json.encodeToString(dto).toByteArray())
        })
    }

    post("/abetal/historisk") {
        val dto = call.receive<HistoriskUtbetaling>()
        val txId = UUID.randomUUID()
        call.respond(correlator.handleUtbetaling(dto.dryrun, txId) {
            correlator.producers.produceHistorisk(it, JsonSerde.json.encodeToString(dto).toByteArray())
        })
    }

    post("/abetal/valp") {
        val dto = call.receive<ValpUtbetaling>()
        val txId = UUID.randomUUID()
        call.respond(correlator.handleUtbetaling(dto.dryrun, txId) {
            correlator.producers.produceValp(it, JsonSerde.json.encodeToString(dto).toByteArray())
        })
    }

    // Brukes for å teste ikke-deserialiserbare meldinger
    post("/abetal/raw/{fagsystem}") {
        val fagsystem = call.parameters["fagsystem"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Fagsystem parameter is required")
        val body = call.receive<ByteArray>()
        val txId = UUID.randomUUID()

        call.respond(correlator.handleUtbetaling(false, txId) {
            when (fagsystem) {
                "dp" -> correlator.producers.produceDp(it, body)
                "ts" -> correlator.producers.produceTs(it, body)
                "tp" -> correlator.producers.produceTp(it, body)
                "aap" -> correlator.producers.produceAap(it, body)
                "historisk" -> correlator.producers.produceHistorisk(it, body)
            }
        })
    }
}

fun Routing.probes(meters: PrometheusMeterRegistry) {
    route("/actuator") {
        get("/metric") {
            call.respond(meters.scrape())
        }
        get("/ready") {
            call.respond(HttpStatusCode.OK)
        }
        get("/live") {
           call.respond(HttpStatusCode.OK)
        }
    }
}
