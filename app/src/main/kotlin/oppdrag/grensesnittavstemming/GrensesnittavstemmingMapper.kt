package oppdrag.grensesnittavstemming

import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.AksjonType
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Aksjonsdata
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.AvstemmingType
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.DetaljType
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Detaljdata
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Fortegn
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Grunnlagsdata
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.KildeType
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Periodedata
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Totaldata
import oppdrag.iverksetting.tilstand.OppdragLager
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class GrensesnittavstemmingMapper(
    private val oppdragsliste: List<OppdragLager>,
    private val fagsystem: Fagsystem,
    private val fom: LocalDateTime,
    private val tom: LocalDateTime,
) {
    val avstemmingId = getAvstemmingId()

    private val datameldinger
        get(): List<Avstemmingsdata> {
            val avstemmingsDataLister = avstemmingsdataliste.ifEmpty { listOf(lagMelding(AksjonType.DATA)) }

            avstemmingsDataLister.first().apply {
                total = totaldata
                periode = periodedata
                grunnlag = grunnlagsdata
            }

            return avstemmingsDataLister
        }

    private val avstemmingsdataliste
        get() =
            detaljdataliste.chunked(ANTALL_DETALJER_PR_MELDING).map {
                lagMelding(AksjonType.DATA).apply {
                    this.detalj.addAll(it)
                }
            }

    private val detaljdataliste
        get() =
            oppdragsliste.mapNotNull { oppdrag ->
                val detaljtype = oppdrag.status.tilDetaljType()
                if (detaljtype != null) {
                    val utbetalingsoppdrag = oppdrag.utbetalingsoppdrag

                    Detaljdata().apply {
                        detaljType = detaljtype
                        offnr = utbetalingsoppdrag.aktør
                        avleverendeTransaksjonNokkel = utbetalingsoppdrag.saksnummer
                        tidspunkt = oppdrag.avstemming_tidspunkt.format(timeFormatter)

                        if (detaljtype in
                            listOf(
                                DetaljType.AVVI,
                                DetaljType.VARS,
                            ) && oppdrag.kvitteringsmelding != null
                        ) {
                            val kvitteringsmelding = oppdrag.kvitteringsmelding

                            meldingKode = kvitteringsmelding.kodeMelding
                            alvorlighetsgrad = kvitteringsmelding.alvorlighetsgrad
                            tekstMelding = kvitteringsmelding.beskrMelding
                        }
                    }
                } else {
                    null
                }
            }

    private val høyesteAvstemmingstidspunkt get() = sortertAvstemmingstidspunkt.first()

    private val lavesteAvstemmingstidspunkt get() = sortertAvstemmingstidspunkt.last()

    private val sortertAvstemmingstidspunkt
        get() = oppdragsliste.map(OppdragLager::avstemming_tidspunkt).sortedDescending()

    fun lagAvstemmingsmeldinger(): List<Avstemmingsdata> {
        val startmelding = lagMelding(AksjonType.START)
        val sluttmelding = lagMelding(AksjonType.AVSL)

        return if (oppdragsliste.isEmpty()) {
            emptyList()
        } else {
            (listOf(startmelding) + datameldinger + listOf(sluttmelding))
        }
    }

    private fun lagMelding(aksjonType: AksjonType) =
        Avstemmingsdata().apply {
            aksjon =
                Aksjonsdata().apply {
                    this.aksjonType = aksjonType
                    kildeType = KildeType.AVLEV
                    avstemmingType = AvstemmingType.GRSN
                    avleverendeKomponentKode = fagsystem.kode
                    mottakendeKomponentKode = "OS"
                    underkomponentKode = fagsystem.kode
                    nokkelFom = fom.format(timeFormatter)
                    nokkelTom = tom.format(timeFormatter)
                    avleverendeAvstemmingId = avstemmingId
                    brukerId = fagsystem.kode
                }
        }

    private fun OppdragStatus.tilDetaljType() =
        when (this) {
            OppdragStatus.LAGT_PÅ_KØ -> DetaljType.MANG
            OppdragStatus.KVITTERT_MED_MANGLER -> DetaljType.VARS
            OppdragStatus.KVITTERT_FUNKSJONELL_FEIL, OppdragStatus.KVITTERT_TEKNISK_FEIL -> DetaljType.AVVI
            OppdragStatus.KVITTERT_OK, OppdragStatus.KVITTERT_UKJENT, OppdragStatus.OK_UTEN_UTBETALING -> null
        }

    private val totaldata
        get(): Totaldata =
            Totaldata().apply {
                val totalBeløp = oppdragsliste.sumOf { getSatsBeløp(it) }
                this.totalAntall = oppdragsliste.size
                this.totalBelop = BigDecimal.valueOf(totalBeløp)
                this.fortegn = getFortegn(totalBeløp)
            }

    private val periodedata
        get() =
            Periodedata().apply {
                this.datoAvstemtFom =
                    formaterTilPeriodedataFormat(lavesteAvstemmingstidspunkt.format(timeFormatter))
                this.datoAvstemtTom =
                    formaterTilPeriodedataFormat(høyesteAvstemmingstidspunkt.format(timeFormatter))
            }

    private val grunnlagsdata
        get(): Grunnlagsdata {
            var godkjentAntall = 0
            var godkjentBelop = 0L
            var varselAntall = 0
            var varselBelop = 0L
            var avvistAntall = 0
            var avvistBelop = 0L
            var manglerAntall = 0
            var manglerBelop = 0L

            for (oppdrag in oppdragsliste) {
                val satsbeløp = getSatsBeløp(oppdrag)
                when (oppdrag.status) {
                    OppdragStatus.LAGT_PÅ_KØ -> {
                        manglerBelop += satsbeløp
                        manglerAntall++
                    }

                    OppdragStatus.KVITTERT_OK -> {
                        godkjentBelop += satsbeløp
                        godkjentAntall++
                    }

                    OppdragStatus.KVITTERT_MED_MANGLER -> {
                        varselBelop += satsbeløp
                        varselAntall++
                    }

                    else -> {
                        avvistBelop += satsbeløp
                        avvistAntall++
                    }
                }
            }

            return Grunnlagsdata().apply {
                this.godkjentAntall = godkjentAntall
                this.godkjentBelop = BigDecimal.valueOf(godkjentBelop)
                this.godkjentFortegn = getFortegn(godkjentBelop)

                this.varselAntall = varselAntall
                this.varselBelop = BigDecimal.valueOf(varselBelop)
                this.varselFortegn = getFortegn(varselBelop)

                this.manglerAntall = manglerAntall
                this.manglerBelop = BigDecimal.valueOf(manglerBelop)
                this.manglerFortegn = getFortegn(manglerBelop)

                this.avvistAntall = avvistAntall
                this.avvistBelop = BigDecimal.valueOf(avvistBelop)
                this.avvistFortegn = getFortegn(avvistBelop)
            }
        }

    private fun getSatsBeløp(oppdrag: OppdragLager) =
        oppdrag.utbetalingsoppdrag.utbetalingsperiode.map { it.sats }.reduce(BigDecimal::add).toLong()

    private fun getFortegn(satsbeløp: Long) = if (satsbeløp >= 0) Fortegn.T else Fortegn.F

    private fun formaterTilPeriodedataFormat(stringTimestamp: String) =
        LocalDateTime.parse(stringTimestamp, timeFormatter)
            .format(DateTimeFormatter.ofPattern("yyyyMMddHH"))

    companion object {
        private const val ANTALL_DETALJER_PR_MELDING = 70
        private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")

        private fun getAvstemmingId(): String {
            val uuid = UUID.randomUUID()
            val byteBuffer =
                ByteBuffer.wrap(ByteArray(16)).apply {
                    putLong(uuid.mostSignificantBits)
                    putLong(uuid.leastSignificantBits)
                }

            return Base64.getUrlEncoder().encodeToString(byteBuffer.array()).substring(0, 22)
        }
    }
}
