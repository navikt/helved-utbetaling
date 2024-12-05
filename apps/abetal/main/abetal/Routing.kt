package abetal

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.*

fun Route.utbetalingRoute(producer: Producer<String, Utbetaling>) {
    route("/utbetalinger/{uid}") {
        post {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: badRequest(msg = "missing path param", field = "uid")

            val dto = call.receive<UtbetalingApi>().also { it.validate() }
            val periodeId = 1u // TODO: select mot databasen
            val domain = Utbetaling.from(dto, periodeId)
            producer.send(ProducerRecord(uid.id.toString(), domain)).get()

            call.response.headers.append(HttpHeaders.Location, "/utbetalinger/${uid.id}")
            call.respond(HttpStatusCode.Created)
//            producer.send(ProducerRecord(uid.id.toString(), domain)) { metadata, err ->
//                suspend {
//                    if (err == null) {
//                        call.response.headers.append(HttpHeaders.Location, "/utbetalinger/${uid.id}")
//                        call.respond(HttpStatusCode.Created)
//                    } else {
//                        call.respond(HttpStatusCode.InternalServerError, "klarte ikke publisere utbetaling på kafka")
//                    }
//                }
//            }
        }
    }
}

private fun uuid(str: String): UUID {
    return try {
        UUID.fromString(str)
    } catch (e: Exception) {
        badRequest(msg = "path param must be UUIDv4", field = "uid")
    }
}

