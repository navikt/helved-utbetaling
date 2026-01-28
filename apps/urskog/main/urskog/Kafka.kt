package urskog

import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.*
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.kafka.*
import libs.kafka.processor.EnrichMetadataProcessor
import libs.kafka.processor.Metadata
import libs.kafka.processor.Processor
import libs.kafka.stream.ConsumedStream
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.time.LocalDateTime

const val FS_KEY = "fagsystem"

object Topics {
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val pendingUtbetalinger = Topic("helved.pending-utbetalinger.v1", json<Utbetaling>())
    val simuleringer = Topic("helved.simuleringer.v1", jaxb<SimulerBeregningRequest>())
    val dryrunAap = Topic("helved.dryrun-aap.v1", json<Simulering>())
    val dryrunDp = Topic("helved.dryrun-dp.v1", json<Simulering>())
    val dryrunTilleggsstønader = Topic("helved.dryrun-ts.v1", json<models.v1.Simulering>())
    val dryrunTiltakspenger = Topic("helved.dryrun-tp.v1", json<models.v1.Simulering>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val avstemming = Topic("helved.avstemming.v1", xml<Avstemmingsdata>())
}

fun Topology.oppdrag(mq: OppdragMQProducer, meters: MeterRegistry) {
    consume(Topics.oppdrag)
        .branch({ it.mmel == null }) {
            this
                .processor(Processor{ EnrichMetadataProcessor() })
                .map { key, (oppdrag, meta) -> saveOppdragAndSendIfReady(mq, key, oppdrag, meta) }
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
        .branch({ it.mmel != null }) {
            this
                .processor(Processor{ EnrichMetadataProcessor() })
                .map { _, (oppdrag, _) -> statusReply(oppdrag) }
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

    consume(Topics.pendingUtbetalinger)
        .processor(Processor{ EnrichMetadataProcessor() })
        .mapKeyAndValue { key, (value, meta) -> updatePendingAndOppdrag(mq, key, value, meta)}
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
): StatusReply {
    return runBlocking(Jdbc.context + Dispatchers.IO) {
        val hashKey = DaoOppdrag.hash(oppdrag)
        transaction {
            saveIdempotent(key, hashKey, oppdrag, meta)
            val lockedOppdrag = DaoOppdrag.findWithLock(hashKey)
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
): KeyValue<String, StatusReply> { 
    return runBlocking(Jdbc.context + Dispatchers.IO) {
        val hashKey = meta.headers["hash_key"]?.toInt()
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

private suspend fun pendingIsReady(hashKey: Int, expectedUids: List<String>): Boolean {
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
    hashKey: Int,
    oppdrag: Oppdrag,
    meta: Metadata,
): DaoOppdrag {
    val new = DaoOppdrag(
        kafkaKey = key,
        sakId = oppdrag.oppdrag110.fagsystemId.trimEnd(),
        behandlingId = oppdrag.oppdrag110.oppdragsLinje150s.last().henvisning.trimEnd(),
        oppdrag = oppdrag,
        uids = meta.headers["uids"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList<String>(),
        sent = false,
        sentAt =  null,
    )
    if (new.insertIdempotent()) return new
    return DaoOppdrag.find(hashKey)!!
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
                        .map { Result.catch { into(it) } }
                        .branch({ it.isOk() }) { map { it.unwrap() }.produce(Topics.dryrunAap) }
                        .default { map { it.unwrapErr() }.produce(Topics.status) }
                }
                .branch({ (_, fagsystem) -> fagsystem == Fagsystem.DAGPENGER }) {
                    map { (sim, _) -> sim }
                        .map { Result.catch { into(it) } }
                        .branch({ it.isOk() }) { map { it.unwrap() }.produce(Topics.dryrunDp) }
                        .default { map { it.unwrapErr() }.produce(Topics.status) }
                }
                .branch({ (_, fagsystem) -> fagsystem.isTilleggsstønader() }) {
                    map { (sim, _) -> sim }
                        .map { Result.catch { intoV1(it) } }
                        .branch({ it.isOk() }) { map { it.unwrap() }.produce(Topics.dryrunTilleggsstønader) }
                        .default { map { it.unwrapErr() }.produce(Topics.status) }
                }
                .branch({ (_, fagsystem) -> fagsystem == Fagsystem.TILTAKSPENGER }) {
                    map { (sim, _) -> sim }
                        .map { Result.catch { intoV1(it) } }
                        .branch({ it.isOk() }) { map { it.unwrap() }.produce(Topics.dryrunTiltakspenger) }
                        .default { map { it.unwrapErr() }.produce(Topics.status) }
                }
        }
        .default {
            map { result -> result.unwrapErr() }.produce(Topics.status)
        }
}

private val mapper: libs.xml.XMLMapper<Oppdrag> = libs.xml.XMLMapper()

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
