package urskog

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.GregorianCalendar
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar
import no.trygdeetaten.skjema.oppdrag.*

object TestData {
    private val objectFactory = ObjectFactory()
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")

    fun oppdrag(
        oppdragslinjer: List<OppdragsLinje150>,
        kodeEndring: String = "NY", // NY/ENDR
        fagområde: String = "AAP", 
        fagsystemId: String = "1", // sakid
        oppdragGjelderId: String = "12345678910", // personident 
        saksbehId: String = "Z999999",
        avstemmingstidspunkt: LocalDateTime = LocalDateTime.now(),
        enhet: String? = null, // betalende enhet (lokalkontor)
    ): Oppdrag {
        return objectFactory.createOppdrag().apply {
            this.oppdrag110 = objectFactory.createOppdrag110().apply {
                this.kodeAksjon = "1"
                this.kodeEndring = kodeEndring
                this.kodeFagomraade = fagområde
                this.fagsystemId = fagsystemId
                this.utbetFrekvens = "MND"
                this.oppdragGjelderId = oppdragGjelderId
                this.datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).toXMLDate()
                this.saksbehId = saksbehId
                this.avstemming115 = objectFactory.createAvstemming115().apply {
                    val avstemmingstidspunkt = avstemmingstidspunkt.truncatedTo(ChronoUnit.HOURS).format(timeFormatter)
                    nokkelAvstemming = avstemmingstidspunkt
                    kodeKomponent = fagområde
                    tidspktMelding = avstemmingstidspunkt
                }
                oppdragsEnhet120(enhet).forEach(oppdragsEnhet120s::add)
                oppdragsLinje150s.addAll(oppdragslinjer)
            }
        }
    }

    fun oppdragslinje(
        delytelsesId: String,
        sats: Long,
        datoVedtakFom: LocalDate,
        datoVedtakTom: LocalDate,
        typeSats: String, // DAG/DAG7/MND/ENG
        henvisning: String, // behandlingId
        refDelytelsesId: String? = null,
        kodeEndring: String = "NY", // NY/ENDR
        opphør: LocalDate? = null,
        fagsystemId: String = "1", // sakid
        vedtakId: LocalDate = LocalDate.now(), // vedtakstidspunkt
        klassekode: String = "AAPOR",
        saksbehId: String = "Z999999", // saksbehandler
        beslutterId: String = "Z999999", // beslutter
        utbetalesTilId: String = "12345678910", // personident
        vedtakssats: Long? = null, // fastsattDagsats
    ): OppdragsLinje150 {
        val attestant = objectFactory.createAttestant180().apply {
            attestantId = beslutterId
        }

        return objectFactory.createOppdragsLinje150().apply {
            kodeEndringLinje = kodeEndring
            opphør?.let {
                kodeStatusLinje = TkodeStatusLinje.OPPH
                datoStatusFom = opphør.toXMLDate()
            }
            if (kodeEndring == "ENDR") {
                refDelytelsesId?.let {
                    refDelytelseId = refDelytelsesId
                    refFagsystemId = fagsystemId
                }
            }
            this.vedtakId = vedtakId.toString()
            this.delytelseId = delytelsesId
            this.kodeKlassifik = klassekode
            this.datoVedtakFom = datoVedtakFom.toXMLDate()
            this.datoVedtakTom = datoVedtakTom.toXMLDate()
            this.sats = BigDecimal.valueOf(sats)
            this.fradragTillegg = TfradragTillegg.T
            this.typeSats = typeSats
            this.brukKjoreplan = "N"
            this.saksbehId = saksbehId
            this.utbetalesTilId = utbetalesTilId
            this.henvisning = henvisning
            attestant180s.add(attestant)

            vedtakssats?.let {
                vedtakssats157 = objectFactory.createVedtakssats157().apply {
                    this.vedtakssats = BigDecimal.valueOf(vedtakssats)
                }
            }
        }
    }

    fun oppdragsEnhet120(enhet: String? = null): List<OppdragsEnhet120> {
        return enhet?.let {
            listOf(
                objectFactory.createOppdragsEnhet120().apply {
                    this.enhet = enhet
                    this.typeEnhet = "BOS"
                    this.datoEnhetFom = LocalDate.of(1970, 1, 1).toXMLDate()
                },
                objectFactory.createOppdragsEnhet120().apply {
                    this.enhet = "8020"
                    this.typeEnhet = "BEH"
                    this.datoEnhetFom = LocalDate.of(1900, 1, 1).toXMLDate()
                },
            )
        } ?: listOf(
            objectFactory.createOppdragsEnhet120().apply {
                this.enhet = "8020"
                this.typeEnhet = "BOS"
                this.datoEnhetFom = LocalDate.of(1900, 1, 1).toXMLDate()
            },
        )
    }
}

private fun LocalDate.toXMLDate(): XMLGregorianCalendar =
    DatatypeFactory.newInstance()
        .newXMLGregorianCalendar(GregorianCalendar.from(atStartOfDay(ZoneId.systemDefault())))
