package models

import com.fasterxml.jackson.annotation.JsonCreator
import libs.utils.appLog
import libs.utils.secureLog
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

@JvmInline
value class SakId(val id: String) {
    override fun toString(): String = id
}

@JvmInline
value class BehandlingId(val id: String) {
    override fun toString(): String = id
}

@JvmInline
value class NavEnhet(val enhet: String) {
    override fun toString(): String = enhet
}

@JvmInline
value class Personident(val ident: String) {
    override fun toString(): String = ident
}

@JvmInline
value class Navident(val ident: String) {
    override fun toString(): String = ident
}

@JvmInline
value class UtbetalingId(val id: UUID) {
    override fun toString(): String = id.toString()
}

data class Utbetaling(
    val dryrun: Boolean,
    val originalKey: String,
    val fagsystem: Fagsystem,
    val uid: UtbetalingId,
    val action: Action,
    val førsteUtbetalingPåSak: Boolean,
    val sakId: SakId,
    val behandlingId: BehandlingId,
    val lastPeriodeId: PeriodeId,
    val personident: Personident,
    val vedtakstidspunkt: LocalDateTime,
    val stønad: Stønadstype,
    val beslutterId: Navident,
    val saksbehandlerId: Navident,
    val periodetype: Periodetype,
    val avvent: Avvent?,
    val perioder: List<Utbetalingsperiode>,
) {
    fun validate() {
        failOnEmptyPerioder()
        failOnÅrsskifte()
        failOnDuplicatePerioder()
        failOnTomBeforeFom()
        //failOnIllegalFutureUtbetaling()
        failOnTooManyPeriods()
        failOnZeroBeløp()
        failOnTooLongSakId()
        failOnTooLongBehandlingId()
        //failOnWeekendInPeriodetypeDag()
    }

    fun isDuplicate(other: Utbetaling?): Boolean {
        if (other == null) {
            appLog.info("uid $uid is new")
            return false
        }

        val isDuplicate = uid == other.uid
                && action == other.action
                && sakId == other.sakId
                && behandlingId == other.behandlingId
                && personident == other.personident
                && vedtakstidspunkt.equals(other.vedtakstidspunkt)
                && stønad == other.stønad
                && beslutterId == other.beslutterId
                && saksbehandlerId == other.saksbehandlerId
                && periodetype == other.periodetype
                && avvent == other.avvent
                && perioder == other.perioder


        if (isDuplicate) {
            appLog.info("Duplicate message found for $uid")
            return isDuplicate
        }

        appLog.info("uid $uid is changed")
        return isDuplicate
    }
}


enum class Årsak(val kode: String) {
    AVVENT_AVREGNING("AVAV"),
    AVVENT_REFUSJONSKRAV("AVRK"),
}

data class Avvent(
    val fom: LocalDate,
    val tom: LocalDate,
    val overføres: LocalDate? = null,
    val årsak: Årsak? = null,
    val feilregistrering: Boolean = false,
)

fun Utbetaling.failOnEmptyPerioder() {
    if (perioder.isEmpty()) {
        badRequest(DocumentedErrors.Async.Utbetaling.MANGLER_PERIODER)
    }
}

fun Utbetaling.validateLockedFields(other: Utbetaling) {
    if (sakId != other.sakId) badRequest(DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_SAK_ID)
    if (personident != other.personident) badRequest(DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_PERSONIDENT)
    if (stønad != other.stønad) badRequest(DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_STØNAD)
    if (periodetype != other.periodetype) badRequest(DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_PERIODETYPE)
}

fun Utbetaling.preValidateLockedFields(other: Utbetaling) {
    if (sakId != other.sakId) badRequest(DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_SAK_ID)
    if (personident != other.personident) badRequest(DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_PERSONIDENT)
    if (periodetype != other.periodetype) badRequest(DocumentedErrors.Async.Utbetaling.IMMUTABLE_FIELD_PERIODETYPE)
}

