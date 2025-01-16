package oppdrag.utbetaling

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.math.BigDecimal

@JvmInline
value class UtbetalingId(val id: UUID) {
    companion object
}

enum class FagsystemDto(val kode: String) {
    DAGPENGER("DP"), 
    TILTAKSPENGER("TILTPENG"),
    TILLEGGSSTØNADER("TILLST");
}

data class UtbetalingsoppdragDto(
    val uid: UtbetalingId,
    val erFørsteUtbetalingPåSak: Boolean,
    val fagsystem: FagsystemDto,
    val saksnummer: String,
    val aktør: String,
    val saksbehandlerId: String,
    val beslutterId: String,
    val utbetalingsperiode: UtbetalingsperiodeDto,
    val avstemmingstidspunkt: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
    val brukersNavKontor: String? = null,
) {
    companion object;

    fun into() = no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag(
        erFørsteUtbetalingPåSak= erFørsteUtbetalingPåSak,
        fagsystem= when (fagsystem) {
            FagsystemDto.DAGPENGER -> no.nav.utsjekk.kontrakter.felles.Fagsystem.DAGPENGER 
            FagsystemDto.TILTAKSPENGER -> no.nav.utsjekk.kontrakter.felles.Fagsystem.TILTAKSPENGER
            FagsystemDto.TILLEGGSSTØNADER -> no.nav.utsjekk.kontrakter.felles.Fagsystem.TILLEGGSSTØNADER
        },
        saksnummer= saksnummer,
        iverksettingId= null,
        aktør= aktør,
        saksbehandlerId= saksbehandlerId,
        beslutterId= beslutterId,
        avstemmingstidspunkt= avstemmingstidspunkt,
        utbetalingsperiode= listOf(utbetalingsperiode.into()),
        brukersNavKontor= brukersNavKontor,
    )
}

data class UtbetalingsperiodeDto(
    val erEndringPåEksisterendePeriode: Boolean,
    val id: UInt,
    val vedtaksdato: LocalDate,
    val klassekode: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val sats: UInt,
    val satstype: Satstype,
    val utbetalesTil: String,
    val behandlingId: String,
    val opphør: Opphør? = null,
    val forrigePeriodeId: UInt? = null,
) {
    companion object;

    fun into() = no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsperiode(
        erEndringPåEksisterendePeriode= erEndringPåEksisterendePeriode,
        opphør= opphør?.let { no.nav.utsjekk.kontrakter.oppdrag.Opphør(it.fom) },
        periodeId= id.toLong(), // TODO: denne skal byttes ut med en long i databasen også
        forrigePeriodeId= forrigePeriodeId?.toLong(),
        vedtaksdato= vedtaksdato,
        klassifisering= klassekode,
        fom= fom,
        tom= tom,
        sats= BigDecimal(sats.toDouble()),
        satstype= when (satstype) {
            Satstype.DAG ->no.nav.utsjekk.kontrakter.felles.Satstype.DAGLIG_INKL_HELG
            Satstype.VIRKEDAG ->no.nav.utsjekk.kontrakter.felles.Satstype.DAGLIG
            Satstype.MND ->no.nav.utsjekk.kontrakter.felles.Satstype.MÅNEDLIG
            Satstype.ENGANGS ->no.nav.utsjekk.kontrakter.felles.Satstype.ENGANGS 
        },
        utbetalesTil= utbetalesTil,
        behandlingId= behandlingId,
        utbetalingsgrad= null,
    )
}

data class Opphør(val fom: LocalDate)

enum class Satstype {
    DAG,
    VIRKEDAG,
    MND,
    ENGANGS
}
