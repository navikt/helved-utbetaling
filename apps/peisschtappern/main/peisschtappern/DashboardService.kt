@file:UseSerializers(libs.kotlinx.LocalDateSerializer::class)

package peisschtappern

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import libs.utils.erHelligdag
import libs.utils.forrigeVirkedag
import models.Fagsystem
import nl.adaptivity.xmlutil.serialization.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.days

private const val AVSTEMMING_NAMESPACE = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1"

internal val avstemmingXml = XML {
    defaultPolicy {
        ignoreUnknownChildren()
        defaultPrimitiveOutputKind = OutputKind.Element
        defaultObjectOutputKind = OutputKind.Element
        verifyElementOrder = false
    }
}

object DashboardService {
    suspend fun dashboard(fom: Long, tom: Long): Dashboard {
        return Dashboard(
            feiletUtbetalinger = Daos.feiletUtbetalinger(fom, tom),
            korrigerteFeiletUtbetalinger = Daos.korrigerteFeiletUtbetalinger(fom),
            pendingMismatch = PendingMismatchService.detectMismatches(fom, tom),
            avstemming = avstemming(fom, tom),
            oppdragUtenKvittering = Daos.findOppdragWithMissingStatus(fom, tom),
            dobbeltutbetalinger = DobbeltutbetalingService.finnUhåndterte(fom, tom),
        )
    }

    private suspend fun avstemming(fom: Long, tom: Long): List<Dashboard.Avstemming> {
        val dato = Instant.ofEpochMilli(tom).atZone(ZoneId.of("Europe/Oslo")).toLocalDate()
        val periode = avstemmingsperiode(dato)
        val oppdrag = Daos.findAvstemmingsgrunnlag(periode.first, periode.second, tom)
        val periodeStart = periode.first.atZone(ZoneId.of("Europe/Oslo")).toInstant().toEpochMilli()
        val meldinger = Daos.findAvstemminger(minOf(fom, periodeStart).minus(14.days.inWholeMilliseconds), tom)
            .map { avstemmingXml.decodeFromString(AvstemmingXML.serializer(), it.value!!) }
        return avstemmingsstatus(periode.first, periode.second, oppdrag, meldinger)
    }
}

@Serializable
data class Dashboard(
    val feiletUtbetalinger: List<Daos>,
    val korrigerteFeiletUtbetalinger: List<KorrigertFeiletUtbetaling>,
    val pendingMismatch: List<PendingMismatch>,
    val avstemming: List<Avstemming>,
    val oppdragUtenKvittering: List<OppdragUtenKvittering>,
    val dobbeltutbetalinger: List<Suspect> = emptyList(),
) {
    @Serializable
    data class KorrigertFeiletUtbetaling(
        val topic: String,
        val key: String,
        val reason: String,
        val registeredAt: Long,
    )

    @Serializable
    data class Avstemming(
        val fagsystem: Fagsystem,
        val sisteAvstemtDato: LocalDate? = null,
        val datoAvstemtFom: LocalDate? = null,
        val datoAvstemtTom: LocalDate? = null,
        val status: Avstemmingsstatus = Avstemmingsstatus.NOT_REQUIRED,
        val vurdertFom: LocalDate? = null,
        val vurdertTom: LocalDate? = null
    )

}

@Serializable
data class OppdragUtenKvittering(
    val key: String,
    val trace_id: String?,
    val fagsystem: String?,
    val sakId: String?,
    val system_time_ms: Long,
)

@Serializable
@XmlSerialName("avstemmingsdata", AVSTEMMING_NAMESPACE, "ns2")
internal data class AvstemmingXML(
    val aksjon: Aksjon,
    val periode: Periode? = null,
) {
    @Serializable
    @XmlSerialName("aksjon", "", "")
    data class Aksjon(
        val aksjonType: String,
        val nokkelFom: String,
        val nokkelTom: String,
        val avleverendeAvstemmingId: String,
        val avleverendeKomponentKode: String
    )

    @Serializable
    @XmlSerialName("periode", "", "")
    data class Periode(
        val datoAvstemtFom: String,
        val datoAvstemtTom: String,
    )
}

@Serializable
enum class Avstemmingsstatus {
    NOT_REQUIRED,
    COMPLETED,
    MISSING
}

internal fun avstemmingsperiode(dato: LocalDate): Pair<LocalDateTime, LocalDateTime> {
    val kjøredato = if (dato.erHelligdag()) dato.forrigeVirkedag() else dato
    return kjøredato.forrigeVirkedag().atStartOfDay() to kjøredato.atStartOfDay().minusNanos(1_000)
}

internal fun avstemmingsstatus(
    fom: LocalDateTime,
    tom: LocalDateTime,
    oppdrag: List<Pair<String, LocalDateTime>>,
    meldinger: List<AvstemmingXML>
): List<Dashboard.Avstemming> {
    val grunnlag = oppdrag.groupBy { Fagsystem.fromFagområde(it.first) }
    val avstemminger = meldinger.groupBy { Fagsystem.fromFagområde(it.aksjon.avleverendeKomponentKode.trim()) }
    return Fagsystem.entries.map { fagsystem ->
        val fullførte = fullførteAvstemminger(avstemminger[fagsystem].orEmpty())
        val forventede = grunnlag[fagsystem].orEmpty()
        val dekkende = fullførte.filter { it.dekker(fom) && it.dekker(tom) }
        val status = when {
            forventede.isEmpty() -> Avstemmingsstatus.NOT_REQUIRED
            forventede.all { (_, nøkkel) -> fullførte.any { it.dekker(nøkkel) } } -> Avstemmingsstatus.COMPLETED
            else -> Avstemmingsstatus.MISSING
        }
        val siste = dekkende.maxByOrNull { it.aksjon.nokkelTom }?.periode
        Dashboard.Avstemming(
            fagsystem = fagsystem,
            sisteAvstemtDato = fullførte.mapNotNull { it.periode?.tomDato() }.maxOrNull(),
            datoAvstemtFom = siste?.let { xmlDato(it.datoAvstemtFom) },
            datoAvstemtTom = siste?.tomDato(),
            status = status,
            vurdertFom = fom.toLocalDate(),
            vurdertTom = tom.toLocalDate()
        )
    }
}

private fun fullførteAvstemminger(meldinger: List<AvstemmingXML>): List<AvstemmingXML> = meldinger
    .groupBy { it.aksjon.avleverendeAvstemmingId }
    .values
    .filter { kjøring ->
        kjøring.map { it.aksjon.aksjonType }.containsAll(listOf("START", "DATA", "AVSL")) &&
            kjøring.map { it.aksjon.nokkelFom to it.aksjon.nokkelTom }.distinct().size == 1
    }
    .flatten()
    .filter { it.aksjon.aksjonType == "DATA" && it.periode != null }

private val nøkkelformat = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")

private fun AvstemmingXML.dekker(nøkkel: LocalDateTime): Boolean =
    nøkkel >= LocalDateTime.parse(aksjon.nokkelFom, nøkkelformat) &&
        nøkkel <= LocalDateTime.parse(aksjon.nokkelTom, nøkkelformat)

private fun xmlDato(dato: String): LocalDate = LocalDate.parse(dato.take(8), DateTimeFormatter.BASIC_ISO_DATE)

private fun AvstemmingXML.Periode.tomDato(): LocalDate = xmlDato(datoAvstemtTom)
