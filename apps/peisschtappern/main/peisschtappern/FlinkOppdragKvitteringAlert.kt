package peisschtappern

import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import libs.http.HttpClientFactory
import libs.kafka.StreamsConfig
import libs.kafka.StringSerde
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema
import org.apache.flink.core.execution.CheckpointingMode
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.time.Duration
import java.util.Properties

interface Flink {
    fun start()
    fun stop()
}

class FlinkOppdragKvitteringAlert(val flinkConfig: FlinkConfig, val kafkaConfig: StreamsConfig) : Flink {

    private val http = HttpClientFactory.new(LogLevel.ALL)

    private val env = StreamExecutionEnvironment.getExecutionEnvironment()

    override fun stop() = env.close()

    override fun start() {

        env.enableCheckpointing(Duration.ofSeconds(30).toMillis(), CheckpointingMode.EXACTLY_ONCE)

        val consumerProps = Properties().apply {
            setProperty("isolation.level", "read_committed")
            setProperty("auto.offset.reset", "earliest")
        }

        val mapper: XMLMapper<Oppdrag> = XMLMapper()

        val oppdragSource = KafkaSource.builder<KafkaRecord>()
            .setBootstrapServers(kafkaConfig.brokers)
            .setGroupId("flink-oppdrag-checker")
            .setTopics(Topics.oppdrag.toString())
            .setProperties(consumerProps)
            .setStartingOffsets(OffsetsInitializer.latest())
            .setDeserializer(object : KafkaRecordDeserializationSchema<KafkaRecord> {
                override fun deserialize(
                    record: ConsumerRecord<ByteArray, ByteArray>,
                    out: Collector<KafkaRecord>
                ) {
                    val key = StringSerde.deserializer().deserialize("", record.key())
                    val value = mapper.readValue(record.value())
                    out.collect(KafkaRecord(key, value!!))
                }

                override fun getProducedType(): TypeInformation<KafkaRecord> =
                    TypeInformation.of(KafkaRecord::class.java)
            })
            .build()

        val alerts = env
            .fromSource(oppdragSource, WatermarkStrategy.noWatermarks(), "oppdrag-source")
            .keyBy { it.key }
            .process(KvitteringTimeout(Duration.ofHours(1)))
            .name("missing-kvittering-check")
            .executeAndCollect("Flink Oppdrag/Kvittering hourly check")

        alerts.use {
            runBlocking {
                while (it.hasNext()) {
                    http.post(flinkConfig.slackWebhookUrl) {
                        contentType(io.ktor.http.ContentType.Application.Json)
                        setBody(it.next())
                    }
                }
            }
        }
    }

    class KvitteringTimeout(
        private val timeout: Duration
    ) : KeyedProcessFunction<String, KafkaRecord, String>() {

        private lateinit var alertState: ValueState<AlertState>

        override fun open(openContext: OpenContext) {
            alertState = runtimeContext.getState(
                ValueStateDescriptor("alertState", TypeInformation.of(AlertState::class.java))
            )
        }

        override fun processElement(
            value: KafkaRecord,
            ctx: KeyedProcessFunction<String, KafkaRecord, String>.Context,
            out: Collector<String>
        ) {
            val previousState = alertState.value()

            val hasKvittering = value.value.mmel != null
            if (hasKvittering) {
                previousState?.let {
                    ctx.timerService().deleteProcessingTimeTimer(it.timestamp)
                    alertState.clear()
                }
                return
            }

            previousState?.let {
                ctx.timerService().deleteProcessingTimeTimer(it.timestamp)
            }

            val currentTimestamp = ctx.timerService().currentProcessingTime()
            val timer = currentTimestamp + timeout.toMillis()
            val sakId = value.value.oppdrag110.fagsystemId.trim()
            val fagsystem = value.value.oppdrag110.kodeFagomraade.trim()
            ctx.timerService().registerProcessingTimeTimer(timer)
            alertState.update(AlertState(timer, sakId, fagsystem))
        }

        override fun onTimer(
            timestamp: Long,
            ctx: KeyedProcessFunction<String, KafkaRecord, String>.OnTimerContext,
            out: Collector<String>
        ) {
            val alertStateValue = alertState.value() ?: return
            val json = """                {
                  "channel": "team-hel-ved-alerts",
                  "blocks": [
                    {
                      "type": "header",
                      "text": { "type": "plain_text", "text": "Flink alert :alert: (${System.getenv("NAIS_CLUSTER_NAME")})", "emoji": true }
                    },
                    {
                      "type": "section",
                      "text": { "type": "mrkdwn", "text": "Mangler kvittering for ${ctx.currentKey}" },
                      "accessory": {
                        "type": "button",
                        "text": { "type": "plain_text", "text": "Peisen" },
                        "url": "${System.getenv("PEISEN_HOST")}/sak?sakId=${alertStateValue.sakId}&fagsystem=${alertStateValue.fagsystem}",
                        "action_id": "button-action"
                      }
                    }
                  ]
                }"""
            alertState.clear()
            out.collect(json)
        }
    }
}

data class AlertState(
    var timestamp: Long,
    var sakId: String,
    var fagsystem: String
) // Spiser Kryo dette ? Kanskje pga TypeInformation

// TODO: Flink vil ha POJO, vi vil ha immutable data class med val. Prøver med POJO
data class KafkaRecord(var key: String, var value: Oppdrag)