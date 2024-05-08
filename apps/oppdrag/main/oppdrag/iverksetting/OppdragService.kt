package oppdrag.iverksetting

import libs.mq.MQ
import libs.postgres.transaction
import libs.utils.appLog
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.OppdragConfig
import oppdrag.iverksetting.tilstand.OppdragId
import oppdrag.iverksetting.tilstand.OppdragLager
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import org.postgresql.util.PSQLException
import java.sql.Connection
import javax.sql.DataSource

object SQL_STATE {
    const val CONSTRAINT_VIOLATION = "23505"
}

class OppdragService(
    config: OppdragConfig,
    mq: MQ,
    private val postgres: DataSource,
) {
    private val oppdragSender = OppdragMQProducer(config, mq)

    fun opprettOppdrag(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        oppdrag: Oppdrag,
        versjon: Int,
    ) {
        try {
            appLog.debug("Lagrer oppdrag med saksnummer ${utbetalingsoppdrag.saksnummer} i databasen")

            val oppdragLager = OppdragLager.lagFraOppdrag(utbetalingsoppdrag, oppdrag)

            postgres.transaction { con ->
                OppdragLagerRepository.opprettOppdrag(oppdragLager, con, versjon)
            }
        } catch (e: PSQLException) {
            when (e.sqlState) {
                SQL_STATE.CONSTRAINT_VIOLATION -> throw OppdragAlleredeSendtException(utbetalingsoppdrag.saksnummer)
                else -> throw UkjentOppdragLagerException(utbetalingsoppdrag.saksnummer)
            }
        }

        appLog.debug("Legger oppdrag med saksnummer ${utbetalingsoppdrag.saksnummer} på kø")
        oppdragSender.sendOppdrag(oppdrag)
    }

    fun hentStatusForOppdrag(
        oppdragId: OppdragId,
        con: Connection,
    ): OppdragLager {
        return OppdragLagerRepository.hentOppdrag(oppdragId, con)
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
