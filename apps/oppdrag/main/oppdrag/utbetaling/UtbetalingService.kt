package oppdrag.utbetaling

import libs.mq.MQ
import libs.postgres.concurrency.transaction
import libs.postgres.Jdbc
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatusDto
import oppdrag.OppdragConfig
import oppdrag.iverksetting.domene.OppdragMapper
import oppdrag.iverksetting.OppdragMQProducer
import oppdrag.appLog
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.postgresql.util.PSQLException
import kotlinx.coroutines.withContext

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
                uid = dto.uid,
                data = oppdrag,
                sakId = dto.saksnummer,
                behandlingId = dto.utbetalingsperiode.behandlingId,
                personident = dto.utbetalingsperiode.utbetalesTil,
                klassekode = dto.utbetalingsperiode.klassekode,
                kvittering = null,
                fagsystem = dto.fagsystem.kode,
                status = OppdragStatus.LAGT_PÅ_KØ,
            )

            withContext(Jdbc.context) {
                transaction {
                    dao.insert();
                }
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
    ): OppdragStatusDto? {
        return withContext(Jdbc.context) {
            transaction {
                UtbetalingDao.findOrNull(uid)?.let { dao ->
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

class UkjentOppdragLagerException(saksnummer: String) : RuntimeException() {
    init {
        appLog.info("Ukjent feil ved opprettelse av oppdrag med saksnummer $saksnummer.")
    }
}
