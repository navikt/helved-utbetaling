package peisschtappern

import libs.kafka.JsonSerde
import libs.kafka.KafkaProducer
import com.fasterxml.jackson.module.kotlin.readValue
import libs.xml.XMLMapper
import models.DpUtbetaling
import models.Utbetaling
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.ObjectFactory
import no.trygdeetaten.skjema.oppdrag.Oppdrag

class ManuellEndringService(
    private val oppdragProducer: KafkaProducer<String, Oppdrag>,
    private val utbetalingerProducer: KafkaProducer<String, Utbetaling>,
    private val dpProducer: KafkaProducer<String, DpUtbetaling>,
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

        // TODO: riktig? Er det ikke json?
        val xmlMapper = XMLMapper<Utbetaling>()
        val oppdrag = xmlMapper.readValue(oppdragXml)

        utbetalingerProducer.send(messageKey, oppdrag)

        return oppdrag
    }

    fun tombstoneUtbetaling(key: String) = utbetalingerProducer.tombstone(key)

    fun rekjørDagpenger(
        key: String,
        value: String
    ): Boolean {
        val dp = JsonSerde.jackson.readValue<DpUtbetaling>(value)
        return dpProducer.send(key, dp)
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
