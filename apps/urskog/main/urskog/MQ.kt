package urskog

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.kafka.Streams
import libs.mq.DefaultMQConsumer
import libs.mq.DefaultMQProducer
import libs.mq.MQ
import libs.mq.mqLog
import libs.utils.appLog
import libs.utils.secureLog
import libs.xml.XMLMapper
import models.BehandlingId
import models.Fagsystem
import models.PeriodeId
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import javax.jms.TextMessage

class OppdragMQProducer(config: Config, mq: MQ, private val meters: MeterRegistry) {
    private val kvitteringQueue = config.oppdrag.kvitteringsKø
    private val producer = DefaultMQProducer(mq, config.oppdrag.sendKø)
    private val mapper: XMLMapper<Oppdrag> = XMLMapper()

    fun send(oppdrag: Oppdrag): Oppdrag {
        val oppdragXml = mapper.writeValueAsString(oppdrag)
        val hash = oppdragXml.hashCode()
        val sid = oppdrag.oppdrag110.fagsystemId 
        val bid = oppdrag.oppdrag110.oppdragsLinje150s?.lastOrNull()?.henvisning?.trimEnd()?.let(::BehandlingId)
        val lastDelytelsesId = oppdrag.lastDelytelseId() 
        val pid = lastDelytelsesId?.let{ PeriodeId.decode(it) }

        runCatching {
            producer.produce(oppdragXml) {
                jmsReplyTo = kvitteringQueue
            }
            meters.counter("helved_oppdrag_mq", listOf(
                Tag.of("status", "Sendt"),
                Tag.of("fagsystem", oppdrag.fagsystem()),
            )).increment()
            mqLog.info("Sender oppdrag $hash")
            return oppdrag
        }.onFailure {
            meters.counter("helved_oppdrag_mq", listOf(
                Tag.of("status", "Feilet"),
                Tag.of("fagsystem", oppdrag.fagsystem()),
            )).increment()
            mqLog.error("Feilet sending av oppdrag hash: $hash, sak: $sid, behandling: $bid, last delytelse/periodeid: $lastDelytelsesId/$pid")
            secureLog.error("Feilet sending av oppdrag $hash", it)
        }.getOrThrow()
    }
}

class AvstemmingMQProducer(config: Config, mq: MQ) {
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

class KvitteringMQConsumer(config: Config, mq: MQ, kafka: Streams): AutoCloseable {
    private val oppdragProducer = kafka.createProducer(config.kafka, Topics.oppdrag)
    private val mapper: XMLMapper<Oppdrag> = XMLMapper()
    private val consumer = DefaultMQConsumer(mq, config.oppdrag.kvitteringsKø, ::onMessage)

    fun onMessage(message: TextMessage) {
        val kvittering = mapper.readValue(leggTilNamespacePrefiks(message.text))
        val stripped = mapper.copy(kvittering).apply { mmel = null }
        val hashKey = DaoOppdrag.hash(stripped)
        secureLog.info("MQ hashing: $kvittering -> $hashKey")
        val dao = runBlocking {
            withContext(Jdbc.context + Dispatchers.IO) {
                transaction {
                    DaoOppdrag.find(hashKey) ?: error("fant ikke noe sted å lagre unna kvittering for hashKey: $hashKey")
                }
            }
        }
        mqLog.info("Mottok kvittering ${dao.kafkaKey}")
        val headers = mapOf("uids" to dao.uids.joinToString(","))
        oppdragProducer.send(dao.kafkaKey, kvittering, headers)
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

private fun Oppdrag.lastDelytelseId(): String? {
    return oppdrag110.oppdragsLinje150s?.lastOrNull()?.delytelseId?.trimEnd()
}

private fun Oppdrag.fagsystem(): String {
    return Fagsystem.fromFagområde(oppdrag110.kodeFagomraade).name
}

private fun Oppdrag.behandlingId(): BehandlingId? {
    return oppdrag110.oppdragsLinje150s?.lastOrNull()?.henvisning?.trimEnd()?.let(::BehandlingId)
}

