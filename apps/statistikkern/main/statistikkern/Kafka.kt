package statistikkern

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import libs.kafka.KafkaConsumer
import libs.kafka.Topic
import libs.kafka.json
import libs.kafka.xml
import libs.utils.appLog
import models.StatusReply
import models.Utbetaling
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import kotlin.time.Duration.Companion.seconds

object Topics {
    val utbetalinger = Topic("helved.utbetalinger.v1", json<Utbetaling>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
}

suspend fun utbetalingConsumer(
    bigQuery: BigQueryService,
    consumer: KafkaConsumer<String, Utbetaling>,
) {
    withContext(Dispatchers.IO) {
        consumer.seekToBeginning(0, 1, 2)
        while (isActive) {
            for (record in consumer.poll(1.seconds)) {
                try {
                    val utbetaling = record.value ?: continue
                    val systemTimeMs = record.headers["x-sy"]?.toLong()

                    bigQuery.upsertUtbetaling(utbetaling, systemTimeMs)
                } catch (e: Exception) {
                    appLog.info("Feil ved prosessering av utbetaling, key=${record.key}: ${e.message}", e)
                }
            }
            delay(1)
        }
    }
}

suspend fun oppdragConsumer(
    bigQuery: BigQueryService,
    consumer: KafkaConsumer<String, Oppdrag>,
) {
    withContext(Dispatchers.IO) {
        consumer.seekToBeginning(0, 1, 2)
        while (isActive) {
            for (record in consumer.poll(1.seconds)) {
                try {
                    val oppdrag = record.value ?: continue
                    val systemTimeMs = record.headers["x-sy"]?.toLong()

                    bigQuery.upsertOppdrag(oppdrag, systemTimeMs)
                } catch (e: Exception) {
                    appLog.info("Feil ved prosessering av oppdrag, key=${record.key}: ${e.message}", e)
                }
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
            for (record in consumer.poll(1.seconds)) {
                val status = record.value ?: continue
                val systemTimeMs = record.headers["x-sy"]?.toLong()
                val fagsystem = record.headers["fagsystem"]

                bqService.upsertStatus(record.key, status, systemTimeMs, fagsystem)
            }
            delay(1)
        }
    }
}