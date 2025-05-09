package utsjekk.iverksetting.abetal

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar
import kotlinx.coroutines.runBlocking
import libs.postgres.concurrency.transaction
import libs.utils.*
import models.PeriodeId
import models.nesteVirkedag
import models.kontrakter.felles.BrukersNavKontor
import models.kontrakter.felles.Satstype
import models.kontrakter.oppdrag.OppdragStatus
import models.kontrakter.oppdrag.Utbetalingsoppdrag
import models.kontrakter.oppdrag.Utbetalingsperiode
import no.trygdeetaten.skjema.oppdrag.*
import utsjekk.iverksetting.*
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import utsjekk.iverksetting.resultat.IverksettingResultater
import utsjekk.iverksetting.utbetalingsoppdrag.Utbetalingsgenerator

private val objectFactory = ObjectFactory()
private fun LocalDateTime.format() = truncatedTo(ChronoUnit.HOURS).format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS"))
private fun LocalDate.toXMLDate(): XMLGregorianCalendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(atStartOfDay(ZoneId.systemDefault())))

object OppdragService {
    suspend fun create(iverksetting: Iverksetting): Oppdrag? {
        val forrigeResultat = iverksetting.behandling.forrigeBehandlingId?.let {
            IverksettingResultater.hentForrige(iverksetting)
        }
        val beregnetUtbetalingsoppdrag = utbetalingsoppdrag(iverksetting, forrigeResultat)

        IverksettingResultater.oppdater(
            iverksetting = iverksetting,
            resultat = OppdragResultat(OppdragStatus.LAGT_PÅ_KØ),
        )
        val tilkjentYtelse = oppdaterTilkjentYtelse(iverksetting.vedtak.tilkjentYtelse, beregnetUtbetalingsoppdrag, forrigeResultat, iverksetting)
        val oppdrag = tilkjentYtelse.utbetalingsoppdrag?.let { utbetalingsoppdrag ->
            if (utbetalingsoppdrag.utbetalingsperiode.isNotEmpty()) {
                oppdrag(utbetalingsoppdrag)
            } else {
                appLog.warn("Iverksetter ikke noe mot oppdrag. Ingen utbetalingsperioder i utbetalingsoppdraget.")
                null
            }
        }
        return oppdrag
    }

    private suspend fun oppdaterTilkjentYtelse(
        tilkjentYtelse: TilkjentYtelse,
        beregnetUtbetalingsoppdrag: BeregnetUtbetalingsoppdrag,
        forrigeResultat: IverksettingResultatDao?,
        iverksetting: Iverksetting,
    ): TilkjentYtelse {
        val nyeAndelerMedPeriodeId = tilkjentYtelse.andelerTilkjentYtelse.map { andel ->
            val andelData = andel.tilAndelData()
            val andelDataMedPeriodeId = beregnetUtbetalingsoppdrag.andeler.find { a -> andelData.id == a.id } ?: throw IllegalStateException("Fant ikke andel med id ${andelData.id}")
            andel.copy(
                periodeId = andelDataMedPeriodeId.periodeId,
                forrigePeriodeId = andelDataMedPeriodeId.forrigePeriodeId,
            )
        }
        val nyTilkjentYtelse = tilkjentYtelse.copy(
            andelerTilkjentYtelse = nyeAndelerMedPeriodeId,
            utbetalingsoppdrag = beregnetUtbetalingsoppdrag.utbetalingsoppdrag,
        )
        val forrigeSisteAndelPerKjede = forrigeResultat?.tilkjentYtelseForUtbetaling?.sisteAndelPerKjede ?: emptyMap()
        val nyTilkjentYtelseMedSisteAndelIKjede = lagTilkjentYtelseMedSisteAndelPerKjede(nyTilkjentYtelse, forrigeSisteAndelPerKjede)
        transaction {
            IverksettingResultater.oppdater(iverksetting, nyTilkjentYtelseMedSisteAndelIKjede)
        }
        return nyTilkjentYtelseMedSisteAndelIKjede
    }

