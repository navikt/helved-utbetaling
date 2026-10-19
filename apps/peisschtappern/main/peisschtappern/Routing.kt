package peisschtappern

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import libs.kafka.Streams
import libs.kafka.Topic
import libs.utils.Log
import models.Fagsystem
import models.badRequest
import org.http4k.core.*
import org.http4k.routing.*
import java.time.Duration
import java.time.Instant

fun probes(kafka: Streams, meters: PrometheusMeterRegistry) = routes(
    "/actuator/metric" bind Method.GET to { Response(Status.OK).header("Content-Type", "text/plain; version=0.0.4; charset=utf-8").body(meters.scrape()) },
    "/actuator/ready" bind Method.GET to { Response(if (kafka.ready()) Status.OK else Status.LOCKED) },
    "/actuator/live" bind Method.GET to { Response(if (kafka.live()) Status.OK else Status.LOCKED) }
)

fun api(manuellEndringService: ManuellEndringService, jdbcCtx: CoroutineDatasource) = routes(
    "/api/dashboard" bind Method.GET to coroutineHandler { req ->
        val fom = requireNotNull(req.milliseconds("fom")) {
            "Mangler query-parameter \"fom\""
        }
        val tom = requireNotNull(req.milliseconds("tom")) {
            "Mangler query-parameter \"tom\""
        }

        val result = withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                DashboardService.dashboard(fom, tom)
            }
        }

        jsonResponse(result)
    },
    "/api/korriger_utbetalinger" bind Method.POST to coroutineHandler { req ->
        val request = req.receive<KorrigerUtbetalingerRequest>()
        for (utbetaling in request.utbetalinger) {
            if (utbetaling.topic.isBlank()) badRequest("topic er påkrevd")
            if (utbetaling.key.isBlank()) badRequest("key er påkrevd")
            if (utbetaling.reason.isBlank()) badRequest("reason er påkrevd")
        }

        val registeredAt = System.currentTimeMillis()

        withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                request.utbetalinger.forEach {
                    Daos.korrigerFeiletUtbetaling(
                        topic = it.topic,
                        key = it.key,
                        reason = it.reason,
                        registeredAt = registeredAt
                    )
                }
            }
        }

        Response(Status.OK)
    },
    "/api/dashboard/oppdrag_uten_status" bind Method.GET to coroutineHandler { req ->
        val fom = req.milliseconds("fom")
        val tom = req.milliseconds("tom")

        val result = withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                Daos.findOppdragWithMissingStatus(fom, tom)
            }
        }

        jsonResponse(result)
    },
    "/api/topics" bind Method.GET to {
        jsonResponse(Channel.all().sortedBy(Channel::revision).map { it.topic.name })
    },
    "/api/messages" bind Method.GET to coroutineHandler { req ->
        val channels = req.strings("topics")?.mapNotNull(Channel::findOrNull) ?: Channel.all()
        val page = req.query("page")?.toIntOrNull() ?: 1
        val pageSize = req.query("pageSize")?.toIntOrNull() ?: 100
        val key = req.query("key")
        val includeValues = req.include()
        val excludeValues = req.exclude()
        val fom = req.milliseconds("fom")
        val tom = req.milliseconds("tom")
        val fagsystem = req.fagsystem()
        val traceId = req.query("trace_id")
        val status = req.query("status")?.split(",")
        val manueltEndret = req.manueltEndret()
        val orderBy = req.orderBy()
        val direction = req.direction()

        val result = withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                Daos.pagedMessages(
                    channels = channels,
                    page = page,
                    pageSize = pageSize,
                    key = key,
                    includeValues = includeValues,
                    excludeValues = excludeValues,
                    fom = fom,
                    tom = tom,
                    fagsystem = fagsystem,
                    traceId = traceId,
                    status = status,
                    manueltEndret = manueltEndret,
                    orderBy = orderBy,
                    direction = direction
                )
            }
        }

        jsonResponse(result)
    },
    "/api/messages/{topic}" bind Method.GET to coroutineHandler { req ->
        val channel = checkNotNull(Channel.findOrNull(req.path("topic")!!)) {
            "Unknown topic ${req.path("topic")}"
        }
        val fom = req.milliseconds("fom")
        val tom = req.milliseconds("tom")

        val result = withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                Daos.messages(listOf(channel), fom, tom)
            }
        }

        jsonResponse(result)
    },
    "/api/messages/{topic}/{partition}/{offset}" bind Method.GET to coroutineHandler { req ->
        val channel = checkNotNull(Channel.findOrNull(req.path("topic")!!)) {
            "Unknown topic ${req.path("topic")}"
        }
        val partition = checkNotNull(req.path("partition")?.toIntOrNull()) {
            "Missing or invalid partition parameter"
        }
        val offset = checkNotNull(req.path("offset")?.toLongOrNull()) {
            "Missing or invalid offset parameter"
        }

        val dao = withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                Daos.findSingle(partition, offset, channel.table)
            }
        }

        if (dao != null) jsonResponse(dao) else Response(Status.NOT_FOUND)
    },
    "/api/messages/{topic}/{partition}/{offset}/{system_time}" bind Method.GET to coroutineHandler { req ->
        val channel = checkNotNull(Channel.findOrNull(req.path("topic")!!)) {
            "Unknown topic ${req.path("topic")}"
        }
        val partition = checkNotNull(req.path("partition")?.toIntOrNull()) {
            "Missing or invalid partition parameter"
        }
        val offset = checkNotNull(req.path("offset")?.toLongOrNull()) {
            "Missing or invalid offset parameter"
        }
        val systemTime = checkNotNull(req.path("system_time")?.toLongOrNull()) {
            "Missing or invalid system_time parameter"
        }

        val dao = withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                Daos.findSingle(partition, offset, channel.table, systemTime)
            }
        }

        if (dao != null) jsonResponse(dao) else Response(Status.NOT_FOUND)
    },
    "/api/saker/{sakId}/{fagsystem}" bind Method.GET to coroutineHandler { req ->
        val sakId = req.path("sakId")!!
        val fagsystemer: List<Fagsystem> = req.path("fagsystem")!!.split(",").map { kode ->
            try { Fagsystem.from(kode.trim()) } catch (_: IllegalStateException) { badRequest("ukjent fagområde: $kode") }
        }
        val hendelser: List<Daos> = withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                coroutineScope {
                    fagsystemer.flatMap { fagsystem ->
                        val (table, internalTable, dryrun) = fagsystem.tables()
                        val deferred: List<Deferred<List<Daos>>> = listOf(
                            async { Daos.findOppdrag(sakId, fagsystem.fagområde) },
                            async { Daos.findUtbetalinger(sakId, fagsystem.name) },
                            async { Daos.findPendingUtbetalinger(sakId, fagsystem.name) },
                            async { Daos.findSimuleringer(sakId, fagsystem.fagområde) },
                            async { table?.let { Daos.findUtbetalinger(sakId, it) } ?: emptyList() },
                            async { Daos.findUtbetalinger(sakId, internalTable) },
                            async { Daos.findSaker(sakId, fagsystem.name) },
                            async {
                                val keys = Daos.findKeys(sakId, table, internalTable)
                                if (keys.isNotEmpty()) {
                                    listOf(
                                        Daos.findByKeys(Channel.Status.table, keys),
                                        dryrun?.let { Daos.findByKeys(it, keys) } ?: emptyList(),
                                    ).flatten()
                                } else emptyList()
                            },
                        )

                        deferred.awaitAll().flatten()
                    }
                        .sortedByDescending { it.system_time_ms }
                }
            }
        }

        jsonResponse(hendelser)
    },
    "/api/brann" bind Method.GET to coroutineHandler { _ ->
        val timers = withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                TimerDao.findAll()
            }
        }

        jsonResponse(timers)
    },
    "/api/brann/pending-mismatch" bind Method.GET to coroutineHandler { req ->
        val tom = Instant.now().toEpochMilli()
        val since = req.query("since")?.toLongOrNull()
            ?: (tom - Duration.ofHours(1).toMillis())

        val mismatches = withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                PendingMismatchService.detectMismatches(since, tom)
            }
        }

        jsonResponse(mismatches)
    },
    "/api/brann/dobbeltutbetalinger" bind Method.GET to coroutineHandler { req ->
        val tom = Instant.now().toEpochMilli()
        val since = req.query("since")?.toLongOrNull()
            ?: (tom - Duration.ofHours(24).toMillis())

        val suspects = withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                DobbeltutbetalingService.finnUslukkede(since, tom)
            }
        }

        jsonResponse(suspects)
    },
    "/api/brann/dobbeltutbetalinger" bind Method.POST to coroutineHandler { req ->
        val kjentDobbeltutbetaling = req.kjentDobbeltutbetaling()
        withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                kjentDobbeltutbetaling.håndter()
            }
        }
        Response(Status.OK)
    },
    "/api/brann/dobbeltutbetalinger/slukk" bind Method.POST to coroutineHandler { req ->
        val brann = req.kjentDobbeltutbetaling()
        withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                brann.slukk()
            }
        }
        Response(Status.OK)
    },
    "/api/brann/{key}" bind Method.DELETE to coroutineHandler { req ->
        withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                TimerDao.delete(req.path("key")!!)
            }
        }
        Response(Status.OK)
    },
    "/api/resend" bind Method.POST to coroutineHandler { req ->
        val request = req.receive<MessageRequest>()
        if (request.reason == null) {
            badRequest("Må oppgi grunn for resending av melding")
        }
        val channel = Channel.findOrNull(request.topic) ?: badRequest("Fant ikke topic med navn ${request.topic}")

        withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                val message = Daos.findSingle(
                    request.partition.toInt(),
                    request.offset.toLong(),
                    channel.table
                ) ?: badRequest("Fant ikke melding på topic ${request.topic} med partition ${request.partition} og offset ${request.offset}")

                val value = message.value ?: badRequest("Melding mangler value")

                val success = when (channel) {
                    is Channel.Oppdrag -> manuellEndringService.sendOppdragManuelt(
                        message.key,
                        value,
                        Audit.from(req, request.reason)
                    )

                    is Channel.Dp, Channel.DpIntern -> manuellEndringService.rekjørDagpenger(
                        message.key,
                        value,
                        Audit.from(req, request.reason)
                    )

                    is Channel.Ts, Channel.TsIntern -> manuellEndringService.rekjørTilleggsstonader(
                        message.key,
                        value,
                        Audit.from(req, request.reason)
                    )

                    else -> error("Støtter ikke innsending av melding for topic ${channel.topic.name}")
                }

                if (success) {
                    Response(Status.OK).body("Sendte melding ${message.key} i topic ${message.topic_name} på nytt")
                } else {
                    Response(Status.UNPROCESSABLE_ENTITY).body("Feilet å sende melding ${message.key} i topic ${message.topic_name} på nytt")
                }
            }
        }
    },
    "/api/avstemminger" bind Method.GET to coroutineHandler { req ->
        val fom = req.query("fom")
            ?.runCatching { Instant.parse(this).toEpochMilli() }
            ?.getOrNull() ?: badRequest("Missing required parameter: fom")
        val tom = req.query("tom")
            ?.runCatching { Instant.parse(this).toEpochMilli() }
            ?.getOrNull() ?: badRequest("Missing required parameter: tom")

        val avstemminger = withContext(jdbcCtx + Dispatchers.IO) {
            transaction {
                Daos.findAvstemminger(fom, tom)
            }
        }

        jsonResponse(avstemminger)
    },
    "/manuell-kvittering" bind Method.POST to coroutineHandler { req ->
        val request = req.receive<KvitteringRequest>()

        try {
            withContext(jdbcCtx + Dispatchers.IO) {
                transaction {
                    val oppdrag = Daos.findSingle(request.partition.toInt(), request.offset.toLong(), Channel.Oppdrag.table)
                    if (oppdrag?.value == null) badRequest("Kan ikke lage kvittering for melding uten oppdrag")
                    manuellEndringService.addKvitteringManuelt(
                        oppdragXml = oppdrag.value,
                        messageKey = oppdrag.key,
                        alvorlighetsgrad = request.alvorlighetsgrad,
                        beskrMelding = request.beskrMelding,
                        kodeMelding = request.kodeMelding,
                        audit = Audit.from(req, request.reason),
                    )
                }
            }
            Response(Status.OK).body("Created kvittering for uid:${request.key} on ${Topics.oppdrag.name}")
        } catch (e: Exception) {
            val msg = "Failed to create kvittering for uid:${request.key}"
            Log.error(msg, e)
            Response(Status.BAD_REQUEST).body(msg)
        }
    },
    "/pending-til-utbetaling" bind Method.POST to coroutineHandler { req ->
        val request = req.receive<MessageRequest>()

        try {
            withContext(jdbcCtx + Dispatchers.IO) {
                transaction {
                    val utbetaling = Daos.findSingle(
                        request.partition.toInt(),
                        request.offset.toLong(),
                        Channel.PendingUtbetalinger.table
                    )
                    if (utbetaling == null) {
                        badRequest("Kan ikke flytte pending utbetaling. Fant ikke utbetaling i topic ${request.topic} med partition ${request.partition} og offset ${request.offset}")
                    }
                    manuellEndringService.flyttPendingTilUtbetalinger(
                        key = utbetaling.key,
                        value = utbetaling.value!!,
                        audit = Audit.from(req),
                    )
                }
            }
            Response(Status.OK)
        } catch (e: Exception) {
            val msg =
                "Failed to move pending utbetaling with partition ${request.partition} and offset ${request.offset}"
            Log.error(msg, e)
            Response(Status.BAD_REQUEST).body(msg)
        }
    },
    "/endre-utbetaling" bind Method.POST to coroutineHandler { req ->
        val request = req.receive<EndreUtbetalingRequest>()
        if (request.key.isBlank()) badRequest("key er påkrevd")
        if (request.value.isBlank()) badRequest("value er påkrevd")
        if (request.reason.isBlank()) badRequest("reason er påkrevd for å endre utbetaling manuelt")

        try {
            val utbetaling = manuellEndringService.endreUtbetalingManuelt(request.key, request.value, Audit.from(req, request.reason))
            jsonResponse(utbetaling)
        } catch (e: Exception) {
            val msg = "Failed to endre utbetaling manuelt med key ${request.key}. Sjekk at json er gyldig."
            Log.error(msg, e)
            Response(Status.BAD_REQUEST).body(msg)
        }
    },
    "/tombstone-utbetaling" bind Method.POST to coroutineHandler { req ->
        val request = req.receive<TombstoneRequest>()
        when (manuellEndringService.tombstoneUtbetaling(request.key, Audit.from(req, request.reason))) {
            true -> Response(Status.OK).body("Tombstoned utbetaling with kafka key ${request.key}")
            false -> Response(Status.UNPROCESSABLE_ENTITY).body("Failed to tombstone utbetaling with kafka key ${request.key}")
        }
    },
    "/ok-status" bind Method.POST to coroutineHandler { req ->
        val request = req.receive<OkStatusRequest>()
        require(request.reason.isNotBlank()) { "Må oppgi grunn for å sende OK status manuelt" }
        val fagsystem = runCatching { Fagsystem.valueOf(request.fagsystem) }
            .getOrElse { badRequest("Ukjent fagsystem ${request.fagsystem}") }
        when (manuellEndringService.sendOkStatus(request.key, fagsystem, Audit.from(req, request.reason))) {
            true -> Response(Status.OK).body("Sendte OK status på ${Topics.status.name} med key ${request.key}")
            false -> Response(Status.UNPROCESSABLE_ENTITY).body("Feilet å sende OK status på ${Topics.status.name} med key ${request.key}")
        }
    }
)

