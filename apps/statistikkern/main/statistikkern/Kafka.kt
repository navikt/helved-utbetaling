package statistikkern

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import libs.kafka.KafkaConsumer
import libs.kafka.Topic
import libs.kafka.json
import models.StatusReply
import models.Utbetaling
import kotlin.time.Duration.Companion.milliseconds

object Topics {
    val utbetalinger = Topic("helved.utbetalinger.v1", json<Utbetaling>())
    val status = Topic("helved.status.v1", json<StatusReply>())
}

suspend fun utbetalingConsumer(
    bigQuery: BigQueryService,
    consumer: KafkaConsumer<String, Utbetaling>,
) {
    withContext(Dispatchers.IO) {
        //consumer.seekToEnd(0,1,2)
        consumer.seekToBeginning(0,1,2)
        while (isActive) {
            for (record in consumer.poll(50.milliseconds)) {
                val uid = record.key
                val utbetaling = record.value ?: continue
                bigQuery.upsertUtbetaling(uid, utbetaling)
            }
            delay(1)
        }
    }
}

suspend fun statusConsumer(
    bqService: BigQueryService,
    consumer: KafkaConsumer<String, StatusReply>,
) {
    withContext(Dispatchers.IO) {
        // consumer.seekToEnd(0,1,2)
        consumer.seekToBeginning(0,1,2)
        while (isActive) {
            for (record in consumer.poll(50.milliseconds)) {
                val uid = record.key
                val status = record.value ?: continue
                bqService.upsertStatus(uid, status)
            }
            delay(1)
        }
    }
}