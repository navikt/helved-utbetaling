package utsjekk.task

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.util.logging.*
import kotlinx.coroutines.CancellationException
import libs.job.Scheduler
import libs.postgres.concurrency.transaction
import libs.postgres.concurrency.withLock
import libs.utils.appLog
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.BrukersNavKontor
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import utsjekk.iverksetting.*
import utsjekk.iverksetting.utbetalingsoppdrag.Utbetalingsgenerator
import utsjekk.oppdrag.OppdragClient
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext

class TaskScheduler(
    private val oppdrag: OppdragClient,
    private val service: Iverksettinger,
    context: CoroutineContext,
) : Scheduler<TaskDao>(
    feedRPM = 120,
    errorCooldownMs = 100,
    context = context,
) {
    override suspend fun feed(): List<TaskDao> {
        withLock("task") {
            secureLog.debug("Feeding scheduler")
            return transaction {
                TaskDao.select {
                    it.status = listOf(Status.UNPROCESSED)
                    it.scheduledFor = SelectTime(Operator.LE, LocalDateTime.now())
                }
            }
        }
    }

    override suspend fun task(fed: TaskDao) {
        try {
            val json = fed.payload

            when (fed.kind) {
                Kind.Iverksetting -> updateIverksetting(oppdrag, service, objectMapper.readValue<Iverksetting>(json))
                Kind.Avstemming -> TODO("not implemented")
            }

            Tasks.update(fed.id, Status.COMPLETE, "")

        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Tasks.update(fed.id, Status.FAIL, e.message)
            secureLog.error(e)
        }
    }

    override suspend fun onError(err: Throwable) {
        secureLog.error("Ukjent feil oppstod ved uførelse av task. Se logger", err)
    }

}

private suspend fun iverksettUtbetaling(oppdrag: OppdragClient, tilkjentYtelse: TilkjentYtelse) {
    tilkjentYtelse.utbetalingsoppdrag?.let { utbetalingsoppdrag ->
        if (utbetalingsoppdrag.utbetalingsperiode.isNotEmpty()) {
            oppdrag.iverksettOppdrag(utbetalingsoppdrag)
        } else {
            appLog.warn("Iverksetter ikke noe mot oppdrag. Ingen utbetalingsperioder i utbetalingsoppdraget.")
        }
    }
}

private suspend fun updateIverksetting(
    oppdrag: OppdragClient,
    service: Iverksettinger,
    iverksetting: Iverksetting,
) {
    transaction {

        IverksettingResultater
            .hent(iverksetting)
            .copy(oppdragResultat = OppdragResultat(OppdragStatus.LAGT_PÅ_KØ))
            .update()

        val forrigeResultat = iverksetting.behandling.forrigeBehandlingId?.let {
            IverksettingResultater.hentForrige(iverksetting)
        }

        val beregnetUtbetalingsoppdrag = utbetalingsoppdrag(iverksetting, forrigeResultat)
        val tilkjentYtelse = oppdaterTilkjentYtelse(
            tilkjentYtelse = iverksetting.vedtak.tilkjentYtelse,
            beregnetUtbetalingsoppdrag = beregnetUtbetalingsoppdrag,
            forrigeResultat = forrigeResultat,
            iverksetting = iverksetting,
        )

        if (beregnetUtbetalingsoppdrag.utbetalingsoppdrag.utbetalingsperiode.isNotEmpty()) {
            iverksettUtbetaling(oppdrag, tilkjentYtelse)
        } else {
            val resultat = OppdragResultat(OppdragStatus.OK_UTEN_UTBETALING)
            IverksettingResultater.oppdater(iverksetting, resultat)
            appLog.warn("Iverksetter ikke noe mot oppdrag. Ingen perioder i utbetalingsoppdraget for iverksetting $iverksetting")
        }

        service.publiserStatusmelding(iverksetting)
    }
}

private suspend fun oppdaterTilkjentYtelse(
    tilkjentYtelse: TilkjentYtelse,
    beregnetUtbetalingsoppdrag: BeregnetUtbetalingsoppdrag,
    forrigeResultat: IverksettingResultatDao?,
    iverksetting: Iverksetting,
): TilkjentYtelse {
    val nyeAndelerMedPeriodeId =
        tilkjentYtelse.andelerTilkjentYtelse.map { andel ->
            val andelData = andel.tilAndelData()
            val andelDataMedPeriodeId =
                beregnetUtbetalingsoppdrag.andeler.find { a -> andelData.id == a.id }
                    ?: throw IllegalStateException("Fant ikke andel med id ${andelData.id}")

            andel.copy(
                periodeId = andelDataMedPeriodeId.periodeId,
                forrigePeriodeId = andelDataMedPeriodeId.forrigePeriodeId,
            )
        }
    val nyTilkjentYtelse =
        tilkjentYtelse.copy(
            andelerTilkjentYtelse = nyeAndelerMedPeriodeId,
            utbetalingsoppdrag = beregnetUtbetalingsoppdrag.utbetalingsoppdrag,
        )
    val forrigeSisteAndelPerKjede =
        forrigeResultat?.tilkjentYtelseForUtbetaling?.sisteAndelPerKjede
            ?: emptyMap()
    val nyTilkjentYtelseMedSisteAndelIKjede =
        lagTilkjentYtelseMedSisteAndelPerKjede(nyTilkjentYtelse, forrigeSisteAndelPerKjede)

    transaction {
        IverksettingResultater.oppdater(iverksetting, nyTilkjentYtelseMedSisteAndelIKjede)
    }

    return nyTilkjentYtelseMedSisteAndelIKjede
}

