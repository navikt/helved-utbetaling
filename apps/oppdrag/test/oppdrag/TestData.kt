package oppdrag

import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.Satstype
import no.nav.utsjekk.kontrakter.oppdrag.Opphør
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsperiode
import oppdrag.iverksetting.domene.OppdragMapper
import oppdrag.iverksetting.tilstand.OppdragId
import oppdrag.iverksetting.tilstand.OppdragLager
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

object RandomOSURId {
    private val chars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    fun generate() =
        (1..20)
            .map { Random.nextInt(0, chars.size).let { chars[it] } }
            .joinToString("")
}

fun etUtbetalingsoppdrag(
    avstemmingstidspunkt: LocalDateTime = LocalDateTime.now(),
    fagsystem: Fagsystem = Fagsystem.DAGPENGER,
    stønadstype: String = "DPORAS",
    fagsak: String = RandomOSURId.generate(),
    aktør: String = "12345678911",
    saksbehandlerId: String = "Z999999",
    beslutterId: String? = "Z888888",
    iverksettingId: String? = null,
    erFørsteUtbetalingPåSak: Boolean = true,
    brukersNavKontor: String? = null,
    vararg utbetalingsperiode: Utbetalingsperiode = arrayOf(enUtbetalingsperiode(stønadstype, aktør)),
) = Utbetalingsoppdrag(
    erFørsteUtbetalingPåSak = erFørsteUtbetalingPåSak,
    fagsystem = fagsystem,
    saksnummer = fagsak,
    aktør = aktør,
    saksbehandlerId = saksbehandlerId,
    beslutterId = beslutterId,
    avstemmingstidspunkt = avstemmingstidspunkt,
    utbetalingsperiode = utbetalingsperiode.toList(),
    iverksettingId = iverksettingId,
    brukersNavKontor = brukersNavKontor,
)

fun enUtbetalingsperiode(
    klassifisering: String = "DPORAS",
    aktør: String = "12345678911",
    periodeId: Long = 1,
    forrigePeriodeId: Long? = null,
    beløp: Int = 100,
    fom: LocalDate = LocalDate.now().withDayOfMonth(1),
    tom: LocalDate = LocalDate.now().plusYears(6),
    opphør: Opphør? = null,
    satstype: Satstype = Satstype.MÅNEDLIG,
    behandlingId: String = RandomOSURId.generate(),
) = Utbetalingsperiode(
    erEndringPåEksisterendePeriode = false,
    opphør = opphør,
    periodeId = periodeId,
    forrigePeriodeId = forrigePeriodeId,
    vedtaksdato = LocalDate.now(),
    klassifisering = klassifisering,
    fom = fom,
    tom = tom,
    sats = beløp.toBigDecimal(),
    satstype = satstype,
    utbetalesTil = aktør,
    behandlingId = behandlingId,
    utbetalingsgrad = 50,
)

internal val Utbetalingsoppdrag.somOppdragLager: OppdragLager
    get() {
        val tilOppdrag110 = OppdragMapper.tilOppdrag110(this)
        val oppdrag = OppdragMapper.tilOppdrag(tilOppdrag110)
        return OppdragLager.lagFraOppdrag(this, oppdrag)
    }

internal val Utbetalingsoppdrag.oppdragId
    get() =
        OppdragId(
            fagsystem = this.fagsystem,
            fagsakId = this.saksnummer,
            behandlingId = this.utbetalingsperiode[0].behandlingId,
            iverksettingId = this.iverksettingId,
        )