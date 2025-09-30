package peisschtappern

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.*
import libs.kafka.Streams
import libs.kafka.Topic
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.utils.appLog
import libs.utils.secureLog
import models.Fagsystem
import java.time.Instant
import peisschtappern.Dao.Companion.findAll

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

fun Route.api(manuellOppdragService: ManuellOppdragService) {
    route("/api") {
        get {
            val channels = call.queryParameters["topics"]?.split(",")?.mapNotNull(Channel::findOrNull) ?: Channel.all()
            val limit = call.queryParameters["limit"]?.toInt() ?: 10000
            val key = call.queryParameters["key"]?.split(",")
            val value = call.queryParameters["value"]?.split(",")
            val fom = call.queryParameters["fom"]
                ?.runCatching { Instant.parse(this).toEpochMilli() }
                ?.getOrNull()
            val tom = call.queryParameters["tom"]
                ?.runCatching { Instant.parse(this).toEpochMilli() }
                ?.getOrNull()

            val daos = withContext(Jdbc.context + Dispatchers.IO) {
                transaction {
                    findAll(channels, limit, key, value, fom, tom)
                }
            }

            call.respond(daos)
        }

        route("/saker") {
            get {
                val saker = withContext(Jdbc.context + Dispatchers.IO) {
                    transaction {
                        Dao.find(Channel.Saker.table, Integer.MAX_VALUE)
                    }
                }

                call.respond(saker)
            }

            get("/{sakId}/{fagsystem}") {
                val sakId = call.parameters["sakId"]!!
                val fagsystem: Fagsystem = Fagsystem.from(call.parameters["fagsystem"]!!)

                val hendelser: List<Dao> = withContext(Jdbc.context + Dispatchers.IO) {
                    transaction {
                        coroutineScope {
                            val deferred: List<Deferred<List<Dao>>> = listOf(
                                async { Dao.findOppdrag(sakId, fagsystem.fagområde) },
                                async { Dao.findKvitteringer(sakId, fagsystem.fagområde) },
                                async { Dao.findUtbetalinger(sakId, fagsystem.name) },
                                async { Dao.findSimuleringer(sakId, fagsystem.fagområde) },
                                async { if (fagsystem == Fagsystem.DAGPENGER) Dao.findDpUtbetalinger(sakId) else emptyList()},
                                async { if (fagsystem === Fagsystem.DAGPENGER) Dao.findDpInternUtbetalinger(sakId) else emptyList()},
                                async { Dao.findSaker(sakId, fagsystem.name) },
                                async {
                                    val keys = Dao.findOppdrag(sakId, fagsystem.fagområde).map { it.key }
                                    Dao.findStatusByKeys(keys)
                                }
                            )

                            deferred.awaitAll()
                                .flatten()
                                .sortedByDescending { it.timestamp_ms }
                        }
                    }
                }

                call.respond(hendelser)
            }
        }

        route("/brann") {
            get {
                val timers = withContext(Jdbc.context + Dispatchers.IO) {
                    transaction {
                        TimerDao.findAll()
                    }
                }

                call.respond(timers)
            }

            delete("/{key}") {
                withContext(Jdbc.context + Dispatchers.IO) {
                    transaction {
                        TimerDao.delete(call.parameters["key"]!!)
                    }
                }
            }
        }
    }

    post("/manuell-kvittering") {
        val request = call.receive<KvitteringRequest>()

        try {
            manuellOppdragService.addKvitteringManuelt(
                oppdragXml = request.oppdragXml,
                messageKey = request.messageKey,
                alvorlighetsgrad = request.alvorlighetsgrad,
                beskrMelding = request.beskrMelding,
                kodeMelding = request.kodeMelding
            )
            call.respond(HttpStatusCode.OK, "Created kvittering for uid:${request.messageKey} on ${oppdrag.name}")
        } catch (e: Exception) {
            val msg = "Failed to create kvittering for uid:${request.messageKey}"
            appLog.error(msg)
            secureLog.error(msg, e)
            call.respond(HttpStatusCode.BadRequest, msg)
        }
    }

    post("/manuell-oppdrag") {
        val request = call.receive<OppdragRequest>()

        try {
            manuellOppdragService.sendOppdragManuelt(
                oppdragXml = request.oppdragXml,
                messageKey = request.messageKey

            )
            call.respond(HttpStatusCode.OK, "Sent oppdrag for uid:${request.messageKey} on ${oppdrag.name}")
        } catch (e: Exception) {
            val msg = "Failed to send oppdrag for uid:${request.messageKey}"
            appLog.error(msg)
            secureLog.error(msg, e)
            call.respond(HttpStatusCode.BadRequest, msg)
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

data class OppdragRequest(
    val messageKey: String,
    val oppdragXml: String
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
    data object Status : Channel(Topics.status, Table.status, 12)
    data object PendingUtbetalinger : Channel(Topics.pendingUtbetalinger, Table.pending_utbetalinger, 13)
    data object Fk : Channel(Topics.fk, Table.fk, 14)
    data object DpIntern : Channel(Topics.dpIntern, Table.dpIntern, 15)
    data object Dp : Channel(Topics.dp, Table.dp, 16)
    data object TsIntern : Channel(Topics.tsIntern, Table.tsIntern, 17)
    data object TpIntern : Channel(Topics.tpIntern, Table.tpIntern, 18)

    companion object {
        fun all(): List<Channel> = Channel::class.sealedSubclasses.map { it.objectInstance as Channel }
        fun findOrNull(topicName: String): Channel? = all().firstOrNull { it.topic.name == topicName }
    }
}

