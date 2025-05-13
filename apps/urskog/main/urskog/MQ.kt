package urskog

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import javax.jms.TextMessage
import libs.kafka.Streams
import libs.mq.*
import libs.utils.secureLog
import libs.xml.XMLMapper
import models.*
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import no.trygdeetaten.skjema.oppdrag.Oppdrag110

class OppdragMQProducer(private val config: Config, mq: MQ, private val meters: MeterRegistry) {
    private val kvitteringQueue = config.oppdrag.kvitteringsKø
    private val producer = DefaultMQProducer(mq, config.oppdrag.sendKø)
    private val mapper: XMLMapper<Oppdrag> = XMLMapper()

    fun send(oppdrag: Oppdrag) {
        val oppdragXml = mapper.writeValueAsString(oppdrag)
        val fk = OppdragForeignKey.from(oppdrag)

        runCatching {
            producer.produce(oppdragXml) {
                jmsReplyTo = kvitteringQueue
            }
            meters.counter("helved_oppdrag_mq", listOf(
                Tag.of("status", "Sendt"),
                Tag.of("fagsystem", fk.fagsystem.name),
            )).increment()
            mqLog.info("Sender oppdrag $fk")
        }.onFailure {
            meters.counter("helved_oppdrag_mq", listOf(
                Tag.of("status", "Feilet"),
                Tag.of("fagsystem", fk.fagsystem.name),
            )).increment()
            mqLog.error("Feilet sending av oppdrag $fk")
            secureLog.error("Feilet sending av oppdrag $fk", it)
        }.getOrThrow()
    }
}

class AvstemmingMQProducer(private val config: Config, mq: MQ) {
    private val producer = DefaultMQProducer(mq, config.oppdrag.avstemmingKø)
    private val mapper: XMLMapper<Avstemmingsdata> = XMLMapper()

    fun send(avstem: Avstemmingsdata) {
        val xml = mapper.writeValueAsString(avstem)

        runCatching {
            producer.produce(xml)
            mqLog.info("Sender grensesnittavstemming til oppdrag")
            secureLog.trace("Sender grensesnittavstemming til oppdrag $xml")
        }.onFailure {
            mqLog.error("Feil ved grensesnittavstemming")
            secureLog.error("Feil ved grensesnittavstemming", it)
        }.getOrThrow()
    }
}

class KvitteringMQConsumer(private val config: Config, mq: MQ, kafka: Streams): AutoCloseable {
    private val kvitteringProducer = kafka.createProducer(config.kafka, Topics.kvittering)
    private val mapper: XMLMapper<Oppdrag> = XMLMapper()
    private val consumer = DefaultMQConsumer(mq, config.oppdrag.kvitteringsKø, ::onMessage)

    fun onMessage(message: TextMessage) {
        val kvittering = mapper.readValue(leggTilNamespacePrefiks(message.text))
        val fk = OppdragForeignKey.from(kvittering)
        mqLog.info("Mottok kvittering $fk")
        kvitteringProducer.send(fk, kvittering)
    }


    fun start() {
        consumer.start()
    }

    override fun close() {
        consumer.close()
    }

    private fun leggTilNamespacePrefiks(xml: String): String {
        return xml
            .replace("<oppdrag xmlns=", "<ns2:oppdrag xmlns:ns2=", ignoreCase = true)
            .replace("</oppdrag>", "</ns2:oppdrag>", ignoreCase = true)
    }
}

data class OppdragForeignKey(
    val fagsystem: Fagsystem,
    val sakId: SakId,
    val behandlingId: BehandlingId? = null,
    val lastPeriodeId: PeriodeId? = null,
) {
    companion object {
        fun from(oppdrag: Oppdrag) = OppdragForeignKey(
            fagsystem = Fagsystem.fromFagområde(oppdrag.oppdrag110.kodeFagomraade),
            sakId = SakId(oppdrag.oppdrag110.fagsystemId), 
            behandlingId = oppdrag.oppdrag110.oppdragsLinje150s?.lastOrNull()?.henvisning?.trimEnd()?.let(::BehandlingId),
            lastPeriodeId = oppdrag.oppdrag110.lastPeriodeId()
        )

        fun from(utbetaling: Utbetaling) = OppdragForeignKey(
            fagsystem = Fagsystem.from(utbetaling.stønad),
            sakId = utbetaling.sakId,
            behandlingId = utbetaling.behandlingId,
            lastPeriodeId = utbetaling.lastPeriodeId,
        )
    }

    private val jackson: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    fun toJson(): String {
        return jackson.writeValueAsString(this)
    }
}

private fun Oppdrag110.lastPeriodeId(): PeriodeId? {
    val lastDelytelsesId = oppdragsLinje150s?.lastOrNull()?.delytelseId?.trimEnd()
    return try {
        lastDelytelsesId?.let(PeriodeId::decode)
    } catch (e: Exception) {
        null
    }
}

