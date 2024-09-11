package utsjekk.iverksetting.utbetalingsoppdrag.bdd

import no.nav.utsjekk.kontrakter.felles.Satstype
import no.nav.utsjekk.kontrakter.felles.StønadType
import java.time.LocalDate

interface Domenenøkkel {
    val nøkkel: String
}

enum class DomenebegrepAndeler(override val nøkkel: String) : Domenenøkkel {
    YTELSE_TYPE("Ytelse"),
    UTEN_ANDELER("Uten andeler"),
    BELØP("Beløp"),
    SATSTYPE("Satstype"),
}

enum class DomenebegrepUtbetalingsoppdrag(override val nøkkel: String) : Domenenøkkel {
    FØRSTE_UTBETALING_SAK("Første utbetaling sak"),
    ER_ENDRING("Er endring"),
    PERIODE_ID("Periode id"),
    FORRIGE_PERIODE_ID("Forrige periode id"),
    BELØP("Beløp"),
    YTELSE_TYPE("Ytelse"),
    OPPHØRSDATO("Opphørsdato"),
}

data class ForventetUtbetalingsoppdrag(
    val behandlingId: String,
    val erFørsteUtbetalingPåSak: Boolean,
    val utbetalingsperiode: List<ForventetUtbetalingsperiode>,
)

data class ForventetUtbetalingsperiode(
    val erEndringPåEksisterendePeriode: Boolean,
    val periodeId: Long,
    val forrigePeriodeId: Long?,
    val sats: Int,
    val ytelse: StønadType,
    val fom: LocalDate,
    val tom: LocalDate,
    val opphør: LocalDate?,
    val satstype: Satstype,
)

//object OppdragParser {
//    fun <T: WithBehandlingId> mapAndeler(dataTable: DataTable, parser: (Iterator<String>)-> T): Map<UUID, List<AndelData>> {
//        var index = 0L
//        return dataTable.input.into(parser).map { (behandlingId, rader) ->
//            val andeler = parseAndeler(rader, index)
//            index += andeler.size
//            behandlingIdTilUUID[behandlingId.toInt()]!! to andeler
//        }.toMap()
//    }
//
//    private fun parseAndeler(
//        rader: List<Map<String, String>>,
//        forrigeAndelId: Long,
//    ): List<AndelData> {
//        val erUtenAndeler = (parseValgfriBoolean(DomenebegrepAndeler.UTEN_ANDELER, rader.first()) ?: false)
//        var andelId = forrigeAndelId
//        return if (erUtenAndeler) {
//            emptyList()
//        } else {
//            rader.map { mapAndelTilkjentYtelse(it, andelId++) }
//        }
//    }
//
//    private fun mapAndelTilkjentYtelse(
//        rad: Map<String, String>,
//        andelId: Long,
//    ): AndelData {
//        val stønadsdataDagpenger =
//            StønadsdataDagpenger(
//                stønadstype =
//                parseValgfriEnum<StønadTypeDagpenger>(DomenebegrepAndeler.YTELSE_TYPE, rad)
//                    ?: StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR,
//                ferietillegg = null,
//            )
//        return AndelData(
//            id = andelId.toString(),
//            fom = parseDato(Domenebegrep.FRA_DATO, rad),
//            tom = parseDato(Domenebegrep.TIL_DATO, rad),
//            beløp = parseInt(DomenebegrepAndeler.BELØP, rad),
//            satstype =
//            parseValgfriEnum<Satstype>(DomenebegrepAndeler.SATSTYPE, rad)
//                ?: Satstype.DAGLIG,
//            stønadsdata = stønadsdataDagpenger,
//            periodeId = parseValgfriLong(DomenebegrepUtbetalingsoppdrag.PERIODE_ID, rad),
//            forrigePeriodeId = parseValgfriLong(DomenebegrepUtbetalingsoppdrag.FORRIGE_PERIODE_ID, rad),
//        )
//    }
//
//    fun mapForventetUtbetalingsoppdrag(dataTable: DataTable): List<ForventetUtbetalingsoppdrag> {
//        return dataTable.groupByBehandlingId().map { (behandlingId, rader) ->
//            val rad = rader.first()
//            validerAlleKodeEndringerLike(rader)
//            ForventetUtbetalingsoppdrag(
//                behandlingId = behandlingId,
//                erFørsteUtbetalingPåSak = parseBoolean(DomenebegrepUtbetalingsoppdrag.FØRSTE_UTBETALING_SAK, rad),
//                utbetalingsperiode = rader.map { mapForventetUtbetalingsperiode(it) },
//            )
//        }
//    }
//
//    private fun mapForventetUtbetalingsperiode(it: Map<String, String>) =
//        ForventetUtbetalingsperiode(
//            erEndringPåEksisterendePeriode = parseBoolean(DomenebegrepUtbetalingsoppdrag.ER_ENDRING, it),
//            periodeId = parseLong(DomenebegrepUtbetalingsoppdrag.PERIODE_ID, it),
//            forrigePeriodeId = parseValgfriLong(DomenebegrepUtbetalingsoppdrag.FORRIGE_PERIODE_ID, it),
//            sats = parseInt(DomenebegrepUtbetalingsoppdrag.BELØP, it),
//            ytelse =
//            parseValgfriEnum<StønadTypeDagpenger>(DomenebegrepUtbetalingsoppdrag.YTELSE_TYPE, it)
//                ?: StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR,
//            fom = parseDato(Domenebegrep.FRA_DATO, it),
//            tom = parseDato(Domenebegrep.TIL_DATO, it),
//            opphør = parseValgfriDato(DomenebegrepUtbetalingsoppdrag.OPPHØRSDATO, it),
//            satstype =
//            parseValgfriEnum<Satstype>(DomenebegrepAndeler.SATSTYPE, it)
//                ?: Satstype.DAGLIG,
//        )
//
//    private fun validerAlleKodeEndringerLike(rader: List<Map<String, String>>) {
//        rader.map { parseBoolean(DomenebegrepUtbetalingsoppdrag.FØRSTE_UTBETALING_SAK, it) }
//            .zipWithNext().forEach {
//                assertEquals(it.second, it.first)
//            }
//    }
//}
