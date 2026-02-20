package snickerboa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import libs.kafka.KafkaConsumer
import libs.kafka.KafkaFactory
import libs.kafka.KafkaProducer
import libs.kafka.StreamsConfig
import libs.kafka.Topic
import libs.kafka.json
import models.*
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

object Topics {
    val aapIntern = Topic("helved.utbetalinger-aap.v1", json<AapUtbetaling>())
    val dpIntern = Topic("helved.utbetalinger-dp.v1", json<DpUtbetaling>())
    val tsIntern = Topic("helved.utbetalinger-ts.v1", json<TsDto>())
    val tpIntern = Topic("helved.utbetalinger-tp.v1", json<TpUtbetaling>())
    val historiskIntern = Topic("helved.utbetalinger-historisk.v1", json<HistoriskUtbetaling>())

    val status = Topic("helved.status.v1", json<StatusReply>())
    val dryrunAap = Topic("helved.dryrun-aap.v1", json<Simulering>())
    val dryrunDp = Topic("helved.dryrun-dp.v1", json<Simulering>())
    val dryrunTs = Topic("helved.dryrun-ts.v1", json<Simulering>())
    val dryrunTp = Topic("helved.dryrun-tp.v1", json<Simulering>())
}

class UtbetalingProducers(
    private val aap: KafkaProducer<String, AapUtbetaling>,
    private val dp: KafkaProducer<String, DpUtbetaling>,
    private val ts: KafkaProducer<String, TsDto>,
    private val tp: KafkaProducer<String, TpUtbetaling>,
    private val historisk: KafkaProducer<String, HistoriskUtbetaling>,
) : AutoCloseable {

    fun produceAap(uid: UUID, data: AapUtbetaling) = aap.send(uid.toString(), data)
    fun produceDp(uid: UUID, data: DpUtbetaling) = dp.send(uid.toString(), data)
    fun produceTs(uid: UUID, data: TsDto) = ts.send(uid.toString(), data)
    fun produceTp(uid: UUID, data: TpUtbetaling) = tp.send(uid.toString(), data)
    fun produceHistorisk(uid: UUID, data: HistoriskUtbetaling) = historisk.send(uid.toString(), data)

    override fun close() {
        aap.close()
        dp.close()
        ts.close()
        tp.close()
        historisk.close()
    }

    companion object {
        fun create(factory: KafkaFactory, config: StreamsConfig) = UtbetalingProducers(
            aap = factory.createProducer(config, Topics.aapIntern),
            dp = factory.createProducer(config, Topics.dpIntern),
            ts = factory.createProducer(config, Topics.tsIntern),
            tp = factory.createProducer(config, Topics.tpIntern),
            historisk = factory.createProducer(config, Topics.historiskIntern),
        )
    }
}

suspend fun statusConsumer(
    correlator: RequestReplyCorrelator,
    consumer: KafkaConsumer<String, StatusReply>,
) {
    withContext(Dispatchers.IO) {
        while (isActive) {
            for (record in consumer.poll(50.milliseconds)) {
                val uid = UUID.fromString(record.key) ?: continue
                val reply = record.value ?: continue
                correlator.completeStatus(uid, reply)
            }
            delay(1)
        }
        consumer.close()
    }
}

suspend fun dryrunConsumer(
    correlator: RequestReplyCorrelator,
    consumer: KafkaConsumer<String, Simulering>,
) {
    withContext(Dispatchers.IO) {
        while (isActive) {
            for (record in consumer.poll(50.milliseconds)) {
                val uid = UUID.fromString(record.key) ?: continue
                val simulering = record.value ?: continue
                correlator.completeSimulering(uid, simulering)
            }
            delay(1)
        }
        consumer.close()
    }
}
