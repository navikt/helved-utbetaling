@file:UseSerializers(libs.kotlinx.LocalDateSerializer::class)

package peisschtappern

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import libs.kotlinx.KotlinxJson
import models.Fagsystem
import models.StatusReply
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
        val daos = Daos.messages(Channel.all(), fom, tom)

        return Dashboard(
            feiletUtbetalinger = daos.filter { it.status == "FEILET" },
            pendingMismatch = PendingMismatchService.detectMismatches(fom),
            avstemming = avstemming(fom, tom),
            oppdragUtenKvittering = Daos.findOppdragWithMissingStatus(fom, tom),
            dobbeltutbetalinger = dobbeltutbetalinger(daos)
        )
    }

    private fun dobbeltutbetalinger(daos: List<Daos>): List<Dashboard.Suspects> {
        val suspects: MutableMap<String, Dashboard.Suspects> = mutableMapOf()
        val statuses =
            daos.filter { it.topic_name == Channel.Status.topic.name && it.status == "OK" && it.value != null }

        for (suspect in statuses) {
            val status: StatusReply = KotlinxJson.decodeFromString(suspect.value!!)
            val lines = status.detaljer?.linjer ?: emptyList()

            for (line in lines.filter { it.beløp > 0u }) {
                val key = "${line.behandlingId}::${line.klassekode}::${line.fom}::${line.tom}"
                val suspectGroup = suspects.computeIfAbsent(key) {
                    Dashboard.Suspects(
                        behandlingId = line.behandlingId,
                        klassekode = line.klassekode,
                        fom = line.fom,
                        tom = line.tom,
                        beløp = line.beløp,
                        kilder = mutableMapOf(),
                    )
                }

                suspectGroup.kilder["${suspect.key}::${suspect.partition}::${suspect.offset}"] = Dashboard.Suspects.Kilde(
                    key = suspect.key,
                    partition = suspect.partition,
                    offset = suspect.offset,
                    timestampMs = suspect.system_time_ms,
                )

                suspects[key] = suspectGroup
            }
        }

        return suspects.values.toList().filter { it.kilder.size > 1 }
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
    val feiletUtbetalinger: List<Daos>,
    val pendingMismatch: List<PendingMismatch>,
    val avstemming: List<Avstemming>,
    val oppdragUtenKvittering: List<Daos>,
    val dobbeltutbetalinger: List<Suspects> = emptyList(),
) {
    @Serializable
    data class Avstemming(
        val fagsystem: Fagsystem,
        val sisteAvstemtDato: LocalDate? = null,
        val datoAvstemtFom: LocalDate? = null,
        val datoAvstemtTom: LocalDate? = null,
    )

    @Serializable
    data class Suspects(
        val behandlingId: String,
        val klassekode: String,
        val fom: LocalDate,
        val tom: LocalDate,
        val beløp: UInt,
        val kilder: MutableMap<String, Kilde>,
    ) {
        @Serializable
        data class Kilde(
            val key: String,
            val partition: Int,
            val offset: Long,
            val timestampMs: Long
        )
    }
}

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
