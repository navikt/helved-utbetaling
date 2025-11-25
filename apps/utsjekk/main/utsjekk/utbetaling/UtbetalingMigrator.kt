package utsjekk.utbetaling

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.kafka.KafkaProducer
import models.Action
import models.BehandlingId
import models.Fagsystem
import models.Navident
import models.PeriodeId
import models.Periodetype
import models.Personident
import models.SakId
import models.StønadTypeAAP
import models.aapUId
import models.badRequest
import models.notFound
import java.util.*
import java.time.LocalDate
import utsjekk.*

data class MigrationRequest(
    val meldeperiode: String,
    val fom: LocalDate,
    val tom: LocalDate,
)

class UtbetalingMigrator(private val utbetalingProducer: KafkaProducer<String, models.Utbetaling>) {

    fun route(route: Route) {
        route.route("/utbetalinger/{uid}/migrate") {
            post {
                val uid = call.parameters["uid"]
                    ?.let(::uuid)
                    ?.let(::UtbetalingId)
                    ?: badRequest("Mangler path parameter 'uid'")

                val request = call.receive<MigrationRequest>()
                transfer(uid, request)
                call.respond(HttpStatusCode.OK)
            }
        }
    }

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
        perioder = utbetalingsperioder(from.perioder, req.fom, req.tom),
    )

    private fun utbetalingsperioder(
        perioder: List<Utbetalingsperiode>,
        fom: LocalDate,
        tom: LocalDate,
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