fun auditLogs(auditLogs: AuditLogReader, auditLogResource: String?, databaseAuditLogResource: String?) = routes(
    *listOfNotNull(
        auditLogResource?.let { auditLogRoute("/api/audit-logs", auditLogs, it, null) },
        databaseAuditLogResource?.let { auditLogRoute("/api/audit-logs/database", auditLogs, it, databaseAuditLogFilter()) }
    ).toTypedArray()
)

private fun auditLogRoute(path: String, auditLogs: AuditLogReader, resourceName: String, filter: String?) =
    path bind Method.GET to { req: Request ->
        val fom = req.query("fom")?.let(Instant::parse)
        val tom = req.query("tom")?.let(Instant::parse)
        val pageToken = req.query("pageToken")?.takeIf { it.isNotBlank() }
        val pageSize = req.query("pageSize")?.toIntOrNull() ?: 100
        jsonResponse(auditLogs.list(resourceName, fom, tom, pageSize, pageToken, filter))
    }

// http4k handlers are synchronous; bridge the existing suspending JDBC API here.
private fun coroutineHandler(handler: suspend (Request) -> Response): HttpHandler = { request ->
    runBlocking { handler(request) }
}

private fun Request.kjentDobbeltutbetaling(): KjentDobbeltutbetaling {
    val behandlingId = query("behandlingId") ?: badRequest("behandlingId er påkrevd")
    val klassekode = query("klassekode") ?: badRequest("klassekode er påkrevd")
    val fom = query("fom")?.let(java.time.LocalDate::parse) ?: badRequest("fom er påkrevd")
    val tom = query("tom")?.let(java.time.LocalDate::parse) ?: badRequest("tom er påkrevd")
    return KjentDobbeltutbetaling(behandlingId, klassekode, fom, tom)
}

