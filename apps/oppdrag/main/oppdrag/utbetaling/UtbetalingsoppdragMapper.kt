package oppdrag.utbetaling

import UUIDEncoder.encode
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.trygdeetaten.skjema.oppdrag.ObjectFactory
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import no.trygdeetaten.skjema.oppdrag.Oppdrag110
import no.trygdeetaten.skjema.oppdrag.OppdragsLinje150
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import oppdrag.iverksetting.domene.Endringskode
import oppdrag.iverksetting.domene.Kvitteringstatus
import oppdrag.iverksetting.domene.OppdragSkjemaConstants
import oppdrag.iverksetting.domene.Utbetalingsfrekvens
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.GregorianCalendar
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar

internal object UtbetalingsoppdragMapper {
    private val objectFactory = ObjectFactory()
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")

    fun tilOppdrag110(utbetalingsoppdrag: UtbetalingsoppdragDto): Oppdrag110 =
        objectFactory.createOppdrag110().apply {
            kodeAksjon = OppdragSkjemaConstants.KODE_AKSJON
            kodeEndring = if (utbetalingsoppdrag.erFørsteUtbetalingPåSak) Endringskode.NY.kode else Endringskode.ENDRING.kode
            kodeFagomraade = utbetalingsoppdrag.fagsystem.kode
            fagsystemId = utbetalingsoppdrag.saksnummer
            utbetFrekvens = Utbetalingsfrekvens.MÅNEDLIG.kode
            oppdragGjelderId = utbetalingsoppdrag.aktør
            datoOppdragGjelderFom = OppdragSkjemaConstants.OPPDRAG_GJELDER_DATO_FOM.toXMLDate()
            saksbehId = utbetalingsoppdrag.saksbehandlerId
            avstemming115 =
                objectFactory.createAvstemming115().apply {
                    nokkelAvstemming = utbetalingsoppdrag.avstemmingstidspunkt.format(timeFormatter)
                    kodeKomponent = utbetalingsoppdrag.fagsystem.kode
                    tidspktMelding = utbetalingsoppdrag.avstemmingstidspunkt.format(timeFormatter)
                }
            tilOppdragsEnhet120(utbetalingsoppdrag).map { oppdragsEnhet120s.add(it) }
            utbetalingsoppdrag.utbetalingsperioder.map { periode ->
                oppdragsLinje150s.add(
                    tilOppdragsLinje150(
                        utbetalingsperiode = periode,
                        utbetalingsoppdrag = utbetalingsoppdrag,
                    ),
                )
            }
        }

    private fun tilOppdragsEnhet120(utbetalingsoppdrag: UtbetalingsoppdragDto) =
        if (utbetalingsoppdrag.brukersNavKontor == null) {
            listOf(
                objectFactory.createOppdragsEnhet120().apply {
                    enhet = OppdragSkjemaConstants.ENHET
                    typeEnhet = OppdragSkjemaConstants.ENHET_TYPE_BOSTEDSENHET
                    datoEnhetFom = OppdragSkjemaConstants.ENHET_FOM.toXMLDate()
                },
            )
        } else {
            listOf(
                objectFactory.createOppdragsEnhet120().apply {
                    enhet = utbetalingsoppdrag.brukersNavKontor
                    typeEnhet = OppdragSkjemaConstants.ENHET_TYPE_BOSTEDSENHET
                    datoEnhetFom = OppdragSkjemaConstants.BRUKERS_NAVKONTOR_FOM.toXMLDate()
                },
                objectFactory.createOppdragsEnhet120().apply {
                    enhet = OppdragSkjemaConstants.ENHET
                    typeEnhet = OppdragSkjemaConstants.ENHET_TYPE_BEHANDLENDE_ENHET
                    datoEnhetFom = OppdragSkjemaConstants.ENHET_FOM.toXMLDate()
                },
            )
        }

    private fun tilOppdragsLinje150(
        utbetalingsperiode: UtbetalingsperiodeDto,
        utbetalingsoppdrag: UtbetalingsoppdragDto,
    ): OppdragsLinje150 {
        val attestant =
            objectFactory.createAttestant180().apply {
                attestantId = utbetalingsoppdrag.beslutterId
            }

        return objectFactory.createOppdragsLinje150().apply {
            kodeEndringLinje = if (utbetalingsperiode.erEndringPåEksisterendePeriode) Endringskode.ENDRING.kode else Endringskode.NY.kode
            utbetalingsperiode.opphør?.let {
                kodeStatusLinje = TkodeStatusLinje.OPPH
                datoStatusFom = it.fom.toXMLDate()
            }
            if (!utbetalingsperiode.erEndringPåEksisterendePeriode) {
                utbetalingsperiode.forrigePeriodeId?.let {
                    refDelytelseId = it
                    refFagsystemId = utbetalingsoppdrag.saksnummer
                }
            }
            vedtakId = utbetalingsperiode.vedtaksdato.toString()
            delytelseId = utbetalingsperiode.id
            kodeKlassifik = utbetalingsperiode.klassekode
            datoVedtakFom = utbetalingsperiode.fom.toXMLDate()
            datoVedtakTom = utbetalingsperiode.tom.toXMLDate()
            sats = BigDecimal.valueOf(utbetalingsperiode.sats.toLong())
            fradragTillegg = OppdragSkjemaConstants.FRADRAG_TILLEGG
            typeSats = utbetalingsperiode.satstype.value
            brukKjoreplan = OppdragSkjemaConstants.BRUK_KJØREPLAN_DEFAULT
            saksbehId = utbetalingsoppdrag.saksbehandlerId
            utbetalesTilId = utbetalingsperiode.utbetalesTil
            henvisning = utbetalingsperiode.behandlingId
            attestant180s.add(attestant)

            utbetalingsperiode.fastsattDagsats?.let { fastsattDagsats ->
                vedtakssats157 = objectFactory.createVedtakssats157().apply {
                    vedtakssats = BigDecimal.valueOf(fastsattDagsats.toLong())
                }
            }
        }
    }

    fun tilOppdrag(oppdrag110: Oppdrag110): Oppdrag =
        objectFactory.createOppdrag().apply {
            this.oppdrag110 = oppdrag110
        }
}

val Oppdrag.kvitteringstatus: Kvitteringstatus
    get() = when (mmel?.alvorlighetsgrad) {
        "00" -> Kvitteringstatus.OK
        "04" -> Kvitteringstatus.AKSEPTERT_MEN_NOE_ER_FEIL
        "08" -> Kvitteringstatus.AVVIST_FUNKSJONELLE_FEIL
        "12" -> Kvitteringstatus.AVVIST_TEKNISK_FEIL
        else -> Kvitteringstatus.UKJENT
    }

val Oppdrag.status: OppdragStatus
    get() = when (kvitteringstatus) {
        Kvitteringstatus.OK -> OppdragStatus.KVITTERT_OK
        Kvitteringstatus.AKSEPTERT_MEN_NOE_ER_FEIL -> OppdragStatus.KVITTERT_MED_MANGLER
        Kvitteringstatus.AVVIST_FUNKSJONELLE_FEIL -> OppdragStatus.KVITTERT_FUNKSJONELL_FEIL
        Kvitteringstatus.AVVIST_TEKNISK_FEIL -> OppdragStatus.KVITTERT_TEKNISK_FEIL
        Kvitteringstatus.UKJENT -> OppdragStatus.KVITTERT_UKJENT
    }

internal fun LocalDate.toXMLDate(): XMLGregorianCalendar =
    DatatypeFactory.newInstance()
        .newXMLGregorianCalendar(GregorianCalendar.from(atStartOfDay(ZoneId.systemDefault())))
