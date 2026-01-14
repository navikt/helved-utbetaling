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

fun Route.api(manuellEndringService: ManuellEndringService) {
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
            val traceId = call.queryParameters["trace_id"]

            val daos = withContext(Jdbc.context + Dispatchers.IO) {
                transaction {
                    findAll(channels, limit, key, value, fom, tom, traceId)
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
                val fagsystemer: List<Fagsystem> =
                    call.parameters["fagsystem"]!!.split(",").map { Fagsystem.from(it.trim()) }
                val hendelser: List<Dao> = withContext(Jdbc.context + Dispatchers.IO) {
                    transaction {
                        coroutineScope {
                            fagsystemer.flatMap { fagsystem ->
                                val deferred: List<Deferred<List<Dao>>> = listOf(
                                    async { Dao.findOppdrag(sakId, fagsystem.fagområde) },
                                    async { Dao.findKvitteringer(sakId, fagsystem.fagområde) },
                                    async { Dao.findUtbetalinger(sakId, fagsystem.name) },
                                    async { Dao.findPendingUtbetalinger(sakId, fagsystem.name) },
                                    async { Dao.findSimuleringer(sakId, fagsystem.fagområde) },
                                    async {
                                        when (fagsystem) {
                                            Fagsystem.AAP -> Dao.findUtbetalinger(sakId, Table.aap)
                                            Fagsystem.DAGPENGER -> Dao.findUtbetalinger(sakId, Table.dp)
                                            Fagsystem.TILLEGGSSTØNADER -> Dao.findUtbetalinger(sakId, Table.ts)
                                            Fagsystem.HISTORISK -> Dao.findUtbetalinger(sakId, Table.historisk)
                                            // TODO: Fagsystem.TILTAKSPENGER -> Dao.findUtbetalinger(sakId, Table.tp)
                                            else -> emptyList()
                                        }
                                    },
                                    async {
                                        when (fagsystem) {
                                            Fagsystem.AAP -> Dao.findUtbetalinger(sakId, Table.aapIntern)
                                            Fagsystem.DAGPENGER -> Dao.findUtbetalinger(sakId, Table.dpIntern)
                                            Fagsystem.TILLEGGSSTØNADER -> Dao.findUtbetalinger(sakId, Table.tsIntern)
                                            Fagsystem.TILTAKSPENGER -> Dao.findUtbetalinger(sakId, Table.tpIntern)
                                            Fagsystem.HISTORISK -> Dao.findUtbetalinger(sakId, Table.historiskIntern)
                                            else -> emptyList()
                                        }
                                    },
                                    async { Dao.findSaker(sakId, fagsystem.name) },
                                    async {
                                        val keys = Dao.findOppdrag(sakId, fagsystem.fagområde).map { it.key }
                                        if (keys.isNotEmpty()) {
                                            Dao.findStatusByKeys(keys)
                                        } else emptyList()
                                    },
                                    async {
                                        val internTables = when (fagsystem) {
                                            Fagsystem.AAP -> Table.aapIntern
                                            Fagsystem.DAGPENGER -> Table.dpIntern
                                            Fagsystem.TILLEGGSSTØNADER -> Table.tsIntern
                                            Fagsystem.TILTAKSPENGER -> Table.tpIntern
                                            Fagsystem.HISTORISK -> Table.historiskIntern
                                            else -> null
                                        }

                                        val internalKeys = if (internTables != null) {
                                            Dao.findUtbetalinger(sakId, internTables).map { it.key }
                                        } else {
                                            emptyList()
                                        }

                                        if (internalKeys.isNotEmpty()) {
                                            Dao.findStatusByKeys(internalKeys)
                                        } else emptyList()
                                    }
                                )

                                deferred.awaitAll().flatten()
                            }
                                .sortedByDescending { it.system_time_ms }
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
            manuellEndringService.addKvitteringManuelt(
                oppdragXml = request.oppdragXml,
                messageKey = request.messageKey,
                alvorlighetsgrad = request.alvorlighetsgrad,
                beskrMelding = request.beskrMelding,
                kodeMelding = request.kodeMelding
            )
            call.respond(
                HttpStatusCode.OK,
                "Created kvittering for uid:${request.messageKey} on ${Topics.oppdrag.name}"
            )
        } catch (e: Exception) {
            val msg = "Failed to create kvittering for uid:${request.messageKey}"
            appLog.error(msg)
            secureLog.error(msg, e)
            call.respond(HttpStatusCode.BadRequest, msg)
        }
    }

    post("/manuell-oppdrag") {
        val request = call.receive<KeyValueRequest>()

        try {
            manuellEndringService.sendOppdragManuelt(
                oppdragXml = request.value,
                messageKey = request.key

            )
            call.respond(HttpStatusCode.OK, "Sent oppdrag for uid:${request.key} on ${Topics.oppdrag.name}")
        } catch (e: Exception) {
            val msg = "Failed to send oppdrag for uid:${request.key}"
            appLog.error(msg)
            secureLog.error(msg, e)
            call.respond(HttpStatusCode.BadRequest, msg)
        }
    }

    post("/pending-til-utbetaling") {
        val request = call.receive<KeyValueRequest>()

        try {
            manuellEndringService.flyttPendingTilUtbetalinger(
                key = request.key,
                value = request.value
            )
            call.respond(
                HttpStatusCode.OK, "Moved utbetaling from helved.pending-utbetalinger.v1" +
                        "for uid:${request.key} on ${Topics.pendingUtbetalinger.name}" + " to ${Topics.utbetalinger.name}"
            )
        } catch (e: Exception) {
            val msg = "Failed to move pending utbetaling for uid:${request.key}"
            appLog.error(msg)
            secureLog.error(msg, e)
            call.respond(HttpStatusCode.BadRequest, msg)
        }
    }

    post("/tombstone-utbetaling") {
        val request = call.receive<TombstoneRequest>()
        when (manuellEndringService.tombstoneUtbetaling(request.key)) {
            true -> call.respond("Tombstoned utbetaling with kafka key ${request.key}")
            false -> call.respond(HttpStatusCode.UnprocessableEntity, "Failed to tombstone utbetaling with kafka key ${request.key}")
        }
    }

    post("/resend-dagpenger") {
        val req = call.receive<KeyValueRequest>()
        when (manuellEndringService.rekjørDagpenger(req.key, req.value)) {
            true -> call.respond("Dagpenge utbetaling rekjørt for key ${req.key}")
            false -> call.respond(HttpStatusCode.UnprocessableEntity, "Feilet rekjøring av dagpenge utbetaling")
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

data class KeyValueRequest(
    val key: String,
    val value: String
)

data class TombstoneRequest(val key: String)

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
    data object AapIntern : Channel(Topics.aapIntern, Table.aapIntern, 6)
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
    data object Ts : Channel(Topics.ts, Table.ts, 19)
    data object Aap : Channel(Topics.aap, Table.aap, 20)
    data object HistoriskIntern : Channel(Topics.historiskIntern, Table.historiskIntern, 21)
    data object Historisk : Channel(Topics.historisk, Table.historisk, 22)

    companion object {
        fun all(): List<Channel> = Channel::class.sealedSubclasses.map { it.objectInstance as Channel }
        fun findOrNull(topicName: String): Channel? = all().firstOrNull { it.topic.name == topicName }
    }
}

