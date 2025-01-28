package utsjekk.utbetaling.simulering

import kotlinx.coroutines.withContext
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import utsjekk.clients.SimuleringClient
import utsjekk.simulering.SimuleringDetaljer
import utsjekk.simulering.api
import utsjekk.simulering.from
import utsjekk.simulering.oppsummering.OppsummeringGenerator
import utsjekk.notFound
import utsjekk.utbetaling.FagsystemDto
import utsjekk.utbetaling.Opphør
import utsjekk.utbetaling.PeriodeId
import utsjekk.utbetaling.Utbetaling
import utsjekk.utbetaling.UtbetalingDao
import utsjekk.utbetaling.UtbetalingId
import utsjekk.utbetaling.UtbetalingsoppdragDto
import utsjekk.utbetaling.UtbetalingsperiodeDto
import utsjekk.utbetaling.Utbetalingsperioder
import utsjekk.utbetaling.betalendeEnhet
import utsjekk.utbetaling.klassekode
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class SimuleringService(private val client: SimuleringClient) {
    suspend fun simuler(uid: UtbetalingId, utbetaling: Utbetaling, oboToken: String): api.SimuleringRespons {
        val dao = withContext(Jdbc.context) {
            transaction {
                UtbetalingDao.findOrNull(uid)
            }
        }

        val oppdrag = if (dao != null) {
            utbetalingsoppdrag(uid, dao.data, utbetaling)
        } else {
            utbetalingsoppdrag(uid, utbetaling)
        }

        val simulering = client.simuler(oppdrag, oboToken)
        val detaljer = SimuleringDetaljer.from(simulering, Fagsystem.valueOf(oppdrag.fagsystem.name))
        return OppsummeringGenerator.lagOppsummering(detaljer)
    }

    suspend fun simulerDelete(
        uid: UtbetalingId,
        new: Utbetaling,
        oboToken: String,
    ): api.SimuleringRespons {
        val existing = withContext(Jdbc.context) {
            transaction {
                UtbetalingDao.findOrNull(uid)?.data ?: notFound("utbetaling $uid")
            }
        }
        existing.validateLockedFields(new)

        val oppdrag = UtbetalingsoppdragDto(
            uid = uid,
            erFørsteUtbetalingPåSak = false,
            fagsystem = FagsystemDto.from(new.stønad),
            saksnummer = new.sakId.id,
            aktør = new.personident.ident,
            saksbehandlerId = new.saksbehandlerId.ident,
            beslutterId = new.beslutterId.ident,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = new.perioder.betalendeEnhet()?.enhet,
            utbetalingsperioder = listOf(new.perioder.maxBy { it.fom }.let { sistePeriode ->
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = true,
                    opphør = Opphør(new.perioder.minBy { it.fom }.fom),
                    id = existing.lastPeriodeId.toString(),
                    vedtaksdato = new.vedtakstidspunkt.toLocalDate(),
                    klassekode = klassekode(new.stønad),
                    fom = sistePeriode.fom,
                    tom = sistePeriode.tom,
                    sats = sistePeriode.beløp,
                    satstype = sistePeriode.satstype,
                    utbetalesTil = new.personident.ident,
                    behandlingId = new.behandlingId.id,
                )
            })
        )
        val simulering = client.simuler(oppdrag, oboToken)
        val detaljer = SimuleringDetaljer.from(simulering, Fagsystem.valueOf(oppdrag.fagsystem.name))
        return OppsummeringGenerator.lagOppsummering(detaljer)
    }

    /** Lager utbetalingsoppdrag for endringer */
    private fun utbetalingsoppdrag(uid: UtbetalingId, existing: Utbetaling, new: Utbetaling): UtbetalingsoppdragDto {
        existing.validateLockedFields(new)
        existing.validateMinimumChanges(new)

        return UtbetalingsoppdragDto(
            uid = uid,
            erFørsteUtbetalingPåSak = false,
            fagsystem = FagsystemDto.from(new.stønad),
            saksnummer = new.sakId.id,
            aktør = new.personident.ident,
            saksbehandlerId = new.saksbehandlerId.ident,
            beslutterId = new.beslutterId.ident,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = new.perioder.betalendeEnhet()?.enhet,
            utbetalingsperioder = Utbetalingsperioder.utled(existing, new)
        )
    }

    /** Lager utbetalingsoppdrag for nye utbetalinger */
    private suspend fun utbetalingsoppdrag(uid: UtbetalingId, utbetaling: Utbetaling): UtbetalingsoppdragDto {
        val erFørsteUtbetalingPåSak = withContext(Jdbc.context) {
            transaction {
                UtbetalingDao.find(utbetaling.sakId, history = true)
                    .map { it.stønad.asFagsystemStr() }
                    .none { it == utbetaling.stønad.asFagsystemStr() }
            }
        }

        var forrigeId: PeriodeId? = null
        return UtbetalingsoppdragDto(
            uid = uid,
            erFørsteUtbetalingPåSak = erFørsteUtbetalingPåSak,
            fagsystem = FagsystemDto.from(utbetaling.stønad),
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = utbetaling.saksbehandlerId.ident,
            beslutterId = utbetaling.beslutterId.ident,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = utbetaling.perioder.betalendeEnhet()?.enhet,
            utbetalingsperioder = utbetaling.perioder.mapIndexed { i, periode ->
                val id = if(i == utbetaling.perioder.size - 1) utbetaling.lastPeriodeId else PeriodeId()

                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = id.toString(),
                    forrigePeriodeId = forrigeId?.toString().also { forrigeId = id},
                    vedtaksdato = utbetaling.vedtakstidspunkt.toLocalDate(),
                    klassekode = klassekode(utbetaling.stønad),
                    fom = periode.fom,
                    tom = periode.tom,
                    sats = periode.beløp,
                    satstype = periode.satstype,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                )
            }
        )
    }
}