    private fun lagTilkjentYtelseMedSisteAndelPerKjede(
        tilkjentYtelse: TilkjentYtelse,
        forrigeSisteAndelPerKjede: Map<Kjedenøkkel, AndelTilkjentYtelse>,
    ): TilkjentYtelse {
        val beregnetSisteAndePerKjede = tilkjentYtelse.andelerTilkjentYtelse
            .groupBy { it.stønadsdata.tilKjedenøkkel() }
            .mapValues { it.value.maxBy { andel -> andel.periodeId!! } }
        val nySisteAndelerPerKjede: Map<Kjedenøkkel, AndelTilkjentYtelse> = finnSisteAndelPerKjede(beregnetSisteAndePerKjede, forrigeSisteAndelPerKjede)
        return tilkjentYtelse.copy(sisteAndelPerKjede = nySisteAndelerPerKjede)
    }

    /**
     * Finner riktig siste andel per kjede av andeler
     * Funksjonen lager en map med kjedenøkkel som key og en liste med de to andelene fra hver map
     * Deretter finner vi hvilke av de to vi skal bruke, Regelen er
     * 1. Bruk den med største periodeId
     * 2. Hvis periodeIdene er like, bruk den med størst til-og-med-dato
     */
    private fun finnSisteAndelPerKjede(
        nySisteAndePerKjede: Map<Kjedenøkkel, AndelTilkjentYtelse>,
        forrigeSisteAndelPerKjede: Map<Kjedenøkkel, AndelTilkjentYtelse>,
    ) = (nySisteAndePerKjede.asSequence() + forrigeSisteAndelPerKjede.asSequence())
        .groupBy({ it.key }, { it.value })
        .mapValues { entry -> entry.value.sortedWith(compareByDescending<AndelTilkjentYtelse> { it.periodeId }.thenByDescending { it.periode.tom }).first() }

    private fun utbetalingsoppdrag(
        iverksetting: Iverksetting,
        forrigeResultat: IverksettingResultatDao?,
    ): BeregnetUtbetalingsoppdrag {
        val info = Behandlingsinformasjon(
            saksbehandlerId = iverksetting.vedtak.saksbehandlerId,
            beslutterId = iverksetting.vedtak.beslutterId,
            fagsystem = iverksetting.fagsak.fagsystem,
            fagsakId = iverksetting.sakId,
            behandlingId = iverksetting.behandlingId,
            personident = iverksetting.personident,
            brukersNavKontor = iverksetting.vedtak.tilkjentYtelse.andelerTilkjentYtelse.finnBrukersNavKontor(),
            vedtaksdato = iverksetting.vedtak.vedtakstidspunkt.toLocalDate(),
            iverksettingId = iverksetting.behandling.iverksettingId,
        )
        val nyeAndeler = iverksetting.vedtak.tilkjentYtelse.lagAndelData()
        val forrigeAndeler = forrigeResultat?.tilkjentYtelseForUtbetaling.lagAndelData()
        val sisteAndelPerKjede = forrigeResultat
            ?.tilkjentYtelseForUtbetaling
            ?.sisteAndelPerKjede
            ?.mapValues { it.value.tilAndelData() }
            ?: emptyMap()
        return Utbetalingsgenerator.lagUtbetalingsoppdrag(
            behandlingsinformasjon = info,
            nyeAndeler = nyeAndeler,
            forrigeAndeler = forrigeAndeler,
            sisteAndelPerKjede = sisteAndelPerKjede,
        )
    }

    private fun List<AndelTilkjentYtelse>.finnBrukersNavKontor(): BrukersNavKontor? =
        sortedByDescending { it.periode.fom }.firstNotNullOfOrNull {
            when (it.stønadsdata) {
                is StønadsdataTilleggsstønader -> it.stønadsdata.brukersNavKontor
                is StønadsdataTiltakspenger -> it.stønadsdata.brukersNavKontor
                else -> null
            }
        }
}

