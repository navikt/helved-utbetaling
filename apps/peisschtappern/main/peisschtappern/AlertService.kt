package peisschtappern

import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.time.LocalDateTime
import libs.jdbc.concurrency.transaction
import libs.xml.XMLMapper

object AlertService {
    private val oppdragMapper = XMLMapper<Oppdrag>()

    suspend fun missingKvitteringHandler(key: String, value: ByteArray?) {
        val oppdrag = oppdragMapper.readValue(value)
        when {
            oppdrag == null -> stopTimer(key)
            oppdrag.mmel != null -> stopTimer(key)
            oppdrag.mmel == null -> addTimer(key, oppdrag)
        }
    }

    private suspend fun addTimer(key: String, oppdrag: Oppdrag) {
        val timeout = LocalDateTime.now().plusHours(1)
        val timer = TimerDao(
            key, timeout, oppdrag.oppdrag110.fagsystemId.trim(), oppdrag.oppdrag110.kodeFagomraade
                .trim()
        )

        transaction {
            timer.insert()
        }
    } // TODO("dersom key er utbetalingsid kan det komme flere oppdrag på samme key")
}

private suspend fun stopTimer(key: String) {
    TimerDao.delete(key)
}

