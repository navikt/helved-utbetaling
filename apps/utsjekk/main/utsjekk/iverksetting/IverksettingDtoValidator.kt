package utsjekk.iverksetting

import models.kontrakter.felles.GyldigBehandlingId
import models.kontrakter.felles.GyldigSakId
import models.kontrakter.felles.Satstype
import models.kontrakter.felles.StønadTypeDagpenger
import models.kontrakter.iverksett.Ferietillegg
import models.kontrakter.iverksett.IverksettV2Dto
import models.kontrakter.iverksett.StønadsdataDagpengerDto
import utsjekk.badRequest
import java.time.LocalDate
import java.time.YearMonth

fun IverksettV2Dto.validate() {
    sakIdTilfredsstillerLengdebegrensning(this)
    behandlingIdTilfredsstillerLengdebegrensning(this)
    fraOgMedKommerFørTilOgMedIUtbetalingsperioder(this)
    utbetalingsperioderMedLikStønadsdataOverlapperIkkeITid(this)
    utbetalingsperioderSamsvarerMedSatstype(this)
    iverksettingIdSkalEntenIkkeVæreSattEllerVæreSattForNåværendeOgForrige(this)
    ingenUtbetalingsperioderHarStønadstypeEØSOgFerietilleggTilAvdød(this)
}

internal fun sakIdTilfredsstillerLengdebegrensning(iverksettDto: IverksettV2Dto) {
    if (iverksettDto.sakId.length !in 1..GyldigSakId.MAKSLENGDE) {
        badRequest(msg = "lengde må være [1 <= ${GyldigSakId.MAKSLENGDE}]", field = "sakId")
    }
}

internal fun behandlingIdTilfredsstillerLengdebegrensning(iverksettDto: IverksettV2Dto) {
    if (iverksettDto.behandlingId.length !in 1..GyldigBehandlingId.MAKSLENGDE) {
        badRequest(msg = "lengde må være [1 <= ${GyldigBehandlingId.MAKSLENGDE}]", field = "behandlingId")
    }
}

internal fun fraOgMedKommerFørTilOgMedIUtbetalingsperioder(iverksettDto: IverksettV2Dto) {
    val alleErOk =
        iverksettDto.vedtak.utbetalinger.all {
//            it.fraOgMedDato <= it.tilOgMedDato // TODO: test ut dette
            !it.tilOgMedDato.isBefore(it.fraOgMedDato)
        }

    if (!alleErOk) {
        badRequest(msg = "fom må være før eller lik tom", field = "fraOgMedDato/tilOgMedDato")
    }
}

internal fun utbetalingsperioderMedLikStønadsdataOverlapperIkkeITid(iverksettDto: IverksettV2Dto) {
    val allePerioderErUavhengige =
        iverksettDto.vedtak.utbetalinger
            .groupBy { it.stønadsdata }
            .all { utbetalinger ->
                utbetalinger.value
                    .sortedBy { it.fraOgMedDato }
                    .windowed(2, 1, false) {
                        val førstePeriodeTom = it.first().tilOgMedDato
                        val sistePeriodeFom = it.last().fraOgMedDato

                        førstePeriodeTom.isBefore(sistePeriodeFom)
                    }.all { it }
            }

    if (!allePerioderErUavhengige) {
        badRequest("Utbetalinger inneholder perioder som overlapper i tid")
    }
}

internal fun utbetalingsperioderSamsvarerMedSatstype(iverksettDto: IverksettV2Dto) {
    val satstype =
        iverksettDto.vedtak.utbetalinger
            .firstOrNull()
            ?.satstype

    if (satstype == Satstype.MÅNEDLIG) {
        val alleFomErStartenAvMåned = iverksettDto.vedtak.utbetalinger.all { it.fraOgMedDato.dayOfMonth == 1 }
        val alleTomErSluttenAvMåned =
            iverksettDto.vedtak.utbetalinger.all {
                val sisteDag = YearMonth.from(it.tilOgMedDato).atEndOfMonth().dayOfMonth
                it.tilOgMedDato.dayOfMonth == sisteDag
            }

        if (!(alleTomErSluttenAvMåned && alleFomErStartenAvMåned)) {
            badRequest(
                "Det finnes utbetalinger med månedssats der periodene ikke samsvarer med hele måneder",
            )
        }
    }
}

internal fun iverksettingIdSkalEntenIkkeVæreSattEllerVæreSattForNåværendeOgForrige(iverksettDto: IverksettV2Dto) {
    if (iverksettDto.iverksettingId != null &&
        iverksettDto.forrigeIverksetting != null &&
        iverksettDto.forrigeIverksetting?.iverksettingId == null
    ) {
        badRequest(
            "IverksettingId er satt for nåværende iverksetting, men ikke forrige iverksetting",
        )
    }

    if (iverksettDto.iverksettingId == null &&
        iverksettDto.forrigeIverksetting != null &&
        iverksettDto.forrigeIverksetting?.iverksettingId != null
    ) {
        badRequest(
            "IverksettingId er satt for forrige iverksetting, men ikke nåværende iverksetting",
        )
    }
}

internal fun ingenUtbetalingsperioderHarStønadstypeEØSOgFerietilleggTilAvdød(iverksettDto: IverksettV2Dto) {
    val ugyldigKombinasjon =
        iverksettDto.vedtak.utbetalinger.any {
            if (it.stønadsdata is StønadsdataDagpengerDto) {
                val sd = it.stønadsdata as StønadsdataDagpengerDto
                sd.stønadstype == StønadTypeDagpenger.DAGPENGER_EØS && sd.ferietillegg == Ferietillegg.AVDØD
            } else {
                false
            }
        }

    if (ugyldigKombinasjon) {
        badRequest(
            "Ferietillegg til avdød er ikke tillatt for stønadstypen ${StønadTypeDagpenger.DAGPENGER_EØS}",
        )
    }
}