fun Utbetaling.validateMinimumChanges(other: Utbetaling) {
    if (perioder.size != other.perioder.size) return
    val ingenEndring = perioder.sortedBy { it.hashCode() }.zip(other.perioder.sortedBy { it.hashCode() })
        .all { (l, r) -> l.beløp == r.beløp && l.fom.equals(r.fom) && l.tom.equals(r.tom) && l.vedtakssats == r.vedtakssats }
    if (ingenEndring) conflict(DocumentedErrors.Async.Utbetaling.MINIMUM_CHANGES)
}

fun Utbetaling.failOnÅrsskifte() {
    if (periodetype != Periodetype.EN_GANG) return
    if (perioder.minBy { it.fom }.fom.year != perioder.maxBy { it.tom }.tom.year) {
        badRequest(DocumentedErrors.Async.Utbetaling.ENGANGS_OVER_ÅRSSKIFTE)
    }
}

fun Utbetaling.failOnZeroBeløp() {
    if (perioder.any { it.beløp == 0u }) {
        badRequest(DocumentedErrors.Async.Utbetaling.UGYLDIG_BELØP)
    }
}

fun Utbetaling.failOnDuplicatePerioder() {
    val dupFom = perioder.groupBy { it.fom }.any { (_, perioder) -> perioder.size != 1 }
    if (dupFom) badRequest(DocumentedErrors.Async.Utbetaling.DUPLIKATE_PERIODER)
    val dupTom = perioder.groupBy { it.tom }.any { (_, perioder) -> perioder.size != 1 }
    if (dupTom) badRequest(DocumentedErrors.Async.Utbetaling.DUPLIKATE_PERIODER)
}

fun Utbetaling.failOnTomBeforeFom() {
    if (!perioder.all { it.fom <= it.tom }) badRequest(DocumentedErrors.Async.Utbetaling.UGYLDIG_PERIODE)
}

fun Utbetaling.failOnIllegalFutureUtbetaling() {
    if (stønad is StønadTypeTilleggsstønader) return
    val isDay = periodetype in listOf(Periodetype.DAG, Periodetype.UKEDAG)
    val dayIsFuture = perioder.maxBy { it.tom }.tom.isAfter(LocalDate.now())
    if (isDay && dayIsFuture) badRequest(DocumentedErrors.Async.Utbetaling.FREMTIDIG_UTBETALING)
}

fun Utbetaling.failOnTooManyPeriods() {
    if (periodetype in listOf(Periodetype.DAG, Periodetype.UKEDAG)) {
        val min = perioder.minBy { it.fom }.fom
        val max = perioder.maxBy { it.tom }.tom
        val tooManyPeriods = java.time.temporal.ChronoUnit.DAYS.between(min, max) + 1 > 1000
        if (tooManyPeriods) badRequest(DocumentedErrors.Async.Utbetaling.FOR_LANG_UTBETALING)
    }
}

fun Utbetaling.failOnTooLongSakId() {
    if (sakId.id.length > 30) {
        badRequest(DocumentedErrors.Async.Utbetaling.UGYLDIG_SAK_ID)
    }
}

fun Utbetaling.failOnTooLongBehandlingId() {
    if (behandlingId.id.length > 30) {
        badRequest(DocumentedErrors.Async.Utbetaling.UGYLDIG_BEHANDLING_ID)
    }
}

@JvmInline
value class PeriodeId(private val id: String) {
    constructor() : this(UUID.randomUUID().toString())

    init {
        toString() // verifiser at encoding blir under 30 tegn
    }

    companion object {
        fun decode(encoded: String): PeriodeId {
            try {
                val byteBuffer: ByteBuffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded))
                // ||||||||||||||||||||||||||||||||||||||||||||||||||||||||
                // ^ les neste 64 og lag en long
                return PeriodeId(UUID(byteBuffer.long, byteBuffer.long).toString())
            } catch (e: Throwable) {
                appLog.debug("Klarte ikke dekomprimere UUID: $encoded. Bruker det gamle formatet.")
                secureLog.warn("Klarte ikke dekomprimere UUID: $encoded. Bruker det gamle formatet.", e)
                return PeriodeId(encoded)
            }
        }
    }

    /**
     * UUID er 128 bit eller 36 tegn
     * Oppdrag begrenses til 30 tegn (eller 240 bits)
     * To Longs er 128 bits
     */
    override fun toString(): String {
        try {
            val uuid = UUID.fromString(id)
            val byteBuffer = ByteBuffer.allocate(java.lang.Long.BYTES * 2).apply { // 128 bits
                putLong(uuid.mostSignificantBits) // første 64 bits
                putLong(uuid.leastSignificantBits) // siste 64 bits
            }
            // e.g. dNl8DVZKQM2gJ0AcJ/pNKQ== (24 tegn)
            return Base64.getEncoder().encodeToString(byteBuffer.array()).also {
                require(it.length <= 30) { "base64 encoding av UUID ble over 30 tegn." }
            }
        } catch (e: Exception) {
            return id.also {
                require(it.length <= 30) { "gammelt format av periodeid er over 30 tegn." }
            }
        }
    }
}

