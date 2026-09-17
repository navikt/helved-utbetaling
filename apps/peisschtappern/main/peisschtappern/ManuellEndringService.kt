package peisschtappern

import libs.kafka.KafkaProducer
import libs.kotlinx.KotlinxJson
import libs.xml.XMLMapper
import libs.utils.auditLog
import models.DpUtbetaling
import models.Fagsystem
import models.Status
import models.StatusReply
import models.TsDto
import models.Utbetaling
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.ObjectFactory
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.time.Instant

enum class ManuellEndringType {
    KVITTERING,
    RESEND,
    FLYTT_PENDING,
    ENDRING,
    TOMBSTONE,
    REKJOR,
    STATUS_OK,
}

class ManuellEndringService(
    private val oppdragProducer: KafkaProducer<String, Oppdrag>,
    private val utbetalingerProducer: KafkaProducer<String, Utbetaling>,
    private val dpProducer: KafkaProducer<String, DpUtbetaling>,
    private val tsProducer: KafkaProducer<String, TsDto>,
    private val statusProducer: KafkaProducer<String, StatusReply>,
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

        val result = oppdragProducer.send(messageKey, oppdrag, auditHeaders(audit, ManuellEndringType.KVITTERING))
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

        val headers = mapOf("resend" to "true") + auditHeaders(audit, ManuellEndringType.RESEND)
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
        val utbetaling = KotlinxJson.decodeFromString<Utbetaling>(value)
        val result = utbetalingerProducer.send(key, utbetaling, auditHeaders(audit, ManuellEndringType.FLYTT_PENDING))
        if(result.isSuccess) {
            auditLog.info("$audit -> flytt pending til utbetalinger manuelt -> key:${key} topic:${result.topic} partition:${result.partition} offset:${result.offset}")
        }

        return utbetaling
    }

    fun endreUtbetalingManuelt(
        key: String,
        value: String,
        audit: Audit,
    ): Utbetaling {
        val utbetaling = KotlinxJson.decodeFromString<Utbetaling>(value)
        utbetaling.validate()

        val headers = auditHeaders(audit, ManuellEndringType.ENDRING)
        val result = utbetalingerProducer.send(key, utbetaling, headers)
        if(result.isSuccess) {
            auditLog.info("$audit -> endret utbetaling manuelt -> key:${key} topic:${result.topic} partition:${result.partition} offset:${result.offset}")
        }
        return utbetaling
    }

    fun tombstoneUtbetaling(key: String, audit: Audit): Boolean { 
        val result = utbetalingerProducer.tombstone(key, headers = auditHeaders(audit, ManuellEndringType.TOMBSTONE))
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
        val dp = KotlinxJson.decodeFromString<DpUtbetaling>(value)
        val result =  dpProducer.send(key, dp, auditHeaders(audit, ManuellEndringType.REKJOR))
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
        val ts = KotlinxJson.decodeFromString<TsDto>(value)
        val result = tsProducer.send(key, ts, auditHeaders(audit, ManuellEndringType.REKJOR))
        if(result.isSuccess) {
            auditLog.info("$audit -> rekjør tilleggsstønader manuelt -> key:${key} topic:${result.topic} partition:${result.partition} offset:${result.offset}")
        }
        return result.isSuccess
    }

    fun sendOkStatus(
        key: String,
        fagsystem: Fagsystem,
        audit: Audit,
    ): Boolean {
        val status = StatusReply(Status.OK)
        val headers = mapOf("fagsystem" to fagsystem.name) + auditHeaders(audit, ManuellEndringType.STATUS_OK)
        val result = statusProducer.send(key, status, headers)
        if(result.isSuccess) {
            auditLog.info("$audit -> send OK status manuelt -> key:${key} fagsystem:${fagsystem.name} topic:${result.topic} partition:${result.partition} offset:${result.offset}")
        }
        return result.isSuccess
    }
}

private fun auditHeaders(audit: Audit, type: ManuellEndringType): Map<String, String> = buildMap {
    put("manuelt-endret", "true")
    put("endret-type", type.name)
    put("endret-av", audit.ident)
    put("endret-tidspunkt", Instant.now().toString())
    audit.reason?.let { put("endret-aarsak", it) }
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
