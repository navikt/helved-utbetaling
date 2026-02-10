package vedskiva

import models.Avstemming
import models.AvstemmingId
import models.Kvittering
import models.Oppdragsdata
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.*
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

private const val CHUNK_SIZE = 70

object AvstemmingFactory {
    fun create(avstemming: Avstemming): List<Avstemmingsdata> {
        if (avstemming.oppdragsdata.isEmpty()) return emptyList()
        val start = listOf(avstemmingsdata(AksjonType.START, avstemming))
        val chunks = avstemmingsdatas(avstemming)
        val end = listOf(avstemmingsdata(AksjonType.AVSL, avstemming))
        return start + chunks + end
    }

    private fun avstemmingsdata(type: AksjonType, avstemming: Avstemming) = Avstemmingsdata().apply {
        aksjon = Aksjonsdata().apply {
            this.aksjonType = type
            this.kildeType = KildeType.AVLEV
            this.avstemmingType = AvstemmingType.GRSN
            this.avleverendeKomponentKode = avstemming.fagsystem.fagområde
            this.mottakendeKomponentKode = "OS"
            this.underkomponentKode = avstemming.fagsystem.fagområde
            this.nokkelFom = avstemming.fom.format("yyyy-MM-dd-HH.mm.ss.SSSSSS")
            this.nokkelTom = avstemming.tom.format("yyyy-MM-dd-HH.mm.ss.SSSSSS")
            this.avleverendeAvstemmingId = avstemming.id
            this.brukerId = avstemming.fagsystem.fagområde
        }
    }

    fun genererId(): AvstemmingId {
        val uuid = UUID.randomUUID()
        val byteBuffer = ByteBuffer.wrap(ByteArray(16)).apply {
            putLong(uuid.mostSignificantBits)
            putLong(uuid.leastSignificantBits)
        }
        return Base64.getUrlEncoder().encodeToString(byteBuffer.array()).substring(0, 22)
    }

    private fun avstemmingsdatas(avstemming: Avstemming): List<Avstemmingsdata> {
        val avstemmingsdatas = avstemming.oppdragsdata
            .mapNotNull(::detaljdata)
            .chunked(CHUNK_SIZE)
            .map { chunk ->
                avstemmingsdata(AksjonType.DATA, avstemming).apply {
                    this.detaljs.addAll(chunk)
                }
            }
            .ifEmpty {
                listOf(avstemmingsdata(AksjonType.DATA, avstemming))
            }
        avstemmingsdatas.first().apply {
            total = totaldata(avstemming.oppdragsdata)
            periode = periodedata(avstemming)
            grunnlag = grunnlagsdata(avstemming.oppdragsdata)
        }
        return avstemmingsdatas
    }

    private fun detaljdata(data: Oppdragsdata): Detaljdata? {
        val type = detaljType(data.kvittering) ?: return null
        return Detaljdata().apply {
            detaljType = type
            offnr = data.personident.ident
            avleverendeTransaksjonNokkel = data.sakId.id
            tidspunkt = data.innsendt.format("yyyy-MM-dd-HH.mm.ss.SSSSSS")
            if (type in listOf(DetaljType.AVVI, DetaljType.VARS)) {
                val kvittering = data.kvittering ?: return null
                meldingKode = kvittering.kode
                alvorlighetsgrad = kvittering.alvorlighetsgrad
                tekstMelding = kvittering.melding
            }
        }
    }

    private fun totaldata(datas: List<Oppdragsdata>) = Totaldata().apply {
        val totalbeløp = datas.sumOf { it.totalBeløpAllePerioder.toLong() }
        totalAntall = datas.size
        totalBelop = BigDecimal.valueOf(totalbeløp)
        fortegn = if (totalbeløp >= 0) Fortegn.T else Fortegn.F
    }

    private fun periodedata(avstemming: Avstemming) = Periodedata().apply {
        datoAvstemtFom = avstemming.fom.format("yyyyMMddHH")
        datoAvstemtTom = avstemming.tom.format("yyyyMMddHH")
    }

    private fun grunnlagsdata(datas: List<Oppdragsdata>) = Grunnlagsdata().apply {
        val godkjente = datas.filter { it.kvittering?.alvorlighetsgrad == "00" }
        godkjentAntall = godkjente.size
        godkjentBelop = BigDecimal.valueOf(godkjente.sumOf{ it.totalBeløpAllePerioder }.toLong())
        godkjentFortegn = if(godkjentBelop.toLong() >= 0) Fortegn.T else Fortegn.F

        val varsel = datas.filter { it.kvittering?.alvorlighetsgrad == "04" }
        varselAntall = varsel.size
        varselBelop = BigDecimal.valueOf(varsel.sumOf{ it.totalBeløpAllePerioder }.toLong())
        varselFortegn = if(varselBelop.toLong() >= 0) Fortegn.T else Fortegn.F

        val mangler = datas.filter { it.kvittering == null }
        manglerAntall = mangler.size
        manglerBelop = BigDecimal.valueOf(mangler.sumOf { it.totalBeløpAllePerioder }.toLong())
        manglerFortegn = if(manglerBelop.toLong() >= 0) Fortegn.T else Fortegn.F

        val avvist = datas.filter { it.kvittering?.alvorlighetsgrad in listOf("08", "12") }
        avvistAntall = avvist.size
        avvistBelop = BigDecimal.valueOf(avvist.sumOf { it.totalBeløpAllePerioder }.toLong())
        avvistFortegn = if(avvistBelop.toLong() >= 0) Fortegn.T else Fortegn.F
    }

    private fun detaljType(kvittering: Kvittering?): DetaljType? {
        return when(kvittering?.alvorlighetsgrad) {
            "00" -> null
            "04" -> DetaljType.VARS
            "08" -> DetaljType.AVVI
            "12" -> DetaljType.AVVI
            else -> DetaljType.MANG
        }
    }
}

private fun LocalDateTime.format(pattern: String) = format(DateTimeFormatter.ofPattern(pattern))
