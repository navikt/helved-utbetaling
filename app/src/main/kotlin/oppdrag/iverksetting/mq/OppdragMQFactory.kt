package oppdrag.iverksetting.mq

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import oppdrag.OppdragConfig

object OppdragMQFactory {
    fun default(config: OppdragConfig): MQConnectionFactory =
        MQConnectionFactory().apply {
            hostName = config.mq.host
            port = config.mq.port
            queueManager = config.mq.manager
            channel = config.mq.channel
            transportType = WMQConstants.WMQ_CM_CLIENT
            setBooleanProperty(JmsConstants.USER_AUTHENTICATION_MQCSP, true)
        }
}