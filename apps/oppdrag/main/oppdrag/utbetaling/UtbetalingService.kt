package oppdrag.utbetaling

import kotlinx.coroutines.withContext
import libs.mq.MQ
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction
import libs.utils.secureLog
import models.kontrakter.oppdrag.OppdragStatus
import models.kontrakter.oppdrag.OppdragStatusDto
import oppdrag.OppdragConfig
import oppdrag.appLog
import oppdrag.iverksetting.OppdragMQProducer
import org.postgresql.util.PSQLException

object SqlState {
    const val CONSTRAINT_VIOLATION = "23505"
}

class UtbetalingService(config: OppdragConfig, mq: MQ) {
    private val oppdragSender = OppdragMQProducer(config, mq)

    suspend fun opprettOppdrag(dto: UtbetalingsoppdragDto) {
        try {
            appLog.debug("Lagrer utbetalingsoppdrag med saksnummer ${dto.saksnummer} i databasen")

            val oppdrag110 = UtbetalingsoppdragMapper.tilOppdrag110(dto)
            val oppdrag = UtbetalingsoppdragMapper.tilOppdrag(oppdrag110)
            val sistePeriode = dto.utbetalingsperioder.last()

            val dao = UtbetalingDao(
                uid = dto.uid,
                data = oppdrag,
                sakId = dto.saksnummer,
                behandlingId = sistePeriode.behandlingId,
                personident = sistePeriode.utbetalesTil,
                klassekode = sistePeriode.klassekode,
                kvittering = null,
                fagsystem = dto.fagsystem.name,
                status = OppdragStatus.LAGT_PÅ_KØ,
            )

            withContext(Jdbc.context) {
                transaction {
                    dao.insert()
                }
            }

            appLog.debug("Legger utbetalingsoppdrag med saksnummer ${dto.saksnummer} på kø")
            oppdragSender.sendOppdrag(oppdrag)
        } catch (e: PSQLException) {
            when (e.sqlState) {
                SqlState.CONSTRAINT_VIOLATION -> throw OppdragAlleredeSendtException(dto.saksnummer)
                else -> throw UkjentOppdragLagerException(e, dto.saksnummer)
            }
        }
    }

    suspend fun hentStatusForOppdrag(
        uid: UtbetalingId,
    ): OppdragStatusDto? {
        return withContext(Jdbc.context) {
            transaction {
                UtbetalingDao.findLatestOrNull(uid)?.let { dao ->
                    OppdragStatusDto(
                        status = dao.status,
                        feilmelding = dao.kvittering?.beskrMelding,
                    )
                }
            }
        }
    }
}

class OppdragAlleredeSendtException(saksnummer: String) : RuntimeException() {
    init {
        appLog.info("Oppdrag med saksnummer $saksnummer er allerede sendt.")
    }
}

class UkjentOppdragLagerException(e: PSQLException, saksnummer: String) : RuntimeException() {
    init {
        appLog.info("Ukjent feil ved opprettelse av oppdrag med saksnummer $saksnummer.")
        secureLog.error("Ukjent feil ved opprettelse av oppdrag med saksnummer $saksnummer.", e)
    }
}
