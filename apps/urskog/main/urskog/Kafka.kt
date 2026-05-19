package urskog

import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import libs.utils.intoUids
import libs.kafka.*
import libs.kafka.processor.EnrichMetadataProcessor
import libs.kafka.processor.Metadata
import libs.kafka.processor.Processor
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import no.trygdeetaten.skjema.oppdrag.Oppdrag

const val FS_KEY = "fagsystem"

object Topics {
    val utbetalinger = Topic("helved.utbetalinger.v1", json<Utbetaling>())
    val saker = Topic("helved.saker.v1", jsonjsonSet<SakKey, UtbetalingId>())
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val pendingUtbetalinger = Topic("helved.pending-utbetalinger.v1", json<Utbetaling>())
    val simuleringer = Topic("helved.simuleringer.v1", jaxb<SimulerBeregningRequest>())
    val dryrunAap = Topic("helved.dryrun-aap.v1", json<Simulering>())
    val dryrunDp = Topic("helved.dryrun-dp.v1", json<Simulering>())
    val dryrunTilleggsstønader = Topic("helved.dryrun-ts.v1", json<Simulering>())
    val dryrunTiltakspenger = Topic("helved.dryrun-tp.v1", json<Simulering>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val avstemming = Topic("helved.avstemming.v1", xml<Avstemmingsdata>())
    val retryKvittering = Topic("helved.retry-kvittering.v1", xml<Oppdrag>())
}

object Tables {
    val saker = Table(Topics.saker)
}

/**
 * Hver gang helved.utbetalinger.v1 blir produsert til
 * akkumulerer vi uids (UtbetalingID) for saken og erstatter aggregatet på helved.saker.v1.
 * Dette gjør at vi kan holde på alle aktive uids for en sakid per fagsystem.
 * Slettede utbetalinger fjernes fra lista.
 * Hvis lista er tom men ikke null betyr det at det ikke er første utbetaling på sak.
 */
fun Topology.utbetalingToSak(jdbcCtx: CoroutineDatasource): KTable<SakKey, Set<UtbetalingId>> {
    val ktable = consume(Topics.utbetalinger)
        .rekey { _, utbetaling ->
            val fagsystem = if (utbetaling.fagsystem.isTilleggsstønader()) {
                Fagsystem.TILLEGGSSTØNADER
            } else {
                utbetaling.fagsystem
            }
            SakKey(utbetaling.sakId, fagsystem)
        }
        .groupByKey(Serde.json(), Serde.json(), "utbetalinger-groupby-sakkey")
        .aggregate(Tables.saker) { _, utbetaling, uids ->
            when (utbetaling.action) {
                Action.DELETE -> uids - utbetaling.uid
                else -> uids + utbetaling.uid
            }
        }

    ktable
        .toStream()
        .produce(Topics.saker)

    ktable
        .toStream()
        .forEach { sakKey, uids -> markSakerAck(sakKey, uids, jdbcCtx) }

    return ktable
}

fun Topology.oppdrag(mq: OppdragMQProducer, meters: MeterRegistry, jdbcCtx: CoroutineDatasource) {
    consume(Topics.oppdrag)
        .branch({ oppdrag -> oppdrag.mmel == null }) {
            this
                .processor(Processor{ EnrichMetadataProcessor() })
                .map { key, (oppdrag, meta) -> saveOppdragAndSendIfReady(mq, key, oppdrag, meta, jdbcCtx) }
                .filter { reply -> reply.status == Status.HOS_OPPDRAG }
                .includeHeader(FS_KEY) { reply ->
                    val ytelse = reply.detaljer?.ytelse
                    when(ytelse?.isTilleggsstønader()) {
                        true -> Fagsystem.TILLEGGSSTØNADER.name
                        false -> ytelse.name
                        null -> "ukjent"
                    }
                }
                .produce(Topics.status)
        }
        .branch({ oppdrag -> oppdrag.mmel != null }) {
            val decided = this
                .merge(consume(Topics.retryKvittering))
                .processor(Processor{ EnrichMetadataProcessor() })
                .map { _, (oppdrag, meta) -> kvitteringReadyOrRetry(oppdrag, meta, jdbcCtx) }

            decided
                .branch({ it is KvitteringDecision.Terminal }) {
                    this
                        .map { decision -> (decision as KvitteringDecision.Terminal).reply }
                        .includeHeader(FS_KEY) { reply ->
                            val ytelse = reply.detaljer?.ytelse
                            when(ytelse?.isTilleggsstønader()) {
                                true -> Fagsystem.TILLEGGSSTØNADER.name
                                false -> ytelse.name
                                null -> "ukjent"
                            }
                        }
                        .produce(Topics.status)
                }
                .default {
                    this
                        .map { decision -> (decision as KvitteringDecision.Retry).let { it.oppdrag to it.retries } }
                        .includeHeader("retries") { (_, retries) -> (retries + 1).toString() }
                        .includeHeader("maxRetries") { _ -> kvitteringDefaultMaxRetries.toString() }
                        .map { (oppdrag, _) -> oppdrag }
                        .produce(Topics.retryKvittering)
                }
        }

    consume(Topics.pendingUtbetalinger)
        .processor(Processor{ EnrichMetadataProcessor() })
        .mapKeyAndValue { key, (value, meta) -> updatePendingAndOppdrag(mq, key, value, meta, jdbcCtx)}
        .filter { reply -> reply.status == Status.HOS_OPPDRAG }
        .includeHeader(FS_KEY) { reply -> 
            val ytelse = reply.detaljer?.ytelse
            when(ytelse?.isTilleggsstønader()) {
                true -> Fagsystem.TILLEGGSSTØNADER.name
                false -> ytelse.name
                null -> "ukjent" 
            }
        }
        .produce(Topics.status)
}

private fun saveOppdragAndSendIfReady(
    mq: OppdragMQProducer,
    key: String,
    oppdrag: Oppdrag,
    meta: Metadata,
    jdbcCtx: CoroutineDatasource,
): StatusReply {
    return runBlocking(jdbcCtx + Dispatchers.IO) {
        val hashKey = DaoOppdrag.hash(oppdrag)
        transaction {
            saveIdempotent(key, hashKey, oppdrag, meta)
        }
        transaction {
            val lockedOppdrag = DaoOppdrag.findWithLockOrLegacy(hashKey, oppdrag)
                ?: return@transaction StatusReply.mottatt(oppdrag)
            val alreadySent = lockedOppdrag.sent && meta.headers["resend"] != "true"
            if (!alreadySent && pendingIsReady(hashKey, lockedOppdrag.uids)) {
                mq.send(oppdrag)
                lockedOppdrag.updateAsSent()
                StatusReply.sendt(oppdrag)
            } else {
                StatusReply.mottatt(oppdrag)
            }
        }
    }
}

private fun updatePendingAndOppdrag(
    mq: OppdragMQProducer,
    key: String,
    value: Utbetaling,
    meta: Metadata,
    jdbcCtx: CoroutineDatasource,
): KeyValue<String, StatusReply> { 
    return runBlocking(jdbcCtx + Dispatchers.IO) {
        val hashKey = meta.headers["hash_key"]
        if (hashKey == null) {
            kafkaLog.warn("pendingUtbetaling med key:$key mangler header hash_key")
            return@runBlocking KeyValue(value.originalKey, StatusReply(Status.MOTTATT))
        }

        transaction {
            DaoPendingUtbetaling(hashKey, key).insertIdempotent()
            val lockedOppdrag = DaoOppdrag.findWithLock(hashKey)
            val alreadySent = lockedOppdrag != null && lockedOppdrag.sent && meta.headers["resend"] != "true"
            if (!alreadySent && lockedOppdrag != null && pendingIsReady(hashKey, lockedOppdrag.uids)) {
                mq.send(lockedOppdrag.oppdrag)
                lockedOppdrag.updateAsSent()
                KeyValue(value.originalKey, StatusReply.sendt(lockedOppdrag.oppdrag))
            } else {
                KeyValue(value.originalKey, StatusReply(Status.MOTTATT))
            }
        }
    }
}

private suspend fun pendingIsReady(hashKey: String, expectedUids: List<String>): Boolean {
    val receivedUids = transaction {
        DaoPendingUtbetaling
            .findAll(hashKey)
            .filter { it.mottatt  }
            .map { it.uid }
            .toList()
    }
    val isReady = expectedUids.size == receivedUids.size && expectedUids.containsAll(receivedUids)
    kafkaLog.info("is ready:$isReady for hash:$hashKey. expected: $expectedUids, received: $receivedUids")
    return isReady
}

private suspend fun saveIdempotent(
    key: String,
    hashKey: String,
    oppdrag: Oppdrag,
    meta: Metadata,
): DaoOppdrag {
    val new = DaoOppdrag(
        kafkaKey = key,
        sakId = oppdrag.oppdrag110.fagsystemId.trimEnd(),
        behandlingId = oppdrag.oppdrag110.oppdragsLinje150s.lastOrNull()?.henvisning?.trimEnd() ?: "",
        oppdrag = oppdrag,
        uids = meta.headers["uids"].intoUids(),
        sent = false,
        sentAt =  null,
    )
    if (new.insertIdempotent()) return new
    return DaoOppdrag.findOrLegacy(hashKey, new.oppdrag)!!
}

fun Topology.avstemming(avstemProducer: AvstemmingMQProducer) {
    consume(Topics.avstemming).forEach { _, v ->
        avstemProducer.send(v)
    }
}

fun Topology.simulering(simuleringService: SimuleringService) {
    consume(Topics.simuleringer)
        .map { sim ->
            Result.catch {
                runBlocking {
                    val fagsystem = Fagsystem.from(sim.request.oppdrag.kodeFagomraade)
                    simuleringService.simuler(sim) to fagsystem
                }
            }
        }
        .branch({ result -> result.isOk() }) {
            map { result -> result.unwrap() }
                .branch({ (_, fagsystem) -> fagsystem == Fagsystem.AAP }) {
                    map { (sim, _) -> sim }
                        .map { Result.catch { v2.Simulering.from(it) } }
                        .branch({ it.isOk() }) { map { it.unwrap() }.produce(Topics.dryrunAap) }
                        .default { map { it.unwrapErr().copy(simulering = true) }.produce(Topics.status) }
                }
                .branch({ (_, fagsystem) -> fagsystem == Fagsystem.DAGPENGER }) {
                    map { (sim, _) -> sim }
                        .map { Result.catch { v2.Simulering.from(it) } }
                        .branch({ it.isOk() }) { map { it.unwrap() }.produce(Topics.dryrunDp) }
                        .default { map { it.unwrapErr().copy(simulering = true) }.produce(Topics.status) }
                }
                .branch({ (_, fagsystem) -> fagsystem.isTilleggsstønader() }) {
                    map { (sim, _) -> sim }
                        .map { Result.catch { intoV1(it) ?: Info.OkUtenEndring(Fagsystem.TILLEGGSSTØNADER) } }
                        .branch({ it.isOk() }) { map { it.unwrap() }.produce(Topics.dryrunTilleggsstønader) }
                        .default { map { it.unwrapErr().copy(simulering = true) }.produce(Topics.status) }
                }
                .branch({ (_, fagsystem) -> fagsystem == Fagsystem.TILTAKSPENGER }) {
                    map { (sim, _) -> sim }
                        .map { Result.catch { intoV1(it) ?: Info.OkUtenEndring(Fagsystem.TILTAKSPENGER) } }
                        .branch({ it.isOk() }) { map { it.unwrap() }.produce(Topics.dryrunTiltakspenger) }
                        .default { map { it.unwrapErr().copy(simulering = true) }.produce(Topics.status) }
                }
        }
        .default {
            map { result -> result.unwrapErr().copy(simulering = true) }.produce(Topics.status)
        }
}

private val mapper: libs.xml.XMLMapper<Oppdrag> = libs.xml.XMLMapper()

private const val kvitteringDefaultMaxRetries: Int = 1000

private sealed interface KvitteringDecision {
    data class Terminal(val reply: StatusReply) : KvitteringDecision
    data class Retry(val oppdrag: Oppdrag, val retries: Int) : KvitteringDecision
}

/**
 * Read-side completeness barrier for kvittering Status.OK emission.
 *
 * The kvittering (OS-svar) from Oppdragsystemet may arrive before the saker aggregate
 * has caught up with all pending utbetalinger for the same hashKey. Emitting Status.OK
 * before pending uids are all received (or before saker_ack=true) would race utsjekk's
 * view of the sak. Instead, we gate Status.OK on:
 *   - locked.uids ⊆ received pending uids  (pendingIsReady)
 *   - locked.sakerAck == true              (T5/T6 marks this when saker emits the full set)
 *
 * When not ready (or the oppdrag row isn't visible yet -- rare race where kvittering
 * arrives before the corresponding oppdrag insert) we route the original kvittering XML
 * to Topics.retryKvittering. T8 handles exhaustion of retries.
 */
private fun kvitteringReadyOrRetry(
    oppdrag: Oppdrag,
    meta: Metadata,
    jdbcCtx: CoroutineDatasource,
): KvitteringDecision {
    val retries = meta.headers["retries"]?.toIntOrNull() ?: 0
    val maxRetries = meta.headers["maxRetries"]?.toIntOrNull() ?: kvitteringDefaultMaxRetries
    return runBlocking(jdbcCtx + Dispatchers.IO) {
        val hashKey = DaoOppdrag.hash(oppdrag)
        transaction {
            val locked = DaoOppdrag.findWithLockOrLegacy(hashKey, oppdrag)
            if (locked == null) {
                if (retries >= maxRetries) {
                    libs.utils.appLog.error("Kvittering barrier eksaurert for hash:$hashKey etter $retries forsøk. pendingReady=n/a, sakerReady=n/a (manglende oppdrag-rad)")
                    val diag = ApiError(500, "Kvittering barrier eksaurert etter $retries/$maxRetries forsøk: manglende oppdrag-rad for hash:$hashKey")
                    return@transaction KvitteringDecision.Terminal(StatusReply.err(oppdrag, diag))
                }
                kafkaLog.info("kvittering for hash:$hashKey har ikke matchende oppdrag-rad ennå, ruter til retry-kvittering (forsøk $retries)")
                return@transaction KvitteringDecision.Retry(oppdrag, retries)
            }
            if (locked.uids.isEmpty()) {
                val alvorlighet = oppdrag.mmel?.alvorlighetsgrad ?: "ukjent"
                libs.utils.appLog.info("utsjekk-REST bypass: hash=$hashKey alvorlighet=$alvorlighet (uids tom -> Terminal uten barrier)")
                return@transaction KvitteringDecision.Terminal(statusReply(oppdrag))
            }
            val pendingReady = pendingIsReady(hashKey, locked.uids)
            val sakerReady = locked.sakerAck
            if (pendingReady && sakerReady) {
                KvitteringDecision.Terminal(statusReply(oppdrag))
            } else if (retries >= maxRetries) {
                libs.utils.appLog.error("Kvittering barrier eksaurert for hash:$hashKey etter $retries forsøk. pendingReady=$pendingReady, sakerReady=$sakerReady")
                val diag = ApiError(500, "Kvittering barrier eksaurert etter $retries/$maxRetries forsøk: pendingReady=$pendingReady, sakerReady=$sakerReady")
                KvitteringDecision.Terminal(StatusReply.err(oppdrag, diag))
            } else {
                kafkaLog.info("kvittering for hash:$hashKey ikke klar (pendingReady=$pendingReady, sakerAck=${locked.sakerAck}), ruter til retry-kvittering (forsøk $retries)")
                KvitteringDecision.Retry(oppdrag, retries)
            }
        }
    }
}

private fun statusReply(o: Oppdrag): StatusReply {
    return when (o.mmel) {
        null -> StatusReply(Status.OK) // TODO: denne kan skape feil hvis statusReply blir kalt fra et sted som ikke har kvittering
        else -> when (o.mmel.alvorlighetsgrad) {
            "00" -> StatusReply.ok(o)
            "04" -> StatusReply.ok(o, o.mmel.apiError(200))
            "08" -> StatusReply.err(o, o.mmel.apiError(400))
            "12" -> StatusReply.err(o, o.mmel.apiError(500))
            else -> StatusReply.err(o, ApiError(500, "umulig feil, skal aldri forekomme. Hvis du ser denne er alt håp ute."))
        }
    }
}

/**
 * Write-side completeness barrier:
 * For each new aggregate published to helved.saker.v1 we look up pending oppdrag rows for the same sakId
 * and mark saker_ack=true on rows whose stored uids are all present in the current aggregate.
 * This signals that the oppdrag's view of the sak matches utsjekk's view, and lets the kvittering
 * barrier (T7) emit status=OK without racing the saker aggregate.
 */
private fun markSakerAck(sakKey: SakKey, uids: Set<UtbetalingId>, jdbcCtx: CoroutineDatasource) {
    val newUidStrings = uids.map { it.id.toString() }.toSet()
    runBlocking(jdbcCtx + Dispatchers.IO) {
        val pendings = transaction {
            DaoOppdrag.findPendingSakerAck(sakKey.sakId.id)
        }
        pendings.forEach { pending ->
            val fagsystem = Fagsystem.fromFagområde(pending.oppdrag.oppdrag110.kodeFagomraade.trimEnd())
            if (fagsystem != sakKey.fagsystem) return@forEach
            if (!newUidStrings.containsAll(pending.uids)) return@forEach

            val hashKey = DaoOppdrag.hash(pending.oppdrag)
            transaction {
                DaoOppdrag.findWithLock(hashKey)?.updateSakerAck()
            }
        }
    }
}
