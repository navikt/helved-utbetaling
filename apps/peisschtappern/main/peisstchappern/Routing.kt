package peisstchappern

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.withContext
import libs.kafka.Streams
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction

fun Routing.probes(kafka: Streams, meters: PrometheusMeterRegistry) {
    route("/probes") {
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

fun Routing.api() {
    get("/api") {
        val topics = call.queryParameters["topics"]?.split(",")?.mapNotNull {
            when (it) {
                Topics.oppdrag.name -> Tables.oppdrag
                Topics.aap.name -> Tables.aap
                Topics.saker.name -> Tables.saker
                Topics.utbetalinger.name -> Tables.utbetalinger
                Topics.kvittering.name -> Tables.kvittering
                Topics.simuleringer.name -> Tables.simuleringer
                else -> null
            }
        } ?: Tables.entries

        val limit = call.queryParameters["limit"]?.toInt() ?: 1000
        val daos = withContext(Jdbc.context) {
            transaction {
                when (val key = call.queryParameters["key"]) {
                    null -> Dao.find(topics, limit)
                    else -> Dao.find(topics, key, limit)
                }
            }
        }

        call.respond(daos)
    }
}
