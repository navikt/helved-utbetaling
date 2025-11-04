package peisschtappern

import libs.kafka.KafkaProducer
import libs.xml.XMLMapper
import models.Utbetaling
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.ObjectFactory
import no.trygdeetaten.skjema.oppdrag.Oppdrag

class ManuellEndringService(
    private val oppdragProducer: KafkaProducer<String, Oppdrag>,
    private val utbetalingerProducer: KafkaProducer<String, Utbetaling>
) {
    fun addKvitteringManuelt(
        oppdragXml: String,
        messageKey: String,
        alvorlighetsgrad: String,
        beskrMelding: String?,
        kodeMelding: String?
    ): Oppdrag {

        val xmlMapper = XMLMapper<Oppdrag>()
        val oppdrag = xmlMapper.readValue(oppdragXml)

        val mmel = createMmel(
            alvorlighetsgrad = alvorlighetsgrad,
            beskrMelding = beskrMelding,
            kodeMelding = kodeMelding
        )
        oppdrag.mmel = mmel

        oppdragProducer.send(messageKey, oppdrag)

        return oppdrag
    }

    fun sendOppdragManuelt(
        oppdragXml: String,
        messageKey: String,
    ): Oppdrag {

        val xmlMapper = XMLMapper<Oppdrag>()
        val oppdrag = xmlMapper.readValue(oppdragXml)

        oppdragProducer.send(messageKey, oppdrag)

        return oppdrag
    }

    fun flyttPendingTilUtbetalinger(
        oppdragXml: String,
        messageKey: String,
    ): Utbetaling {

        val xmlMapper = XMLMapper<Utbetaling>()
        val oppdrag = xmlMapper.readValue(oppdragXml)

        utbetalingerProducer.send(messageKey, oppdrag)

        return oppdrag
    }


}

private fun createMmel(
    alvorlighetsgrad: String,
    beskrMelding: String?,
    kodeMelding: String?
): Mmel {
    val factory = ObjectFactory()
    return factory.createMmel().apply {
        this.alvorlighetsgrad = alvorlighetsgrad
        this.beskrMelding = beskrMelding
        this.kodeMelding = kodeMelding
    }
}