data class Utbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: UInt,
    val betalendeEnhet: NavEnhet? = null,
    val vedtakssats: UInt? = null,
)

fun List<Utbetalingsperiode>.betalendeEnhet(): NavEnhet? {
    return sortedBy { it.tom }.find { it.betalendeEnhet != null }?.betalendeEnhet
}

enum class Periodetype(val satstype: String) {
    DAG("DAG7"),
    UKEDAG("DAG"),
    MND("MND"),
    EN_GANG("ENG");
}

enum class Frekvens(val utbetFrekvens: String) {
    DAG("DAG"),
    UKE("UKE"),
    MND("MND"),
    TOUKER("14DG"),
    ENG("ENG");

}

sealed interface Stønadstype {
    val name: String
    val klassekode: String

    companion object {
        @JsonCreator
        @JvmStatic
        fun valueOf(str: String): Stønadstype =
            runCatching { StønadTypeDagpenger.valueOf(str) }
                .recoverCatching { StønadTypeTilleggsstønader.valueOf(str) }
                .recoverCatching { StønadTypeTiltakspenger.valueOf(str) }
                .recoverCatching { StønadTypeAAP.valueOf(str) }
                .recoverCatching { StønadTypeHistorisk.valueOf(str) }
                .getOrThrow()

        fun fraKode(klassekode: String): Stønadstype =
            (StønadTypeDagpenger.entries + StønadTypeTiltakspenger.entries + StønadTypeTilleggsstønader.entries + StønadTypeAAP.entries + StønadTypeHistorisk.entries)
                .single { it.klassekode == klassekode }
    }
}

enum class StønadTypeDagpenger(override val klassekode: String) : Stønadstype {
    DAGPENGER("DAGPENGER"),
    DAGPENGERFERIE("DAGPENGERFERIE"),

    ARBEIDSSØKER_ORDINÆR(""),                          // bakoverkomaptibel enn så lenge
    PERMITTERING_ORDINÆR(""),                          // bakoverkomaptibel enn så lenge
    PERMITTERING_FISKEINDUSTRI(""),                    // bakoverkomaptibel enn så lenge
    EØS(""),                                           // bakoverkomaptibel enn så lenge
    ARBEIDSSØKER_ORDINÆR_FERIETILLEGG(""),             // bakoverkomaptibel enn så lenge
    PERMITTERING_ORDINÆR_FERIETILLEGG(""),             // bakoverkomaptibel enn så lenge
    PERMITTERING_FISKEINDUSTRI_FERIETILLEGG(""),       // bakoverkomaptibel enn så lenge
    EØS_FERIETILLEGG(""),                              // bakoverkomaptibel enn så lenge
    ARBEIDSSØKER_ORDINÆR_FERIETILLEGG_AVDØD(""),       // bakoverkomaptibel enn så lenge
    PERMITTERING_ORDINÆR_FERIETILLEGG_AVDØD(""),       // bakoverkomaptibel enn så lenge
    PERMITTERING_FISKEINDUSTRI_FERIETILLEGG_AVDØD(""), // bakoverkomaptibel enn så lenge
    EØS_FERIETILLEGG_AVDØD(""),                        // bakoverkomaptibel enn så lenge
}

