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
import libs.kafka.bytes
import libs.kafka.json
import models.*
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

object Topics {
    val aapIntern = Topic("helved.utbetalinger-aap.v1", bytes())
    val dpIntern = Topic("helved.utbetalinger-dp.v1", bytes())
    val tsIntern = Topic("helved.utbetalinger-ts.v1", bytes())
    val tpIntern = Topic("helved.utbetalinger-tp.v1", bytes())
    val historiskIntern = Topic("helved.utbetalinger-historisk.v1", bytes())

    val status = Topic("helved.status.v1", json<StatusReply>())
    val dryrunAap = Topic("helved.dryrun-aap.v1", json<Simulering>())
    val dryrunDp = Topic("helved.dryrun-dp.v1", json<Simulering>())
    val dryrunTs = Topic("helved.dryrun-ts.v1", json<Simulering>())
    val dryrunTp = Topic("helved.dryrun-tp.v1", json<Simulering>())
}

class UtbetalingProducers(
    private val aap: KafkaProducer<String, ByteArray>,
    private val dp: KafkaProducer<String, ByteArray>,
    private val ts: KafkaProducer<String, ByteArray>,
    private val tp: KafkaProducer<String, ByteArray>,
    private val historisk: KafkaProducer<String, ByteArray>,
) : AutoCloseable {

    fun produceAap(uid: UUID, data: ByteArray) = aap.send(uid.toString(), data)
    fun produceDp(uid: UUID, data: ByteArray) = dp.send(uid.toString(), data)
    fun produceTs(uid: UUID, data: ByteArray) = ts.send(uid.toString(), data)
    fun produceTp(uid: UUID, data: ByteArray) = tp.send(uid.toString(), data)
    fun produceHistorisk(uid: UUID, data: ByteArray) = historisk.send(uid.toString(), data)

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
        consumer.seekToEnd(0,1,2)
        while (isActive) {
            for (record in consumer.poll(50.milliseconds)) {
                val uid = UUID.fromString(record.key) ?: continue
                val reply = record.value ?: continue
                correlator.completeStatus(uid, reply)
            }
            delay(1)
        }
        consumer.unsubscribe()
        consumer.close()
    }
}

suspend fun dryrunConsumer(
    correlator: RequestReplyCorrelator,
    consumer: KafkaConsumer<String, Simulering>,
) {
    withContext(Dispatchers.IO) {
        consumer.seekToEnd(0,1,2)
        while (isActive) {
            for (record in consumer.poll(50.milliseconds)) {
                val uid = UUID.fromString(record.key) ?: continue
                val simulering = record.value ?: continue
                correlator.completeSimulering(uid, simulering)
            }
            delay(1)
        }
        consumer.unsubscribe()
        consumer.close()
    }
}
