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
import java.util.*

data class MigrationRequest(
    val meldeperiode: String? = null,
    val id: UUID? = null,
)

/**
 * @param uid er utbetalingsId fra REST-endepunktet
 * @param id er den nye IDen som skal refereres til på Kafka
 * @param meldeperiode er alternativ til id dersom man ikke får til å bruke en UUID
 */
data class MigrationBatchItem(
    val uid: UUID,
    val meldeperiode: String? = null,
    val id: UUID? = null,
)

data class MigrationBatchRequest(val items: List<MigrationBatchItem>)

class UtbetalingMigrator(private val utbetalingProducer: KafkaProducer<String, models.Utbetaling>) {

    suspend fun transfer(uid: UtbetalingId, request: MigrationRequest) {
        if(request.meldeperiode == null && request.id == null) {
            badRequest("meldeperiode eller id må være satt")
        }
        if (request.meldeperiode != null && request.id != null) {
            badRequest("kan ikke sette både id og meldeperiode")
        }
        withContext(Jdbc.context) {
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
        if (request.items.isEmpty()) {
            badRequest("items kan ikke være tom")
        }
        request.items.forEach { item ->
            if (item.meldeperiode == null && item.id == null) {
                badRequest("meldeperiode eller id må være satt for uid ${item.uid}")
            }
            if (item.meldeperiode != null && item.id != null) {
                badRequest("kan ikke sette både id og meldeperiode for uid ${item.uid}")
            }
        }
        withContext(Jdbc.context) {
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
                    val migrationReq = MigrationRequest(meldeperiode = item.meldeperiode, id = item.id)
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
        val uid = req.id
            ?.let { models.UtbetalingId(it)}
            ?: aapUId(from.sakId.id, req.meldeperiode!!, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

       return models.Utbetaling(
            dryrun = false,
            originalKey = transactionId.id.toString(),
            fagsystem = Fagsystem.AAP,
            uid = uid,
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
