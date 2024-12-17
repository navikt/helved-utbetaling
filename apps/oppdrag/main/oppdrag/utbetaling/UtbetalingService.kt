package oppdrag.utbetaling

import libs.mq.MQ
import libs.postgres.concurrency.transaction
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.OppdragConfig
import oppdrag.iverksetting.domene.OppdragMapper
import oppdrag.iverksetting.OppdragMQProducer
import oppdrag.appLog
import org.postgresql.util.PSQLException

object SQL_STATE {
    const val CONSTRAINT_VIOLATION = "23505"
}

class UtbetalingService(config: OppdragConfig, mq: MQ) {
    private val oppdragSender = OppdragMQProducer(config, mq)

    suspend fun opprettOppdrag(dto: UtbetalingsoppdragDto) {
        try {
            appLog.debug("Lagrer utbetalingsoppdrag med saksnummer ${dto.saksnummer} i databasen")

            val oppdrag110 = OppdragMapper.tilOppdrag110(dto.into())
            val oppdrag = OppdragMapper.tilOppdrag(oppdrag110)

            val dao = UtbetalingDao(
                data = oppdrag,
                sakId = dto.saksnummer,
                behandlingId = dto.utbetalingsperiode.behandlingId,
                personident = dto.utbetalingsperiode.utbetalesTil,
                klassekode = dto.utbetalingsperiode.klassekode,
            )

            transaction {
                dao.insert(dto.uid);
            }

            appLog.debug("Legger utbetalingsoppdrag med saksnummer ${dto.saksnummer} på kø")
            oppdragSender.sendOppdrag(oppdrag)
        } catch (e: PSQLException) {
            when (e.sqlState) {
                SQL_STATE.CONSTRAINT_VIOLATION -> throw OppdragAlleredeSendtException(dto.saksnummer)
                else -> throw UkjentOppdragLagerException(dto.saksnummer)
            }
        }
    }

    suspend fun hentStatusForOppdrag(
        uid: UtbetalingId,
    ): UtbetalingDao {
        return UtbetalingDao.find(uid)
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
