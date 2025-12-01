package utsjekk.iverksetting

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.kafka.KafkaProducer
import libs.utils.appLog
import libs.utils.secureLog
import models.Action
import models.BehandlingId
import models.Navident
import models.PeriodeId
import models.Periodetype
import models.Personident
import models.Stønadstype
import models.UtbetalingId
import models.Utbetalingsperiode
import models.badRequest
import models.kontrakter.oppdrag.OppdragStatus
import models.locked
import models.notFound
import models.notImplemented
import utsjekk.*
import utsjekk.iverksetting.resultat.IverksettingResultater
import java.util.UUID

data class MigrationRequest(
    val sakId: String,
    val behandlingId: String,
    val iverksettingId: String?,
    val meldeperiode: String?, // meldekortId
    val uidToStønad: Pair<UUID, models.Stønadstype>?,
) {
    init {
        require(meldeperiode == null || uidToStønad == null) 
    }
}

class IverksettingMigrator(
    val iverksettingService: IverksettingService,
    val utbetalingProducer: KafkaProducer<String, models.Utbetaling>,
) {
    fun route(route: Route) {
        route.route("/api/iverksetting/v2/migrate") {
            post {
                val fagsystem = call.fagsystem()
                val request = call.receive<MigrationRequest>()
                if(request.meldeperiode == null && request.uidToStønad == null) badRequest("mangler en av: 'meldeperiode' eller 'uidToStønad'")
                if (request.meldeperiode != null && request.uidToStønad != null) badRequest("mutual exclusive: 'meldeperiode' and 'uidToStønad'")
                transfer(fagsystem, request)
                call.respond(HttpStatusCode.OK)
            }
        }
    }

    suspend fun transfer(fs: models.kontrakter.felles.Fagsystem, req: MigrationRequest) {
        if (fs !in listOf(models.kontrakter.felles.Fagsystem.TILLEGGSSTØNADER, models.kontrakter.felles.Fagsystem.TILTAKSPENGER)) {
            notImplemented("kan ikke migrere $fs enda")
        } 
        withContext(Jdbc.context) {
            transaction {
                val iverksetting = iverksettingService.hentSisteMottatte(SakId(req.sakId), fs)
                    ?: notFound("iverksetting for (sak=${req.sakId} fagsystem=$fs)")
                val sisteIverksettingResultat = try {
                    IverksettingResultater.hent(iverksetting)
                } catch (e: Exception) {
                    secureLog.error("iverksettingsresultat for (sak=${req.sakId} fagsystem=$fs)", e)
                    notFound("iverksettingsresultat for (sak=${req.sakId} fagsystem=$fs)")
                }
                if (sisteIverksettingResultat.oppdragResultat?.oppdragStatus !in listOf(OppdragStatus.KVITTERT_OK, OppdragStatus.OK_UTEN_UTBETALING)) {
                    locked("iverksetting for (sak=${req.sakId} fagsystem=$fs)")
                }

                val andelerByKlassekode = sisteIverksettingResultat
                    .tilkjentYtelseForUtbetaling
                    .lagAndelData()
                    .groupBy { it.stønadsdata.tilKjedenøkkel() }
                    .mapValues { andel -> andel.value.sortedBy { it.fom} }
                    .filter { (nøkkel, _) -> 
                        when (nøkkel) {
                            is KjedenøkkelMeldeplikt -> nøkkel.meldekortId == req.meldeperiode!!
                            is KjedenøkkelStandard -> nøkkel.klassifiseringskode == req.uidToStønad!!.second.klassekode
                        }
                    }

               andelerByKlassekode.forEach { klassekode, andeler ->
                    appLog.info("forsøker å migrere $klassekode}")
                    val utbet = utbetaling(req, iverksetting, andeler, klassekode.klassifiseringskode, models.Fagsystem.from(fs.kode))
                    val key = utbet.uid.id.toString()
                    utbetalingProducer.send(key, utbet, partition(key))
                }
            }
        }
    }

    private fun utbetaling(
        req: MigrationRequest,
        iverksetting: Iverksetting,
        andeler: List<AndelData>,
        klassekode: String,
        fagsystem: models.Fagsystem,
    ): models.Utbetaling = models.Utbetaling(
        dryrun = false,
        originalKey = iverksetting.iverksettingId?.id ?: iverksetting.behandlingId.id,
        fagsystem = fagsystem,
        uid = req.uidToStønad
            ?.let { UtbetalingId(it.first) }
            ?: uid(iverksetting.sakId.id, requireNotNull(req.meldeperiode), Stønadstype.fraKode(klassekode), fagsystem),
        action = Action.CREATE,
        førsteUtbetalingPåSak = false,
        sakId = models.SakId(iverksetting.sakId.id),
        behandlingId = BehandlingId(iverksetting.behandlingId.id),
        lastPeriodeId = andeler.mapNotNull { it.periodeId }.maxByOrNull { it }?.let { PeriodeId("${iverksetting.sakId.id}#$it") } ?: error("fant ingen siste periode id for $req"),
        personident = Personident(iverksetting.personident),
        vedtakstidspunkt = iverksetting.vedtak.vedtakstidspunkt,
        stønad = Stønadstype.fraKode(klassekode),
        beslutterId = Navident(iverksetting.vedtak.beslutterId),
        saksbehandlerId = Navident(iverksetting.vedtak.saksbehandlerId),
        periodetype = Periodetype.UKEDAG,
        avvent = null,
        perioder = andeler.map { Utbetalingsperiode(it.fom, it.tom, it.beløp.toUInt()) },
    )
}

fun uid(sakId: String, meldeperiode: String, stønad: Stønadstype, fagsystem: models.Fagsystem): UtbetalingId {
    return UtbetalingId(
        models.uuid(sakId = models.SakId(sakId),
            fagsystem = fagsystem,
            meldeperiode = meldeperiode,
            stønad = stønad
        )
    )
}
