package urskog

import com.ibm.mq.jms.MQQueue
import javax.jms.TextMessage
import libs.mq.*
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.*

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

