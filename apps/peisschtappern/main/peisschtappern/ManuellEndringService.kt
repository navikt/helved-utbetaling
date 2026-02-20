package peisschtappern

import libs.kafka.JsonSerde
import libs.kafka.KafkaProducer
import com.fasterxml.jackson.module.kotlin.readValue
import libs.xml.XMLMapper
import libs.utils.auditLog
import models.DpUtbetaling
import models.TsDto
import models.Utbetaling
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.ObjectFactory
import no.trygdeetaten.skjema.oppdrag.Oppdrag

class ManuellEndringService(
    private val oppdragProducer: KafkaProducer<String, Oppdrag>,
    private val utbetalingerProducer: KafkaProducer<String, Utbetaling>,
    private val dpProducer: KafkaProducer<String, DpUtbetaling>,
    private val tsProducer: KafkaProducer<String, TsDto>
) {
    fun addKvitteringManuelt(
        oppdragXml: String,
        messageKey: String,
        alvorlighetsgrad: String,
        beskrMelding: String?,
        kodeMelding: String?,
        audit: Audit,
    ): Oppdrag {

        val xmlMapper = XMLMapper<Oppdrag>()
        val oppdrag = xmlMapper.readValue(oppdragXml)

        val mmel = createMmel(
            alvorlighetsgrad = alvorlighetsgrad,
            beskrMelding = beskrMelding,
            kodeMelding = kodeMelding
        )
        oppdrag.mmel = mmel

        val result = oppdragProducer.send(messageKey, oppdrag)
        if(result.isSuccess) {
            auditLog.info("$audit -> setter kvittering på oppdrag manuelt -> key:${messageKey} topic:${result.topic} partition:${result.partition} offset:${result.offset}")
        }

        return oppdrag
    }

    fun sendOppdragManuelt(
        key: String,
        value: String,
        audit: Audit,
    ): Boolean {
        val xmlMapper = XMLMapper<Oppdrag>()
        val oppdrag = xmlMapper.readValue(value)

        val headers = mapOf("resend" to "true")
        val result =  oppdragProducer.send(key, oppdrag, headers)
        if(result.isSuccess) {
            auditLog.info("$audit -> sender oppdrag manuelt -> key:${key} topic:${result.topic} partition:${result.partition} offset:${result.offset}")
        }
        return result.isSuccess
    }

    fun flyttPendingTilUtbetalinger(
        key: String,
        value: String,
        audit: Audit,
    ): Utbetaling {
        val utbetaling = JsonSerde.jackson.readValue<Utbetaling>(value)
        val result = utbetalingerProducer.send(key, utbetaling)
        if(result.isSuccess) {
            auditLog.info("$audit -> flytt pending til utbetalinger manuelt -> key:${key} topic:${result.topic} partition:${result.partition} offset:${result.offset}")
        }

        return utbetaling
    }

    fun tombstoneUtbetaling(key: String, audit: Audit): Boolean { 
        val result = utbetalingerProducer.tombstone(key)
        if(result.isSuccess) {
            auditLog.info("$audit -> tombstone utbetaling manuelt -> key:${key} topic:${result.topic} partition:${result.partition} offset:${result.offset}")
        }
        return result.isSuccess
    }

    fun rekjørDagpenger(
        key: String,
        value: String,
        audit: Audit,
    ): Boolean {
        val dp = JsonSerde.jackson.readValue<DpUtbetaling>(value)
        val result =  dpProducer.send(key, dp)
        if(result.isSuccess) {
            auditLog.info("$audit -> rekjør dagpenger manuelt -> key:${key} topic:${result.topic} partition:${result.partition} offset:${result.offset}")
        }
        return result.isSuccess
    }

    fun rekjørTilleggsstonader(
        key: String,
        value: String,
        audit: Audit,
    ): Boolean {
        val ts = JsonSerde.jackson.readValue<TsDto>(value)
        val result = tsProducer.send(key, ts)
        if(result.isSuccess) {
            auditLog.info("$audit -> rekjør tilleggsstønader manuelt -> key:${key} topic:${result.topic} partition:${result.partition} offset:${result.offset}")
        }
        return result.isSuccess
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
