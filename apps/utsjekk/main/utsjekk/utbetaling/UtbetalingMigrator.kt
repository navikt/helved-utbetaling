package utsjekk.utbetaling

import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.kafka.KafkaProducer
import models.*
import models.BehandlingId
import models.Navident
import models.PeriodeId
import models.Personident
import models.SakId
import models.StønadTypeAAP
import utsjekk.partition
import java.time.LocalDate
import java.util.*

data class MigrationRequest(
    val meldeperiode: String,
    val fom: LocalDate,
    val tom: LocalDate,
)

class UtbetalingMigrator(private val utbetalingProducer: KafkaProducer<String, models.Utbetaling>) {

    suspend fun transfer(uid: UtbetalingId, request: MigrationRequest) {
        withContext(Jdbc.context) {
            transaction { 
                val dao = UtbetalingDao.findOrNull(uid) ?: notFound("Utbetaling $uid")
                val utbet = utbetaling(uid, request, dao.data)
                val key = utbet.uid.id.toString()
                utbetalingProducer.send(key, utbet, partition(key))
            }
        }
    }

    private fun utbetaling(
        transactionId: UtbetalingId,
        req: MigrationRequest,
        from: Utbetaling,
    ): models.Utbetaling = models.Utbetaling(
        dryrun = false,
        originalKey = transactionId.id.toString(),
        fagsystem = Fagsystem.AAP,
        uid = aapUId(from.sakId.id, req.meldeperiode, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING),
        action = Action.CREATE,
        førsteUtbetalingPåSak = from.erFørsteUtbetaling ?: false,
        sakId = SakId(from.sakId.id),
        behandlingId = BehandlingId(from.behandlingId.id),
        lastPeriodeId = PeriodeId.decode(from.lastPeriodeId.toString()),
        personident = Personident(from.personident.ident),
        vedtakstidspunkt = from.vedtakstidspunkt,
        stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
        beslutterId = Navident(from.beslutterId.ident),
        saksbehandlerId = Navident(from.saksbehandlerId.ident),
        periodetype = Periodetype.UKEDAG,
        avvent = from.avvent?.let(::avvent),
        perioder = utbetalingsperioder(from.perioder),
    )

    private fun utbetalingsperioder(
        perioder: List<Utbetalingsperiode>
    ): List<models.Utbetalingsperiode> = perioder.map { 
        models.Utbetalingsperiode(
            fom = it.fom,
            tom = it.tom,
            beløp = it.beløp,
            betalendeEnhet = it.betalendeEnhet?.let { models.NavEnhet(it.enhet) },
            vedtakssats = it.fastsattDagsats ?: it.beløp,
        )
    }

    private fun avvent(from: Avvent) = 
        models.Avvent(
            fom = from.fom,
            tom = from.tom,
            overføres = from.overføres,
            årsak = from.årsak?.let { årsak -> models.Årsak.valueOf(årsak.name) },
            feilregistrering = from.feilregistrering,
        )
}

private fun uuid(str: String): UUID {
    return try {
        UUID.fromString(str)
    } catch (e: Exception) {
        badRequest("Path param 'uid' må være en UUID")
    }
}

