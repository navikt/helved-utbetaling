package peisschtappern

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.*
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.kafka.Streams
import libs.kafka.Topic
import libs.utils.appLog
import libs.utils.secureLog
import models.Fagsystem
import java.time.Instant

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
                    Daos.findAll(channels, limit, key, value, fom, tom, traceId)
                }
            }

            call.respond(daos)
        }

        get("/messages") {
            val channels = call.queryParameters["topics"]?.split(",")?.mapNotNull(Channel::findOrNull) ?: Channel.all()
            val page = call.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.queryParameters["pageSize"]?.toIntOrNull() ?: 100
            val key = call.queryParameters["key"]
            val value = call.queryParameters["value"]?.split(",")
            val fom = call.queryParameters["fom"]
                ?.runCatching { Instant.parse(this).toEpochMilli() }
                ?.getOrNull()
            val tom = call.queryParameters["tom"]
                ?.runCatching { Instant.parse(this).toEpochMilli() }
                ?.getOrNull()
            val traceId = call.queryParameters["trace_id"]
            val status = call.queryParameters["status"]?.split(",")
            val orderBy = when (call.queryParameters["orderBy"]) {
                "offset" -> "record_offset"
                "timestamp" -> "system_time_ms"
                null -> null
                else -> {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        "Invalid orderBy parameter: ${call.queryParameters["orderBy"]}"
                    )
                }
            }
            val direction = call.queryParameters["direction"] ?: "DESC"

            if (direction != "ASC" && direction != "DESC") {
                call.respond(HttpStatusCode.BadRequest, "Invalid direction parameter: $direction")
            }

            val result = withContext(Jdbc.context + Dispatchers.IO) {
                transaction {
                    Daos.findAll(channels, page, pageSize, key, value, fom, tom, traceId, status, orderBy, direction)
                }
            }

            call.respond(result)
        }

        get("/{topic}/{partition}/{offset}") {
            val channel = checkNotNull(Channel.findOrNull(call.parameters["topic"]!!)) {
                "Unknown topic ${call.parameters["topic"]}"
            }
            val partition = checkNotNull(call.parameters["partition"]?.toIntOrNull()) {
                "Missing or invalid partition parameter"
            }
            val offset = checkNotNull(call.parameters["offset"]?.toLongOrNull()) {
                "Missing or invalid offset parameter"
            }

            val dao = withContext(Jdbc.context + Dispatchers.IO) {
                transaction {
                    Daos.findSingle(partition, offset, channel.table)
                }
            }

            if (dao != null) {
                call.respond(dao)
            }

            call.respond(HttpStatusCode.NotFound)
        }

        route("/saker") {
            get {
                val saker = withContext(Jdbc.context + Dispatchers.IO) {
                    transaction {
                        Daos.find(Channel.Saker.table, Integer.MAX_VALUE)
                    }
                }

                call.respond(saker)
            }

            get("/{sakId}/{fagsystem}") {
                val sakId = call.parameters["sakId"]!!
                val fagsystemer: List<Fagsystem> =
                    call.parameters["fagsystem"]!!.split(",").map { Fagsystem.from(it.trim()) }
                val hendelser: List<Daos> = withContext(Jdbc.context + Dispatchers.IO) {
                    transaction {
                        coroutineScope {
                            fagsystemer.flatMap { fagsystem ->
                                val deferred: List<Deferred<List<Daos>>> = listOf(
                                    async { Daos.findOppdrag(sakId, fagsystem.fagområde) },
                                    async { Daos.findKvitteringer(sakId, fagsystem.fagområde) },
                                    async { Daos.findUtbetalinger(sakId, fagsystem.name) },
                                    async { Daos.findPendingUtbetalinger(sakId, fagsystem.name) },
                                    async { Daos.findSimuleringer(sakId, fagsystem.fagområde) },
                                    async {
                                        when (fagsystem) {
                                            Fagsystem.AAP -> Daos.findUtbetalinger(sakId, Table.aap)
                                            Fagsystem.DAGPENGER -> Daos.findUtbetalinger(sakId, Table.dp)
                                            Fagsystem.TILLEGGSSTØNADER -> Daos.findUtbetalinger(sakId, Table.ts)
                                            Fagsystem.HISTORISK -> Daos.findUtbetalinger(sakId, Table.historisk)
                                            // TODO: Fagsystem.TILTAKSPENGER -> Daos.findUtbetalinger(sakId, Table.tp)
                                            else -> emptyList()
                                        }
                                    },
                                    async {
                                        when (fagsystem) {
                                            Fagsystem.AAP -> Daos.findUtbetalinger(sakId, Table.aapIntern)
                                            Fagsystem.DAGPENGER -> Daos.findUtbetalinger(sakId, Table.dpIntern)
                                            Fagsystem.TILLEGGSSTØNADER -> Daos.findUtbetalinger(sakId, Table.tsIntern)
                                            Fagsystem.TILTAKSPENGER -> Daos.findUtbetalinger(sakId, Table.tpIntern)
                                            Fagsystem.HISTORISK -> Daos.findUtbetalinger(sakId, Table.historiskIntern)
                                            else -> emptyList()
                                        }
                                    },
                                    async { Daos.findSaker(sakId, fagsystem.name) },
                                    async {
                                        val keys = Daos.findOppdrag(sakId, fagsystem.fagområde).map { it.key }
                                        if (keys.isNotEmpty()) {
                                            Daos.findStatusByKeys(keys)
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
                                            Daos.findUtbetalinger(sakId, internTables).map { it.key }
                                        } else {
                                            emptyList()
                                        }

                                        if (internalKeys.isNotEmpty()) {
                                            Daos.findStatusByKeys(internalKeys)
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

        post("/resend") {
            val request = call.receive<MessageRequest>()
            val channel = requireNotNull(Channel.findOrNull(request.topic)) {
                "Fant ikke topic med navn ${request.topic}"
            }

            withContext(Jdbc.context + Dispatchers.IO) {
                transaction {
                    val message = requireNotNull(Daos.findSingle(request.partition.toInt(), request.offset.toLong(), channel.table)) {
                        "Fant ikke melding på topic ${request.topic} med partition ${request.partition} og offset ${request.offset}"
                    }
                    val value = requireNotNull(message.value) { "Melding mangler value" }

                    val success = when (channel) {
                        is Channel.Oppdrag -> manuellEndringService.sendOppdragManuelt(message.key, value, Audit.from(call))
                        is Channel.Dp, Channel.DpIntern -> manuellEndringService.rekjørDagpenger(message.key, value, Audit.from(call))
                        is Channel.Ts, Channel.TsIntern -> manuellEndringService.rekjørTilleggsstonader(message.key, value, Audit.from(call))
                        else -> error("Støtter ikke innsending av melding for topic ${channel.topic.name}")
                    }

                    if (success) {
                        call.respond(HttpStatusCode.OK, "Sendte melding ${message.key} i topic ${message.topic_name} på nytt")
                    } else {
                        call.respond(HttpStatusCode.UnprocessableEntity, "Feilet å sende melding ${message.key} i topic ${message.topic_name} på nytt")
                    }
                }
            }
        }
    }

    post("/manuell-kvittering") {
        val request = call.receive<KvitteringRequest>()

        try {
            withContext(Jdbc.context + Dispatchers.IO) {
                transaction {
                    val oppdrag = Daos.findSingle(request.partition.toInt(), request.offset.toLong(), Channel.Oppdrag.table)
                    requireNotNull(oppdrag?.value) { "Kan ikke lage kvittering for melding uten oppdrag" } // Burde i teorien ikke kunne oppstå
                    manuellEndringService.addKvitteringManuelt(
                        oppdragXml = oppdrag.value,
                        messageKey = oppdrag.key,
                        alvorlighetsgrad = request.alvorlighetsgrad,
                        beskrMelding = request.beskrMelding,
                        kodeMelding = request.kodeMelding,
                        audit = Audit.from(call),
                    )
                }
            }
            call.respond(
                HttpStatusCode.OK,
                "Created kvittering for uid:${request.key} on ${Topics.oppdrag.name}"
            )
        } catch (e: Exception) {
            val msg = "Failed to create kvittering for uid:${request.key}"
            appLog.error(msg)
            secureLog.error(msg, e)
            call.respond(HttpStatusCode.BadRequest, msg)
        }
    }

    post("/pending-til-utbetaling") {
        val request = call.receive<MessageRequest>()

        try {
            withContext(Jdbc.context + Dispatchers.IO) {
                transaction {
                    val utbetaling = requireNotNull(
                        Daos.findSingle(
                            request.partition.toInt(),
                            request.offset.toLong(),
                            Channel.PendingUtbetalinger.table
                        )
                    ) {
                        "Kan ikke flytte pending utbetaling. Fant ikke utbetaling i topic ${request.topic} med partition ${request.partition} og offset ${request.offset}"
                    }
                    manuellEndringService.flyttPendingTilUtbetalinger(
                        key = utbetaling.key,
                        value = utbetaling.value!!,
                        audit = Audit.from(call),
                    )
                }
            }
            call.respond(HttpStatusCode.OK)
        } catch (e: Exception) {
            val msg =
                "Failed to move pending utbetaling with partition ${request.partition} and offset ${request.offset}"
            appLog.error(msg)
            secureLog.error(msg, e)
            call.respond(HttpStatusCode.BadRequest, msg)
        }
    }

    post("/tombstone-utbetaling") {
        val request = call.receive<TombstoneRequest>()
        when (manuellEndringService.tombstoneUtbetaling(request.key, Audit.from(call))) {
            true -> call.respond("Tombstoned utbetaling with kafka key ${request.key}")
            false -> call.respond(
                HttpStatusCode.UnprocessableEntity,
                "Failed to tombstone utbetaling with kafka key ${request.key}"
            )
        }
    }
}

data class KvitteringRequest(
    val key: String,
    val offset: String,
    val partition: String,
    val alvorlighetsgrad: String,
    val beskrMelding: String?,
    val kodeMelding: String?,
)

data class MessageRequest(
    val topic: String,
    val partition: String,
    val offset: String,
)

data class TombstoneRequest(val key: String)

sealed class Channel(
    val topic: Topic<String, ByteArray>,
    val table: Table,
    val revision: Int, // the topology must create these streams in the same order every time, sort by revision
) {
    data object Avstemming : Channel(Topics.avstemming, Table.avstemming, 0)
    data object Oppdrag : Channel(Topics.oppdrag, Table.oppdrag, 1)
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

data class Page(val items: List<Daos>, val total: Int)