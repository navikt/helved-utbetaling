package utsjekk.iverksetting

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.kafka.KafkaProducer
import libs.utils.secureLog
import models.PeriodeId
import models.kontrakter.oppdrag.OppdragStatus
import utsjekk.*
import utsjekk.iverksetting.resultat.IverksettingResultater

data class MigrationRequest(
    val sakId: String,
    val behandlingId: String,
    val iverksettingId: String?,
    val meldeperiode: String,
)

class IverksettingMigrator(
    val iverksettingService: IverksettingService,
    val utbetalingProducer: KafkaProducer<String, models.Utbetaling>,
) {
    fun route(route: Route) {
        route.route("/api/iverksetting/v2/migrate") {
            post {
                val fagsystem = call.fagsystem()
                val request = call.receive<MigrationRequest>()
                transfer(fagsystem, request)
                call.respond(HttpStatusCode.OK)
            }
        }
    }

    suspend fun transfer(fs: models.kontrakter.felles.Fagsystem, req: MigrationRequest) {
        if (fs != models.kontrakter.felles.Fagsystem.TILLEGGSSTØNADER) {
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

                andelerByKlassekode.forEach { klassekode, andeler ->
                    println("forsøker å migrere klassekode: ${klassekode.klassifiseringskode}")
                    val utbet = utbetaling(req, iverksetting, andeler, klassekode.klassifiseringskode)
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
    ): models.Utbetaling = models.Utbetaling(
        dryrun = false,
        originalKey = iverksetting.behandlingId.id, // TODO: er dette riktig, eller har det ikke noe å si?
        fagsystem = models.Fagsystem.AAP,
        uid = tsUId(iverksetting.sakId.id, req.meldeperiode, models.Stønadstype.fraKode(klassekode)),
        action = models.Action.CREATE, 
        førsteUtbetalingPåSak = false,
        sakId = models.SakId(iverksetting.sakId.id),
        behandlingId = models.BehandlingId(iverksetting.behandlingId.id),
        lastPeriodeId = andeler.mapNotNull { it.periodeId }.maxByOrNull { it }?.let { PeriodeId("${iverksetting.sakId.id}#$it") } ?: error("fant ingen siste periode id for $req"),
        personident = models.Personident(iverksetting.personident),
        vedtakstidspunkt = iverksetting.vedtak.vedtakstidspunkt,
        stønad = models.Stønadstype.fraKode(klassekode),
        beslutterId = models.Navident(iverksetting.vedtak.beslutterId),
        saksbehandlerId = models.Navident(iverksetting.vedtak.saksbehandlerId),
        periodetype = models.Periodetype.UKEDAG,
        avvent = null,
        perioder = andeler.map { models.Utbetalingsperiode(it.fom, it.tom, it.beløp.toUInt()) },
    )
}

fun tsUId(sakId: String, meldeperiode: String, stønad: models.Stønadstype): models.UtbetalingId {
    if (stønad !is models.StønadTypeTilleggsstønader) badRequest("kan ikke migrere tilleggstønader med stønadstype $stønad")
    return models.UtbetalingId(
        models.uuid(
            sakId = models.SakId(sakId),
            fagsystem = models.Fagsystem.TILLEGGSSTØNADER,
            meldeperiode = meldeperiode,
            stønad = stønad
        )
    )
}
