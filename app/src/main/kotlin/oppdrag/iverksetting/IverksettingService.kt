package oppdrag.iverksetting

import com.ibm.mq.jms.MQConnectionFactory
import felles.log.appLog
import no.nav.dagpenger.kontrakter.oppdrag.Utbetalingsoppdrag
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.OppdragConfig
import oppdrag.iverksetting.mq.OppdragMQProducer
import oppdrag.iverksetting.tilstand.OppdragId
import oppdrag.iverksetting.tilstand.OppdragLager
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import oppdrag.postgres.transaction
import org.postgresql.util.PSQLException
import java.sql.Connection
import javax.sql.DataSource

class OppdragService(
    private val config: OppdragConfig,
    private val postgres: DataSource,
    private val mqFactory: MQConnectionFactory,
) {
    private val oppdragSender = OppdragMQProducer(config)

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
                "23505" -> throw OppdragAlleredeSendtException(utbetalingsoppdrag.saksnummer)
                else -> throw UkjentOppdragLagerException(utbetalingsoppdrag.saksnummer)
            }
        }

        appLog.debug("Legger oppdrag med saksnummer ${utbetalingsoppdrag.saksnummer} på kø")
        mqFactory.createConnection(config.mq.username, config.mq.password).use { con ->
            oppdragSender.sendOppdrag(oppdrag, con)
        }
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