enum class StønadTypeTiltakspenger(override val klassekode: String) : Stønadstype {
    ARBEIDSFORBEREDENDE_TRENING("TPTPAFT"),
    ARBEIDSRETTET_REHABILITERING("TPTPARREHABAGDAG"),
    ARBEIDSTRENING("TPTPATT"),
    AVKLARING("TPTPAAG"),
    DIGITAL_JOBBKLUBB("TPTPDJB"),
    ENKELTPLASS_AMO("TPTPEPAMO"),
    ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG("TPTPEPVGSHOU"),
    FORSØK_OPPLÆRING_LENGRE_VARIGHET("TPTPFLV"),
    GRUPPE_AMO("TPTPGRAMO"),
    GRUPPE_VGS_OG_HØYERE_YRKESFAG("TPTPGRVGSHOY"),
    HØYERE_UTDANNING("TPTPHOYUTD"),
    INDIVIDUELL_JOBBSTØTTE("TPTPIPS"),
    INDIVIDUELL_KARRIERESTØTTE_UNG("TPTPIPSUNG"),
    JOBBKLUBB("TPTPJK2009"),
    OPPFØLGING("TPTPOPPFAG"),
    UTVIDET_OPPFØLGING_I_NAV("TPTPUAOPPF"),
    UTVIDET_OPPFØLGING_I_OPPLÆRING("TPTPUOPPFOPPL"),

    ARBEIDSFORBEREDENDE_TRENING_BARN("TPBTAF"),
    ARBEIDSRETTET_REHABILITERING_BARN("TPBTARREHABAGDAG"),
    ARBEIDSTRENING_BARN("TPBTATTILT"),
    AVKLARING_BARN("TPBTAAGR"),
    DIGITAL_JOBBKLUBB_BARN("TPBTDJK"),
    ENKELTPLASS_AMO_BARN("TPBTEPAMO"),
    ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG_BARN("TPBTEPVGSHOY"),
    FORSØK_OPPLÆRING_LENGRE_VARIGHET_BARN("TPBTFLV"),
    GRUPPE_AMO_BARN("TPBTGRAMO"),
    GRUPPE_VGS_OG_HØYERE_YRKESFAG_BARN("TPBTGRVGSHOY"),
    HØYERE_UTDANNING_BARN("TPBTHOYUTD"),
    INDIVIDUELL_JOBBSTØTTE_BARN("TPBTIPS"),
    INDIVIDUELL_KARRIERESTØTTE_UNG_BARN("TPBTIPSUNG"),
    JOBBKLUBB_BARN("TPBTJK2009"),
    OPPFØLGING_BARN("TPBTOPPFAGR"),
    UTVIDET_OPPFØLGING_I_NAV_BARN("TPBTUAOPPFL"),
    UTVIDET_OPPFØLGING_I_OPPLÆRING_BARN("TPBTUOPPFOPPL"),
}

fun StønadTypeTiltakspenger.medBarnetillegg(barnetillegg: Boolean): StønadTypeTiltakspenger =
    if (barnetillegg) {
        when (this) {
            StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING -> StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING_BARN
            StønadTypeTiltakspenger.ARBEIDSRETTET_REHABILITERING -> StønadTypeTiltakspenger.ARBEIDSRETTET_REHABILITERING_BARN
            StønadTypeTiltakspenger.ARBEIDSTRENING -> StønadTypeTiltakspenger.ARBEIDSTRENING_BARN
            StønadTypeTiltakspenger.AVKLARING -> StønadTypeTiltakspenger.AVKLARING_BARN
            StønadTypeTiltakspenger.DIGITAL_JOBBKLUBB -> StønadTypeTiltakspenger.DIGITAL_JOBBKLUBB_BARN
            StønadTypeTiltakspenger.ENKELTPLASS_AMO -> StønadTypeTiltakspenger.ENKELTPLASS_AMO_BARN
            StønadTypeTiltakspenger.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG -> StønadTypeTiltakspenger.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG_BARN
            StønadTypeTiltakspenger.FORSØK_OPPLÆRING_LENGRE_VARIGHET -> StønadTypeTiltakspenger.FORSØK_OPPLÆRING_LENGRE_VARIGHET_BARN
            StønadTypeTiltakspenger.GRUPPE_AMO -> StønadTypeTiltakspenger.GRUPPE_AMO_BARN
            StønadTypeTiltakspenger.GRUPPE_VGS_OG_HØYERE_YRKESFAG -> StønadTypeTiltakspenger.GRUPPE_VGS_OG_HØYERE_YRKESFAG_BARN
            StønadTypeTiltakspenger.HØYERE_UTDANNING -> StønadTypeTiltakspenger.HØYERE_UTDANNING_BARN
            StønadTypeTiltakspenger.INDIVIDUELL_JOBBSTØTTE -> StønadTypeTiltakspenger.INDIVIDUELL_JOBBSTØTTE_BARN
            StønadTypeTiltakspenger.INDIVIDUELL_KARRIERESTØTTE_UNG -> StønadTypeTiltakspenger.INDIVIDUELL_KARRIERESTØTTE_UNG_BARN
            StønadTypeTiltakspenger.JOBBKLUBB -> StønadTypeTiltakspenger.JOBBKLUBB_BARN
            StønadTypeTiltakspenger.OPPFØLGING -> StønadTypeTiltakspenger.OPPFØLGING_BARN
            StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_NAV -> StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_NAV_BARN
            StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_OPPLÆRING -> StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_OPPLÆRING_BARN
            else -> error("${this.name} har ikke klassekode for barnetillegg")
        }
    } else {
        this
    }