@Serializable
data class KvitteringRequest(
    val key: String,
    val offset: String,
    val partition: String,
    val alvorlighetsgrad: String,
    val beskrMelding: String?,
    val kodeMelding: String?,
    val reason: String,
)

@Serializable
data class MessageRequest(
    val topic: String,
    val partition: String,
    val offset: String,
    val reason: String? = null,
)

@Serializable
data class KorrigerUtbetalingerRequest(
    val utbetalinger: List<FeiletUtbetaling>
) {
    @Serializable
    data class FeiletUtbetaling(
        val topic: String,
        val key: String,
        val reason: String,
    )
}

@Serializable
data class EndreUtbetalingRequest(
    val key: String,
    val value: String,
    val reason: String,
)

@Serializable
data class TombstoneRequest(val key: String, val reason: String)

@Serializable
data class OkStatusRequest(val key: String, val reason: String, val fagsystem: String)

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
    data object DpIntern : Channel(Topics.dpIntern, Table.dpIntern, 15)
    data object Dp : Channel(Topics.dp, Table.dp, 16)
    data object TsIntern : Channel(Topics.tsIntern, Table.tsIntern, 17)
    data object TpIntern : Channel(Topics.tpIntern, Table.tpIntern, 18)
    data object Ts : Channel(Topics.ts, Table.ts, 19)
    data object Aap : Channel(Topics.aap, Table.aap, 20)
    data object HistoriskIntern : Channel(Topics.historiskIntern, Table.historiskIntern, 21)
    data object Historisk : Channel(Topics.historisk, Table.historisk, 22)
    data object ValpIntern : Channel(Topics.valpIntern, Table.valpIntern, 23)
    data object Valp : Channel(Topics.valp, Table.valp, 24)
    data object DryrunValp : Channel(Topics.dryrunValp, Table.dryrun_valp, 25)
    data object DryrunHistorisk : Channel(Topics.dryrunHistorisk, Table.dryrun_historisk, 26)

    companion object {
        fun all(): List<Channel> = Channel::class.sealedSubclasses.map { it.objectInstance as Channel }
        fun findOrNull(topicName: String): Channel? = all().firstOrNull { it.topic.name == topicName }
    }
}

@Serializable
data class Page(val items: List<Daos>, val total: Int)
