package utsjekk.iverksetting

import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.kafka.KafkaProducer
import libs.utils.appLog
import libs.utils.secureLog
import models.*
import models.BehandlingId
import models.UtbetalingId
import models.Utbetalingsperiode
import utsjekk.partition
import java.util.UUID

data class MigrationRequest(
    val sakId: String,
    val behandlingId: String,
    val iverksettingId: String?,
    val meldeperiode: String?, // meldekortId
    val uidToStønad: Pair<UUID, Stønadstype>?,
) {
    init {
        require(meldeperiode == null || uidToStønad == null)
    }
}

class IverksettingMigrator(
    val iverksettingService: IverksettingService,
    val utbetalingProducer: KafkaProducer<String, Utbetaling>,
) {
    fun migrate(utbetaling: Utbetaling) {
        val key = utbetaling.uid.id.toString()
        utbetalingProducer.send(key, utbetaling, partition(key))
    }

    suspend fun mapUtbetalinger(fs: models.kontrakter.Fagsystem, req: MigrationRequest): List<Utbetaling> {
        if (fs !in listOf(models.kontrakter.Fagsystem.TILLEGGSSTØNADER, models.kontrakter.Fagsystem.TILTAKSPENGER)) {
            notImplemented("kan ikke migrere $fs enda")
        }
        return withContext(Jdbc.context) {
            transaction {
                val iverksetting = iverksettingService.hentSisteMottatte(SakId(req.sakId), fs)
                    ?: notFound("iverksetting for (sak=${req.sakId} fagsystem=$fs)")
                val sisteIverksettingResultat = try {
                    IverksettingService.hent(iverksetting)
                } catch (e: Exception) {
                    secureLog.error("iverksettingsresultat for (sak=${req.sakId} fagsystem=$fs)", e)
                    notFound("iverksettingsresultat for (sak=${req.sakId} fagsystem=$fs)")
                }
                if (sisteIverksettingResultat.oppdragResultat?.oppdragStatus !in listOf(
                        OppdragStatus.KVITTERT_OK,
                        OppdragStatus.OK_UTEN_UTBETALING
                    )
                ) {
                    locked("iverksetting for (sak=${req.sakId} fagsystem=$fs)")
                }

                val andelerByKlassekode = sisteIverksettingResultat
                    .tilkjentYtelseForUtbetaling
                    .lagAndelData()
                    .groupBy { it.stønadsdata.tilKjedenøkkel() }
                    .mapValues { andel -> andel.value.sortedBy { it.fom } }
                    .filter { (nøkkel, _) ->
                        when (nøkkel) {
                            is KjedenøkkelMeldeplikt -> nøkkel.meldekortId == req.meldeperiode!!
                            is KjedenøkkelStandard -> nøkkel.klassifiseringskode == req.uidToStønad!!.second.klassekode
                        }
                    }

                val sisteAndeler = sisteIverksettingResultat
                    .tilkjentYtelseForUtbetaling
                    ?.sisteAndelPerKjede
                    ?.mapValues { it.value.tilAndelData() }
                    ?: badRequest("Fant ikke siste andeler for (sak=${req.sakId} fagsystem=$fs)")

                if (andelerByKlassekode.isEmpty()) {
                    sisteAndeler.map {
                        appLog.info("forsøker å migrere ${it.key.klassifiseringskode}} uten andeler")
                        utbetaling(
                            req = req,
                            iverksetting = iverksetting,
                            andeler = emptyList(),
                            sisteAndel = it.value,
                            klassekode = it.key.klassifiseringskode,
                            fagsystem = Fagsystem.from(fs.kode),
                            action = Action.DELETE
                        )
                    }
                } else {
                    andelerByKlassekode.map { (kjedenøkkel, andeler) ->
                        appLog.info("forsøker å migrere $kjedenøkkel}")
                        utbetaling(
                            req = req,
                            iverksetting = iverksetting,
                            andeler = andeler,
                            sisteAndel = sisteAndeler[kjedenøkkel]
                                ?: badRequest("Fant ikke siste andeler for (sak=${req.sakId} fagsystem=$fs) kjedenøkkel=$kjedenøkkel"),
                            klassekode = kjedenøkkel.klassifiseringskode,
                            fagsystem = Fagsystem.from(fs.kode)
                        )
                    }
                }
            }
        }
    }

    private fun utbetaling(
        req: MigrationRequest,
        iverksetting: Iverksetting,
        andeler: List<AndelData>,
        sisteAndel: AndelData,
        klassekode: String,
        fagsystem: Fagsystem,
        action: Action = Action.CREATE,
    ): Utbetaling = Utbetaling(
        dryrun = false,
        originalKey = iverksetting.iverksettingId?.id ?: iverksetting.behandlingId.id,
        fagsystem = fagsystem,
        uid = req.uidToStønad
            ?.let { UtbetalingId(it.first) }
            ?: uid(iverksetting.sakId.id, requireNotNull(req.meldeperiode), Stønadstype.fraKode(klassekode), fagsystem),
        action = action,
        førsteUtbetalingPåSak = false,
        sakId = models.SakId(iverksetting.sakId.id),
        behandlingId = BehandlingId(iverksetting.behandlingId.id),
        lastPeriodeId = PeriodeId("${iverksetting.sakId.id}#${sisteAndel.periodeId}"),
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

fun uid(sakId: String, meldeperiode: String, stønad: Stønadstype, fagsystem: Fagsystem): UtbetalingId {
    return UtbetalingId(
        uuid(
            sakId = models.SakId(sakId),
            fagsystem = fagsystem,
            meldeperiode = meldeperiode,
            stønad = stønad
        )
    )
}