enum class StønadTypeTilleggsstønader(override val klassekode: String) : Stønadstype {
    TILSYN_BARN_ENSLIG_FORSØRGER("TSTBASISP2-OP"),
    TILSYN_BARN_AAP("TSTBASISP4-OP"),
    TILSYN_BARN_ETTERLATTE("TSTBASISP5-OP"),
    LÆREMIDLER_ENSLIG_FORSØRGER("TSLMASISP2-OP"),
    LÆREMIDLER_AAP("TSLMASISP3-OP"),
    LÆREMIDLER_ETTERLATTE("TSLMASISP4-OP"),
    BOUTGIFTER_AAP("TSBUASIA-OP"),
    BOUTGIFTER_ENSLIG_FORSØRGER("TSBUAISP2-OP"),
    BOUTGIFTER_ETTERLATTE("TSBUAISP3-O"),
    @Deprecated("skrivefeil", ReplaceWith("DAGLIG_REISE_ENSLIG_FORSØRGER"))
    DAGLIG_REISE_ENSLIG_FORSØRGET("TSDRASISP1-OP"),
    DAGLIG_REISE_ENSLIG_FORSØRGER("TSDRASISP1-OP"),
    DAGLIG_REISE_AAP("TSDRASISP3-OP"),
    DAGLIG_REISE_ETTERLATTE("TSDRASISP4-OP"),

    REISE_TIL_SAMLING_ENSLIG_FORSØRGER(""),
    REISE_TIL_SAMLING_AAP(""),
    REISE_TIL_SAMLING_ETTERLATTE(""),
    @Deprecated("skrivefeil", ReplaceWith("REISE_OPPSTART_ENSLIG_FORSØRGER"))
    REISE_OPPSTART_ENSLIG_FORSØRGET(""),
    REISE_OPPSTART_ENSLIG_FORSØRGER(""),
    REISE_OPPSTART_AAP(""),
    REISE_OPPSTART_ETTERLATTE(""),
    REIS_ARBEID_ENSLIG_FORSØRGER(""),
    REIS_ARBEID_AAP(""),
    REIS_ARBEID_ETTERLATTE(""),
    FLYTTING_ENSLIG_FORSØRGER(""),
    FLYTTING_AAP(""),
    FLYTTING_ETTERLATTE(""),

