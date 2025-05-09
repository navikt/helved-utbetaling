package oppdrag.iverksetting

import libs.mq.MQ
import libs.postgres.concurrency.transaction
import models.kontrakter.oppdrag.Utbetalingsoppdrag
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.OppdragConfig
import oppdrag.appLog
import oppdrag.iverksetting.tilstand.OppdragId
import oppdrag.iverksetting.tilstand.OppdragLager
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import org.postgresql.util.PSQLException

object SQL_STATE {
    const val CONSTRAINT_VIOLATION = "23505"
}

class OppdragService(config: OppdragConfig, mq: MQ) {
    private val oppdragSender = OppdragMQProducer(config, mq)

    suspend fun opprettOppdrag(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        oppdrag: Oppdrag,
        versjon: Int,
    ) {
        try {
            appLog.debug("Lagrer oppdrag med saksnummer ${utbetalingsoppdrag.saksnummer} i databasen")

            val oppdragLager = OppdragLager.lagFraOppdrag(utbetalingsoppdrag, oppdrag)

            transaction {
                OppdragLagerRepository.opprettOppdrag(oppdragLager, versjon)
            }

            appLog.debug("Legger oppdrag med saksnummer ${utbetalingsoppdrag.saksnummer} på kø")
            oppdragSender.sendOppdrag(oppdrag)
        } catch (e: PSQLException) {
            when (e.sqlState) {
                SQL_STATE.CONSTRAINT_VIOLATION -> throw OppdragAlleredeSendtException(utbetalingsoppdrag.saksnummer)
                else -> throw UkjentOppdragLagerException(utbetalingsoppdrag.saksnummer)
            }
        }
    }

    suspend fun hentStatusForOppdrag(
        oppdragId: OppdragId,
    ): OppdragLager {
        return OppdragLagerRepository.hentOppdrag(oppdragId)
    }
}

class OppdragAlleredeSendtException(saksnummer: String) : RuntimeException() {
    init {
        appLog.info("Oppdrag med saksnummer $saksnummer er allerede sendt.")
    }
}

class UkjentOppdragLagerException(saksnummer: String) : RuntimeException() {
    init {
        appLog.info("Ukjent feil ved opprettelse av oppdrag med saksnummer $saksnummer.")
    }
}
