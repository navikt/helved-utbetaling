package oppdrag.fakes

import com.ibm.mq.jms.MQQueue
import libs.mq.MQ
import libs.mq.MQConsumer
import libs.mq.MQProducer
import libs.mq.MQFake
import libs.mq.JMSContextFake
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.Config
import oppdrag.iverksetting.domene.Kvitteringstatus
import oppdrag.testLog
import javax.jms.TextMessage

fun oppdragURFake(): MQ {
    val mapper = XMLMapper<Oppdrag>()
    lateinit var mq: MQFake 
    val ctx by lazy {
        JMSContextFake {
            val oppdrag = mapper.readValue(it.text).apply {
                mmel = Mmel().apply {
                    alvorlighetsgrad = "00" // 00/04/08/12
                }
            }
            mq.textMessage(mapper.writeValueAsString(oppdrag))
        }
    }
    mq = MQFake(ctx) 
    return mq
}

fun MQ.textMessage(xml: String): TextMessage {
    return transaction {
        it.createTextMessage(xml)
    }
}

    // TODO: hva gjør vi med fler queues i MQFake?
    // val avstemmingKø = AvstemmingKøListener().apply { start() }
    // inner class AvstemmingKøListener : MQConsumer(mq, MQQueue(config.avstemming.utKø)) {
    //     private val received: MutableList<TextMessage> = mutableListOf()
    //
    //     fun getReceived() = received.toList()
    //     fun clearReceived() = received.clear()
    //
    //     override fun onMessage(message: TextMessage) {
    //         testLog.info("Avstemming mottatt i oppdrag-fake ${message.jmsMessageID}")
    //         received.add(message)
    //     }
    // }