    DAGLIG_REISE_TILTAK_ARBEIDSFORBEREDENDE("TSDRAFT-OP"),
    DAGLIG_REISE_TILTAK_ARBEIDSRETTET_REHAB("TSDRARREHABAGDAG-OP"),
    DAGLIG_REISE_TILTAK_ARBEIDSTRENING("TSDRATTT2-OP"),
    DAGLIG_REISE_TILTAK_AVKLARING("TSDRAAG-OP"),
    DAGLIG_REISE_TILTAK_DIGITAL_JOBBKLUBB("TSDRDIGJK-OP"),
    DAGLIG_REISE_TILTAK_ENKELTPLASS_AMO("TSDREPAMO-OP"),
    DAGLIG_REISE_TILTAK_ENKELTPLASS_FAG_YRKE_HOYERE_UTD("TSDREPVGSHOY-OP"),
    DAGLIG_REISE_TILTAK_FORSØK_OPPLÆRINGSTILTAK_LENGER_VARIGHET("TSDRFOLV"),
    DAGLIG_REISE_TILTAK_GRUPPE_AMO("TSDRGRAMO-OP"),
    DAGLIG_REISE_TILTAK_GRUPPE_FAG_YRKE_HOYERE_UTD("TSDRGRVGSHOY-OP"),
    DAGLIG_REISE_TILTAK_HØYERE_UTDANNING("TSDRHOYUTD-OP"),
    DAGLIG_REISE_TILTAK_INDIVIDUELL_JOBBSTØTTE("TSDRIPS-OP"),
    DAGLIG_REISE_TILTAK_INDIVIDUELL_JOBBSTØTTE_UNG("TSDRIPSUNG-OP"),
    DAGLIG_REISE_TILTAK_JOBBKLUBB("TSDRJB2009-OP"),
    DAGLIG_REISE_TILTAK_OPPFØLGING("TSDROPPFAG2-OP"),
    DAGLIG_REISE_TILTAK_UTVIDET_OPPFØLGING_I_NAV("TSDRUTVAVKLOPPF-OP"),
    DAGLIG_REISE_TILTAK_UTVIDET_OPPFØLGING_I_OPPLÆRING("TSDRUTVOPPFOPPL-OP")
}

enum class StønadTypeAAP(override val klassekode: String) : Stønadstype {
    AAP_UNDER_ARBEIDSAVKLARING("AAPOR"),
}

enum class StønadTypeHistorisk(override val klassekode: String) : Stønadstype {
    TILSKUDD_SMÅHJELPEMIDLER("HJRIM"), // bakoverkomaptibel enn så lenge

    REISEUTGIFTER("HTRUTR"),
    ORTOPEDISK_PROTESE("HTOHPR"),
    ORTOSE("HTOHHÅ"),
    SPESIALSKO("HTOHSKBA"),
    PARYKK("HTOHPAAV"),
    ANSIKTSDEFEKTPROTESE("HTOHAD"),
    BRYSTPROTESE("HTOHBP"),
    ØYEPROTESE("HTOHØP"),
    VANLIGE_SKO("HTOHAS"),
    FOTSENG("HTOHFTEN")
}

fun uuid(
    sakId: SakId,
    fagsystem: Fagsystem,
    meldeperiode: String,
    stønad: Stønadstype,
): UUID {
    val buffer = ByteBuffer.allocate(java.lang.Long.BYTES)
    buffer.putLong((fagsystem.name + sakId.id + meldeperiode + stønad.klassekode).hashCode().toLong())

    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(buffer.array())

    val bb = ByteBuffer.wrap(hash)
    val mostSigBits = bb.long
    val leastSigBits = bb.long

    return UUID(mostSigBits, leastSigBits)
}

fun fakeDelete(
    dryrun: Boolean,
    originalKey: String,
    sakId: SakId,
    uid: UtbetalingId,
    fagsystem: Fagsystem,
    stønad: Stønadstype,
    beslutterId: Navident,
    saksbehandlerId: Navident,
    personident: Personident,
    behandlingId: BehandlingId,
    periodetype: Periodetype,
    vedtakstidspunkt: LocalDateTime
) = Utbetaling(
    dryrun = dryrun,
    originalKey = originalKey,
    fagsystem = fagsystem,
    uid = uid,
    action = Action.DELETE,
    førsteUtbetalingPåSak = false,
    sakId = sakId,
    behandlingId = behandlingId,
    lastPeriodeId = PeriodeId(),
    personident = personident,
    vedtakstidspunkt = vedtakstidspunkt,
    stønad = stønad,
    beslutterId = beslutterId,
    saksbehandlerId = saksbehandlerId,
    periodetype = periodetype,
    avvent = null,
    perioder = listOf(Utbetalingsperiode(LocalDate.now(), LocalDate.now(), 1u)), // placeholder
)
