@file:UseSerializers(libs.kotlinx.LocalDateSerializer::class)

package peisschtappern

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import models.Fagsystem
import nl.adaptivity.xmlutil.serialization.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.days

private const val AVSTEMMING_NAMESPACE = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1"

private val avstemmingXml = XML {
    defaultPolicy {
        ignoreUnknownChildren()
        defaultPrimitiveOutputKind = OutputKind.Element
        defaultObjectOutputKind = OutputKind.Element
        verifyElementOrder = false
    }
}

object DashboardService {
    suspend fun dashboard(fom: Long, tom: Long): Dashboard {
        val successfulStatuses = Daos.findStatuses("OK", fom, tom)

        return Dashboard(
            feiletUtbetalinger = Daos.antallFeilet(fom, tom),
            pendingMismatch = PendingMismatchService.detectMismatches(fom, tom),
            avstemming = avstemming(fom, tom),
            oppdragUtenKvittering = Daos.findOppdragWithMissingStatus(fom, tom),
            dobbeltutbetalinger = DobbeltutbetalingService.finn(successfulStatuses)
        )
    }

    private fun parseWeirdAsHellXmlDate(weirdAsHellXmlDate: String): LocalDate {
        return LocalDate.parse(weirdAsHellXmlDate.take(8), DateTimeFormatter.BASIC_ISO_DATE)
    }

    private suspend fun avstemming(fom: Long, tom: Long): List<Dashboard.Avstemming> {
        val avstemminger = Daos.findAvstemminger(fom.minus(14.days.inWholeMilliseconds), tom)
        val yesterday = LocalDate.now().minusDays(1)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")

        return avstemminger.groupBy { it.fagsystem }.entries.map { (fagområde, meldinger) ->
            val alleMeldinger: List<AvstemmingXML> = meldinger.map {
                avstemmingXml.decodeFromString(AvstemmingXML.serializer(), it.value!!)
            }
            val meldinger: List<AvstemmingXML> = alleMeldinger
                .filter { xml ->
                    xml.aksjon.aksjonType == "DATA" &&
                            LocalDateTime.parse(xml.aksjon.nokkelFom, formatter).toLocalDate() <= yesterday &&
                            yesterday <= LocalDateTime.parse(xml.aksjon.nokkelTom, formatter).toLocalDate()
                }

            val sisteAvstemtDato: LocalDate? = alleMeldinger
                .filter { it.periode != null }
                .map { parseWeirdAsHellXmlDate(it.periode!!.datoAvstemtTom) }
                .fold(LocalDate.EPOCH) { acc, dato ->
                    if (dato > acc) {
                        dato
                    } else {
                        acc
                    }
                }
                .takeIf { it != LocalDate.EPOCH }

            if (meldinger.isEmpty()) {
                return@map Dashboard.Avstemming(
                    fagsystem = Fagsystem.from(fagområde!!),
                    sisteAvstemtDato = sisteAvstemtDato,
                )
            }

            val sisteAvstemming = meldinger.filter { it.periode != null }.maxByOrNull { it.periode!!.datoAvstemtTom }!!

            return@map Dashboard.Avstemming(
                fagsystem = Fagsystem.from(fagområde!!),
                datoAvstemtFom = parseWeirdAsHellXmlDate(sisteAvstemming.periode!!.datoAvstemtFom),
                datoAvstemtTom = parseWeirdAsHellXmlDate(sisteAvstemming.periode.datoAvstemtTom),
                sisteAvstemtDato = sisteAvstemtDato,
            )
        }
    }
}

@Serializable
data class Dashboard(
    val feiletUtbetalinger: Int,
    val pendingMismatch: List<PendingMismatch>,
    val avstemming: List<Avstemming>,
    val oppdragUtenKvittering: List<OppdragUtenKvittering>,
    val dobbeltutbetalinger: List<Suspect> = emptyList(),
) {
    @Serializable
    data class Avstemming(
        val fagsystem: Fagsystem,
        val sisteAvstemtDato: LocalDate? = null,
        val datoAvstemtFom: LocalDate? = null,
        val datoAvstemtTom: LocalDate? = null,
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
private data class AvstemmingXML(
    val aksjon: Aksjon,
    val periode: Periode?,
) {
    @Serializable
    @XmlSerialName("aksjon", "", "")
    data class Aksjon(
        val aksjonType: String,
        val nokkelFom: String,
        val nokkelTom: String,
    )

    @Serializable
    @XmlSerialName("periode", "", "")
    data class Periode(
        val datoAvstemtFom: String,
        val datoAvstemtTom: String,
    )
}
