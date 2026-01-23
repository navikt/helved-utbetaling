package urskog.avstemming

import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import models.*
import java.util.UUID

private val objectFactory = ObjectFactory()

private fun LocalDateTime.format(pattern: String) = format(DateTimeFormatter.ofPattern(pattern))

val Int.okt: String get() = LocalDate.of(2025, 11, this)
    .atStartOfDay()
    .format("yyyy-MM-dd-HH.mm.ss.SSSSSS")

fun avstemming(): List<Avstemmingsdata> {
    return listOf(
        avstemmingsdata(AksjonType.START),
        avstemmingsdata(AksjonType.DATA),
        avstemmingsdata(AksjonType.AVSL),
    )
}

private fun avstemmingsdata(type: AksjonType? = null) = Avstemmingsdata().apply {
    aksjon = Aksjonsdata().apply {
        this.aksjonType = type
        this.kildeType = KildeType.AVLEV
        this.avstemmingType = AvstemmingType.GRSN
        this.avleverendeKomponentKode = Fagsystem.DAGPENGER.fagområde
        this.mottakendeKomponentKode = "OS"
        this.underkomponentKode = Fagsystem.DAGPENGER.fagområde
        this.nokkelFom = 1.okt
        this.nokkelTom = 10.okt
        this.avleverendeAvstemmingId = UUID.randomUUID().toString()
        this.brukerId = Fagsystem.DAGPENGER.fagområde
    }
}
