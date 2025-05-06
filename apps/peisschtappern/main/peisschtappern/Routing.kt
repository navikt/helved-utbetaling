package peisschtappern

import ManuellKvitteringService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.*
import libs.kafka.Streams
import libs.kafka.Topic
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction
import java.time.Instant
import libs.utils.secureLog

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

fun Routing.api(manuellKvitteringService: ManuellKvitteringService) {
    get("/api") {
        val channels = call.queryParameters["topics"]?.split(",")?.mapNotNull(Channel::findOrNull) ?: Channel.all()
        val limit = call.queryParameters["limit"]?.toInt() ?: 100
        val key = call.queryParameters["key"]
        val value = call.queryParameters["value"]
        val fom = call.queryParameters["fom"]
            ?.runCatching { Instant.parse(this).toEpochMilli() }
            ?.getOrNull()
        val tom = call.queryParameters["tom"]
            ?.runCatching { Instant.parse(this).toEpochMilli() }
            ?.getOrNull()

        val daos = withContext(Jdbc.context + Dispatchers.IO) {
            transaction {
                coroutineScope {
                    val deferred = channels.map { channel ->
                        async {
                            Dao.find(channel.table, limit, key, value, fom, tom)
                        }
                    }

                    deferred.awaitAll()
                        .flatten()
                        .sortedByDescending { it.timestamp_ms }
                        .take(limit)
                }
            }
        }

        call.respond(daos)
    }

    post("/manuell-kvittering") {
        val request = call.receive<KvitteringRequest>()

        try {
            manuellKvitteringService.addKvitteringManuelt(
                oppdragXml = request.oppdragXml,
                messageKey = request.messageKey,
                alvorlighetsgrad = request.alvorlighetsgrad,
                beskrMelding = request.beskrMelding,
                kodeMelding = request.kodeMelding
            )
            call.respond(HttpStatusCode.OK, "Oppdrag med manuell kvittering sent til ${oppdrag.name}")
        } catch (e: Exception) {
            secureLog.error("Legge til kvittering manuelt feilet: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, "Legge til kvittering manuelt feilet: ${e.message}")
        }
    }
}

data class KvitteringRequest(
    val messageKey: String,
    val oppdragXml: String,
    val alvorlighetsgrad: String,
    val beskrMelding: String?,
    val kodeMelding: String?,
)

sealed class Channel(
    val topic: Topic<String, ByteArray>,
    val table: Table,
    val revision: Int, // the topology must create these streams in the same order every time, sort by revision
) {
    data object Avstemming : Channel(Topics.avstemming, Table.avstemming, 0)
    data object Oppdrag : Channel(Topics.oppdrag, Table.oppdrag, 1)
    data object Kvittering : Channel(Topics.kvittering, Table.kvittering, 2)
    data object Simuleringer : Channel(Topics.simuleringer, Table.simuleringer, 3)
    data object Utbetalinger : Channel(Topics.utbetalinger, Table.utbetalinger, 4)
    data object Saker : Channel(Topics.saker, Table.saker, 5)
    data object Aap : Channel(Topics.aap, Table.aap, 6)
    data object DryrunAap : Channel(Topics.dryrunAap, Table.dryrun_aap, 8)
    data object DryrunTp : Channel(Topics.dryrunTp, Table.dryrun_tp, 9)
    data object DryrunTs : Channel(Topics.dryrunTs, Table.dryrun_ts, 10)
    data object DryrunDp : Channel(Topics.dryrunDp, Table.dryrun_dp, 11)

    companion object {
        fun all(): List<Channel> = Channel::class.sealedSubclasses.map { it.objectInstance as Channel }
        fun findOrNull(topicName: String): Channel? = all().firstOrNull { it.topic.name == topicName }
    }
}

