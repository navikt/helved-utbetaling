package oppdrag.iverksetting.domene

import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.Satstype
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.nav.utsjekk.kontrakter.oppdrag.Opphør
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsperiode
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import no.trygdeetaten.skjema.oppdrag.Oppdrag110
import no.trygdeetaten.skjema.oppdrag.OppdragsLinje150
import oppdrag.enUtbetalingsperiode
import oppdrag.etUtbetalingsoppdrag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OppdragMapperTest {
    @Test
    fun `mappe vedtak`() {
        val periode1 =
            enUtbetalingsperiode(
                beløp = 1234,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusYears(6),
            )

        val periode2 =
            enUtbetalingsperiode(
                beløp = 2345,
                fom = LocalDate.now().plusMonths(1),
                tom = LocalDate.now().plusYears(6).plusMonths(1),
            )

        val utbetalingsoppdrag =
            etUtbetalingsoppdrag(
                utbetalingsperiode =
                    arrayOf(
                        periode1,
                        periode2,
                    ),
            )

        val oppdrag110 = OppdragMapper.tilOppdrag110(utbetalingsoppdrag)

        assertOppdrag110(utbetalingsoppdrag, oppdrag110)
        assertOppdragslinje150(periode1, utbetalingsoppdrag, oppdrag110.oppdragsLinje150s[0])
        assertOppdragslinje150(periode2, utbetalingsoppdrag, oppdrag110.oppdragsLinje150s[1])
    }

    @Test
    fun `mappe opphør på vedtak`() {
        val periode =
            enUtbetalingsperiode(
                opphør = Opphør(LocalDate.now().plusMonths(1)),
                periodeId = 3,
                forrigePeriodeId = 2,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusYears(2),
            )

        val utbetalingsoppdrag =
            etUtbetalingsoppdrag(
                erFørsteUtbetalingPåSak = false,
                utbetalingsperiode = arrayOf(periode),
            )

        val oppdrag110 = OppdragMapper.tilOppdrag110(utbetalingsoppdrag)

        assertOppdrag110(utbetalingsoppdrag, oppdrag110)
        assertOppdragslinje150(periode, utbetalingsoppdrag, oppdrag110.oppdragsLinje150s[0])
    }

    @Test
    fun `mappe vedtak for tiltakspenger`() {
        val periode =
            enUtbetalingsperiode(
                klassifisering = "TPTPATT",
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(6),
                beløp = 1354,
                satstype = Satstype.DAGLIG_INKL_HELG,
            )
        val utbetalingsoppdrag =
            etUtbetalingsoppdrag(
                fagsystem = Fagsystem.TILTAKSPENGER,
                brukersNavKontor = "0220",
                utbetalingsperiode = arrayOf(periode),
            )

        val oppdrag110 = OppdragMapper.tilOppdrag110(utbetalingsoppdrag)

        assertOppdrag110(utbetalingsoppdrag, oppdrag110)
        assertOppdragslinje150(periode, utbetalingsoppdrag, oppdrag110.oppdragsLinje150s[0])
    }

    @Test
    fun `konverterer status`() {
        assertEquals(OppdragStatus.KVITTERT_OK, oppdragMedAlvorlighetsgrad("00").status)
        assertEquals(OppdragStatus.KVITTERT_MED_MANGLER, oppdragMedAlvorlighetsgrad("04").status)
        assertEquals(OppdragStatus.KVITTERT_FUNKSJONELL_FEIL, oppdragMedAlvorlighetsgrad("08").status)
        assertEquals(OppdragStatus.KVITTERT_TEKNISK_FEIL, oppdragMedAlvorlighetsgrad("12").status)
        assertEquals(OppdragStatus.KVITTERT_UKJENT, oppdragMedAlvorlighetsgrad("Ukjent").status)
    }

    private fun oppdragMedAlvorlighetsgrad(alvorlighetsgrad: String) =
        Oppdrag().apply {
            mmel = Mmel()
            mmel.alvorlighetsgrad = alvorlighetsgrad
        }

    private fun assertOppdrag110(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        oppdrag110: Oppdrag110,
    ) {
        val forventetEndringskode =
            if (utbetalingsoppdrag.erFørsteUtbetalingPåSak) Endringskode.NY else Endringskode.ENDRING
        assertEquals(OppdragSkjemaConstants.KODE_AKSJON, oppdrag110.kodeAksjon)
        assertEquals(forventetEndringskode.kode, oppdrag110.kodeEndring.toString())
        assertEquals(utbetalingsoppdrag.fagsystem.kode, oppdrag110.kodeFagomraade)
        assertEquals(utbetalingsoppdrag.saksnummer, oppdrag110.fagsystemId)
        assertEquals(Utbetalingsfrekvens.MÅNEDLIG.kode, oppdrag110.utbetFrekvens)
        assertEquals(utbetalingsoppdrag.aktør, oppdrag110.oppdragGjelderId)
        assertEquals(OppdragSkjemaConstants.OPPDRAG_GJELDER_DATO_FOM.toXMLDate(), oppdrag110.datoOppdragGjelderFom)
        assertEquals(utbetalingsoppdrag.saksbehandlerId, oppdrag110.saksbehId)
        assertEquals(utbetalingsoppdrag.fagsystem.kode, oppdrag110.avstemming115.kodeKomponent)
        assertEquals(
            utbetalingsoppdrag.avstemmingstidspunkt.format(OppdragMapper.timeFormatter),
            oppdrag110.avstemming115.nokkelAvstemming,
        )
        assertEquals(
            utbetalingsoppdrag.avstemmingstidspunkt.format(OppdragMapper.timeFormatter),
            oppdrag110.avstemming115.tidspktMelding,
        )
        assertEquals(OppdragSkjemaConstants.ENHET_TYPE_BOSTEDSENHET, oppdrag110.oppdragsEnhet120s[0].typeEnhet)
        utbetalingsoppdrag.brukersNavKontor?.let {
            assertEquals(it, oppdrag110.oppdragsEnhet120s[0].enhet)
            assertEquals(
                OppdragSkjemaConstants.BRUKERS_NAVKONTOR_FOM.toXMLDate(),
                oppdrag110.oppdragsEnhet120s[0].datoEnhetFom,
            )
            assertEquals(OppdragSkjemaConstants.ENHET_TYPE_BEHANDLENDE_ENHET, oppdrag110.oppdragsEnhet120s[1].typeEnhet)
            assertEquals(OppdragSkjemaConstants.ENHET, oppdrag110.oppdragsEnhet120s[1].enhet)
            assertEquals(OppdragSkjemaConstants.ENHET_FOM.toXMLDate(), oppdrag110.oppdragsEnhet120s[1].datoEnhetFom)
        }
            ?: run {
                assertEquals(OppdragSkjemaConstants.ENHET, oppdrag110.oppdragsEnhet120s[0].enhet)
                assertEquals(OppdragSkjemaConstants.ENHET_FOM.toXMLDate(), oppdrag110.oppdragsEnhet120s[0].datoEnhetFom)
            }
    }

    private fun assertOppdragslinje150(
        utbetalingsperiode: Utbetalingsperiode,
        utbetalingsoppdrag: Utbetalingsoppdrag,
        oppdragsLinje150: OppdragsLinje150,
    ) {
        assertEquals(
            if (utbetalingsperiode.erEndringPåEksisterendePeriode) {
                Endringskode.ENDRING.kode
            } else {
                Endringskode.NY.kode
            },
            oppdragsLinje150.kodeEndringLinje,
        )
        assertOpphør(utbetalingsperiode, oppdragsLinje150)
        assertEquals(utbetalingsperiode.vedtaksdato.toString(), oppdragsLinje150.vedtakId)
        assertEquals(
            utbetalingsoppdrag.saksnummer + "#" + utbetalingsperiode.periodeId.toString(),
            oppdragsLinje150.delytelseId,
        )
        assertEquals(utbetalingsperiode.klassifisering, oppdragsLinje150.kodeKlassifik)
        assertEquals(utbetalingsperiode.fom.toXMLDate(), oppdragsLinje150.datoVedtakFom)
        assertEquals(utbetalingsperiode.tom.toXMLDate(), oppdragsLinje150.datoVedtakTom)
        assertEquals(utbetalingsperiode.sats, oppdragsLinje150.sats)
        assertEquals(OppdragSkjemaConstants.FRADRAG_TILLEGG, oppdragsLinje150.fradragTillegg)
        assertEquals(OppdragSkjemaConstants.BRUK_KJØREPLAN_DEFAULT, oppdragsLinje150.brukKjoreplan)
        assertEquals(utbetalingsoppdrag.saksbehandlerId, oppdragsLinje150.saksbehId)
        assertEquals(utbetalingsoppdrag.aktør, oppdragsLinje150.utbetalesTilId)
        assertEquals(utbetalingsperiode.behandlingId, oppdragsLinje150.henvisning)
        utbetalingsoppdrag.beslutterId?.let {
            assertEquals(it, oppdragsLinje150.attestant180s[0].attestantId)
        }
            ?: assertEquals(utbetalingsoppdrag.saksbehandlerId, oppdragsLinje150.attestant180s[0].attestantId)
        assertEquals(utbetalingsperiode.utbetalingsgrad, oppdragsLinje150.grad170s.firstOrNull()?.grad?.toInt())

        if (utbetalingsperiode.forrigePeriodeId !== null && !utbetalingsperiode.erEndringPåEksisterendePeriode) {
            assertEquals(
                utbetalingsoppdrag.saksnummer + "#" + utbetalingsperiode.forrigePeriodeId.toString(),
                oppdragsLinje150.refDelytelseId,
            )
        }
    }

    private fun assertOpphør(
        utbetalingsperiode: Utbetalingsperiode,
        oppdragsLinje150: OppdragsLinje150,
    ) {
        utbetalingsperiode.opphør.let { opphør ->
            if (opphør == null) {
                assertNull(oppdragsLinje150.kodeStatusLinje)
                assertNull(oppdragsLinje150.datoStatusFom)
            } else {
                assertEquals("OPPH", oppdragsLinje150.kodeStatusLinje.name)
                assertEquals(opphør.fom.toXMLDate(), oppdragsLinje150.datoStatusFom)
            }
        }
    }
}
