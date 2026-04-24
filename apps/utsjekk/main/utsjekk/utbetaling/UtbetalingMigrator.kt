package utsjekk.utbetaling

import kotlinx.coroutines.withContext
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import libs.kafka.KafkaProducer
import models.*
import models.BehandlingId
import models.Navident
import models.PeriodeId
import models.Personident
import models.SakId
import models.StønadTypeAAP
import java.util.*

/**
 * @param id er den nye IDen som skal refereres til på Kafka
 */
data class MigrationRequest(val id: UUID)

/**
 * @param uid er utbetalingsId fra REST-endepunktet
 * @param id er den nye IDen som skal refereres til på Kafka
 */
data class MigrationBatchItem(val uid: UUID, val id: UUID)

/**
 * One shot migrering av fler utbetalingsIDer (f.eks alle for samme sak)
 * @param items er en liste med utbetalingsIDer
 */
data class MigrationBatchRequest(val items: List<MigrationBatchItem>)

class UtbetalingMigrator(
    private val utbetalingProducer: KafkaProducer<String, models.Utbetaling>,
    private val jdbcCtx: CoroutineDatasource,
) {

    suspend fun transfer(uid: UtbetalingId, request: MigrationRequest) {
        withContext(jdbcCtx) {
            transaction {
                val dao = UtbetalingDao.findOrNull(uid) ?: notFound("Utbetaling $uid")
                val lastAvvent = lastAvventOnSak(utsjekk.utbetaling.SakId(dao.data.sakId.id))
                val utbet = utbetaling(uid, request, dao.data, lastAvvent)
                val key = utbet.uid.id.toString()
                utbetalingProducer.send(key, utbet, mapOf("migrated" to request.toString()))
            }
        }
    }

    suspend fun transferSak(request: MigrationBatchRequest) {
        if (request.items.isEmpty()) badRequest("items kan ikke være tom")

        withContext(jdbcCtx) {
            transaction {
                // Look up all items and validate they belong to the same sak
                val daos = request.items.map { item ->
                    val uid = UtbetalingId(item.uid)
                    uid to (UtbetalingDao.findOrNull(uid) ?: notFound("Utbetaling ${item.uid}"))
                }
                val sakIds = daos.map { (_, dao) -> dao.data.sakId.id }.distinct()
                if (sakIds.size > 1) {
                    badRequest("alle utbetalinger må tilhøre samme sak, fant: $sakIds")
                }

                val sakId = utsjekk.utbetaling.SakId(sakIds.single())
                val lastAvvent = lastAvventOnSak(sakId)

                for ((index, item) in request.items.withIndex()) {
                    val (uid, dao) = daos[index]
                    val migrationReq = MigrationRequest(id = item.id)
                    val utbet = utbetaling(uid, migrationReq, dao.data, lastAvvent)
                    val key = utbet.uid.id.toString()
                    utbetalingProducer.send(key, utbet, mapOf("migrated" to request.toString()))
                }
            }
        }
    }

    private suspend fun lastAvventOnSak(sakId: utsjekk.utbetaling.SakId): models.Avvent? {
        val allOnSak = UtbetalingDao.find(sakId, history = true)
        return allOnSak
            .sortedByDescending { it.created_at }
            .firstNotNullOfOrNull { it.data.avvent?.let(::avvent) }
    }

    private fun utbetaling(
        transactionId: UtbetalingId,
        req: MigrationRequest,
        from: Utbetaling,
        lastAvvent: models.Avvent?,
    ): models.Utbetaling { 
       return models.Utbetaling(
            dryrun = false,
            originalKey = transactionId.id.toString(),
            fagsystem = Fagsystem.AAP,
            uid = models.UtbetalingId(req.id),
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
            avvent = lastAvvent,
            perioder = utbetalingsperioder(from.perioder),
        )
    }

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