private fun oppdrag(utbetalingsoppdrag: Utbetalingsoppdrag): Oppdrag {
    val oppdrag110 = objectFactory.createOppdrag110().apply {
        kodeAksjon = "1"
        kodeEndring = if (utbetalingsoppdrag.erFørsteUtbetalingPåSak) "NY" else "ENDR"
        kodeFagomraade = utbetalingsoppdrag.fagsystem.kode
        fagsystemId = utbetalingsoppdrag.saksnummer
        utbetFrekvens = "MND"
        oppdragGjelderId = utbetalingsoppdrag.aktør
        datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).toXMLDate()
        saksbehId = utbetalingsoppdrag.saksbehandlerId
        avstemming115 = objectFactory.createAvstemming115().apply {
            kodeKomponent = utbetalingsoppdrag.fagsystem.kode
            nokkelAvstemming = LocalDate.now().nesteVirkedag().atStartOfDay().format()
            tidspktMelding = LocalDate.now().nesteVirkedag().atStartOfDay().format()
        }
        oppdragsEnhet120(utbetalingsoppdrag).map { oppdragsEnhet120s.add(it) }
        utbetalingsoppdrag.utbetalingsperiode.map { periode ->
            oppdragsLinje150s.add(
                oppdragsLinje150(
                    utbetalingsperiode = periode,
                    utbetalingsoppdrag = utbetalingsoppdrag,
                ),
            )
        }
    }

    return objectFactory.createOppdrag().apply {
        this.oppdrag110 = oppdrag110
    }
}


private fun oppdragsEnhet120(utbetalingsoppdrag: Utbetalingsoppdrag): List<OppdragsEnhet120> {
    val bos = objectFactory.createOppdragsEnhet120().apply {
        enhet = utbetalingsoppdrag.brukersNavKontor ?: "8020"
        typeEnhet = "BOS"
        datoEnhetFom = LocalDate.of(1970, 1, 1).toXMLDate()
    }
    val beh = objectFactory.createOppdragsEnhet120().apply {
        enhet = "8020"
        typeEnhet = "BEH"
        datoEnhetFom = LocalDate.of(1970, 1, 1).toXMLDate()
    }
    return when (utbetalingsoppdrag.brukersNavKontor) {
        null -> listOf(bos)
        else -> listOf(bos, beh)
    }
}

private fun oppdragsLinje150(
    utbetalingsperiode: Utbetalingsperiode,
    utbetalingsoppdrag: Utbetalingsoppdrag,
): OppdragsLinje150 {
    val sakIdKomprimert = utbetalingsoppdrag.saksnummer

    val attestant =
        objectFactory.createAttestant180().apply {
            attestantId = utbetalingsoppdrag.beslutterId ?: utbetalingsoppdrag.saksbehandlerId
        }

    return objectFactory.createOppdragsLinje150().apply {
        kodeEndringLinje = if (utbetalingsperiode.erEndringPåEksisterendePeriode) "ENDR" else "NY"
        utbetalingsperiode.opphør?.let {
            kodeStatusLinje = TkodeStatusLinje.OPPH
            datoStatusFom = it.fom.toXMLDate()
        }
        if (!utbetalingsperiode.erEndringPåEksisterendePeriode) {
            utbetalingsperiode.forrigePeriodeId?.let {
                refDelytelseId = "$sakIdKomprimert#$it"
                refFagsystemId = sakIdKomprimert
            }
        }
        vedtakId = utbetalingsperiode.vedtaksdato.toString()
        delytelseId = "$sakIdKomprimert#${utbetalingsperiode.periodeId}"
        kodeKlassifik = utbetalingsperiode.klassifisering
        datoVedtakFom = utbetalingsperiode.fom.toXMLDate()
        datoVedtakTom = utbetalingsperiode.tom.toXMLDate()
        sats = utbetalingsperiode.sats
        fradragTillegg = TfradragTillegg.T
        typeSats = typeSats(utbetalingsperiode.satstype)
        brukKjoreplan = "N"
        saksbehId = utbetalingsoppdrag.saksbehandlerId
        utbetalesTilId = utbetalingsperiode.utbetalesTil
        henvisning = utbetalingsperiode.behandlingId
        attestant180s.add(attestant)

        utbetalingsperiode.utbetalingsgrad?.let { utbetalingsgrad ->
            grad170s.add(
                objectFactory.createGrad170().apply {
                    typeGrad = "UBGR"// Gradtype.UTBETALINGSGRAD.kode
                    grad = utbetalingsgrad.toBigInteger()
                },
            )
        }
        utbetalingsperiode.fastsattDagsats?.let { fastsattDagsats ->
            vedtakssats157 = objectFactory.createVedtakssats157().apply {
                vedtakssats = fastsattDagsats
            }
        }
    }
}

private fun typeSats(satstype: Satstype): String = when(satstype) {
    Satstype.DAGLIG -> "DAG"
    Satstype.DAGLIG_INKL_HELG -> "DAG7"
    Satstype.MÅNEDLIG -> "MND"
    Satstype.ENGANGS -> "ENG"
}