private fun lagTilkjentYtelseMedSisteAndelPerKjede(
    tilkjentYtelse: TilkjentYtelse,
    forrigeSisteAndelPerKjede: Map<Kjedenøkkel, AndelTilkjentYtelse>,
): TilkjentYtelse {
    val beregnetSisteAndePerKjede =
        tilkjentYtelse.andelerTilkjentYtelse
            .groupBy {
                it.stønadsdata.tilKjedenøkkel()
            }.mapValues {
                it.value.maxBy { andel -> andel.periodeId!! }
            }

    val nySisteAndelerPerKjede: Map<Kjedenøkkel, AndelTilkjentYtelse> =
        finnSisteAndelPerKjede(beregnetSisteAndePerKjede, forrigeSisteAndelPerKjede)

    return tilkjentYtelse.copy(sisteAndelPerKjede = nySisteAndelerPerKjede)
}

/**
 * Finner riktig siste andel per kjede av andeler
 * Funksjonen lager en map med kjedenøkkel som key og en liste med de to andelene fra hver map
 * Deretter finner vi hvilke av de to vi skal bruke, Regelen er
 * 1. Bruk den med største periodeId
 * 2. Hvis periodeIdene er like, bruk den med størst til-og-med-dato
 */
private fun finnSisteAndelPerKjede(
    nySisteAndePerKjede: Map<Kjedenøkkel, AndelTilkjentYtelse>,
    forrigeSisteAndelPerKjede: Map<Kjedenøkkel, AndelTilkjentYtelse>,
) = (nySisteAndePerKjede.asSequence() + forrigeSisteAndelPerKjede.asSequence())
    .groupBy({ it.key }, { it.value })
    .mapValues { entry ->
        entry.value
            .sortedWith(
                compareByDescending<AndelTilkjentYtelse> { it.periodeId }.thenByDescending { it.periode.tom },
            ).first()
    }

private fun utbetalingsoppdrag(
    iverksetting: Iverksetting,
    forrigeResultat: IverksettingResultatDao?,
): BeregnetUtbetalingsoppdrag {
    val info = Behandlingsinformasjon(
        saksbehandlerId = iverksetting.vedtak.saksbehandlerId,
        beslutterId = iverksetting.vedtak.beslutterId,
        fagsystem = iverksetting.fagsak.fagsystem,
        fagsakId = iverksetting.sakId,
        behandlingId = iverksetting.behandlingId,
        personident = iverksetting.personident,
        brukersNavKontor = iverksetting.vedtak.tilkjentYtelse.andelerTilkjentYtelse.finnBrukersNavKontor(),
        vedtaksdato = iverksetting.vedtak.vedtakstidspunkt.toLocalDate(),
        iverksettingId = iverksetting.behandling.iverksettingId,
    )

    val nyeAndeler = iverksetting.vedtak.tilkjentYtelse.lagAndelData()
    val forrigeAndeler = forrigeResultat?.tilkjentYtelseForUtbetaling.lagAndelData()
    val sisteAndelPerKjede = forrigeResultat
        ?.tilkjentYtelseForUtbetaling
        ?.sisteAndelPerKjede
        ?.mapValues { it.value.tilAndelData() }
        ?: emptyMap()

    return Utbetalingsgenerator.lagUtbetalingsoppdrag(
        behandlingsinformasjon = info,
        nyeAndeler = nyeAndeler,
        forrigeAndeler = forrigeAndeler,
        sisteAndelPerKjede = sisteAndelPerKjede,
    )
}

private fun List<AndelTilkjentYtelse>.finnBrukersNavKontor(): BrukersNavKontor? = firstNotNullOfOrNull {
    when (it.stønadsdata) {
        is StønadsdataTilleggsstønader -> it.stønadsdata.brukersNavKontor
        is StønadsdataTiltakspenger -> it.stønadsdata.brukersNavKontor
        else -> null
    }
}
