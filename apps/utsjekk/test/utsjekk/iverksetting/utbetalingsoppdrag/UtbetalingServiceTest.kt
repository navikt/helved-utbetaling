package utsjekk.iverksetting.utbetalingsoppdrag

import TestData
import TestData.random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import utsjekk.avstemming.nesteVirkedag
import utsjekk.iverksetting.RandomOSURId
import utsjekk.iverksetting.v3.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private val Int.feb: LocalDate get() = LocalDate.of(2021, 2, this)
private val Int.mar: LocalDate get() = LocalDate.of(2021, 3, this)
private val virkedager: (LocalDate) -> LocalDate = { it.nesteVirkedag() }
private val alleDager: (LocalDate) -> LocalDate = { it.plusDays(1) }

class UtbetalingServiceTest {

    @Test
    fun `legg til to måneder`() {
        val utbetaling = Utbetaling(
            sakId = SakId(RandomOSURId.generate()),
            behandlingId = BehandlingId(RandomOSURId.generate()),
            personident = Personident.random(),
            vedtakstidspunkt = 1.feb.atStartOfDay(),
            saksbehandlerId = Navident(TestData.DEFAULT_SAKSBEHANDLER),
            beslutterId = Navident(TestData.DEFAULT_BESLUTTER),
            perioder = listOf(
                Utbetalingsperiode(1.feb, 28.feb, 20u * 700u, Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR),
                Utbetalingsperiode(1.mar, 31.mar, 23u * 700u, Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
            )
        )

        val actual = UtbetalingsoppdragService.create(utbetaling, FagsystemDto.DAGPENGER)
        val expected = UtbetalingsoppdragDto(
            erFørsteUtbetalingPåSak = true,
            fagsystem = FagsystemDto.DAGPENGER,
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = TestData.DEFAULT_SAKSBEHANDLER,
            beslutterId = TestData.DEFAULT_BESLUTTER,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = null,
            utbetalingsperiode = listOf(
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[0].id,
                    forrigeId = null,
                    vedtaksdato = 1.feb,
                    klassekode = "DPORAS",
                    fom = 1.feb,
                    tom = 28.feb,
                    sats = 20u * 700u,
                    satstype = Satstype.MÅNEDLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                ),
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[1].id,
                    forrigeId = utbetaling.perioder[0].id,
                    vedtaksdato = 1.feb,
                    klassekode = "DPORAS",
                    fom = 1.mar,
                    tom = 31.mar,
                    sats = 23u * 700u,
                    satstype = Satstype.MÅNEDLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `lag en enkeltutbetaling`() {
        val utbetaling = Utbetaling(
            sakId = SakId(RandomOSURId.generate()),
            behandlingId = BehandlingId(RandomOSURId.generate()),
            personident = Personident.random(),
            vedtakstidspunkt = 8.feb.atStartOfDay(),
            saksbehandlerId = Navident(TestData.DEFAULT_SAKSBEHANDLER),
            beslutterId = Navident(TestData.DEFAULT_BESLUTTER),
            perioder = listOf(
                Utbetalingsperiode(8.feb, 16.feb, 1500u, Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR),
            )
        )

        val actual = UtbetalingsoppdragService.create(utbetaling, FagsystemDto.DAGPENGER)
        val expected = UtbetalingsoppdragDto(
            erFørsteUtbetalingPåSak = true,
            fagsystem = FagsystemDto.DAGPENGER,
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = TestData.DEFAULT_SAKSBEHANDLER,
            beslutterId = TestData.DEFAULT_BESLUTTER,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = null,
            utbetalingsperiode = listOf(
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[0].id,
                    forrigeId = null,
                    vedtaksdato = 8.feb,
                    klassekode = "DPORAS",
                    fom = 8.feb,
                    tom = 16.feb,
                    sats = 1500u,
                    satstype = Satstype.ENGANGS,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                ),
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `begrens til virkedager`() {
        val utbetaling = Utbetaling(
            sakId = SakId(RandomOSURId.generate()),
            behandlingId = BehandlingId(RandomOSURId.generate()),
            personident = Personident.random(),
            vedtakstidspunkt = 8.feb.atStartOfDay(),
            saksbehandlerId = Navident(TestData.DEFAULT_SAKSBEHANDLER),
            beslutterId = Navident(TestData.DEFAULT_BESLUTTER),
            perioder = expand(
                fom = 5.feb,
                tom = 8.feb,
                beløp = 800u,
                stønad = Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG,
                expansionStrategy = virkedager
            )
        )

        val actual = UtbetalingsoppdragService.create(utbetaling, FagsystemDto.DAGPENGER)
        val expected = UtbetalingsoppdragDto(
            erFørsteUtbetalingPåSak = true,
            fagsystem = FagsystemDto.DAGPENGER,
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = TestData.DEFAULT_SAKSBEHANDLER,
            beslutterId = TestData.DEFAULT_BESLUTTER,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = null,
            utbetalingsperiode = listOf(
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[0].id,
                    forrigeId = null,
                    vedtaksdato = 8.feb,
                    klassekode = "DPORASFE",
                    fom = 5.feb,
                    tom = 5.feb,
                    sats = 800u,
                    satstype = Satstype.DAGLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                ),
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[1].id,
                    forrigeId = utbetaling.perioder[0].id,
                    vedtaksdato = 8.feb,
                    klassekode = "DPORASFE",
                    fom = 8.feb,
                    tom = 8.feb,
                    sats = 800u,
                    satstype = Satstype.DAGLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `inkludere alle dager`() {
        val utbetaling = Utbetaling(
            sakId = SakId(RandomOSURId.generate()),
            behandlingId = BehandlingId(RandomOSURId.generate()),
            personident = Personident.random(),
            vedtakstidspunkt = 8.feb.atStartOfDay(),
            saksbehandlerId = Navident(TestData.DEFAULT_SAKSBEHANDLER),
            beslutterId = Navident(TestData.DEFAULT_BESLUTTER),
            perioder = expand(
                fom = 5.feb,
                tom = 8.feb,
                beløp = 900u,
                stønad = Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                expansionStrategy = alleDager
            )
        )

        val actual = UtbetalingsoppdragService.create(utbetaling, FagsystemDto.DAGPENGER)
        val expected = UtbetalingsoppdragDto(
            erFørsteUtbetalingPåSak = true,
            fagsystem = FagsystemDto.DAGPENGER,
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = TestData.DEFAULT_SAKSBEHANDLER,
            beslutterId = TestData.DEFAULT_BESLUTTER,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = null,
            utbetalingsperiode = listOf(
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[0].id,
                    forrigeId = null,
                    vedtaksdato = 8.feb,
                    klassekode = "DPORAS",
                    fom = 5.feb,
                    tom = 5.feb,
                    sats = 900u,
                    satstype = Satstype.DAGLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                ),
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[1].id,
                    forrigeId = utbetaling.perioder[0].id,
                    vedtaksdato = 8.feb,
                    klassekode = "DPORAS",
                    fom = 6.feb,
                    tom = 6.feb,
                    sats = 900u,
                    satstype = Satstype.DAGLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                ),
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[2].id,
                    forrigeId = utbetaling.perioder[1].id,
                    vedtaksdato = 8.feb,
                    klassekode = "DPORAS",
                    fom = 7.feb,
                    tom = 7.feb,
                    sats = 900u,
                    satstype = Satstype.DAGLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                ),
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[3].id,
                    forrigeId = utbetaling.perioder[2].id,
                    vedtaksdato = 8.feb,
                    klassekode = "DPORAS",
                    fom = 8.feb,
                    tom = 8.feb,
                    sats = 900u,
                    satstype = Satstype.DAGLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `kjede med tidliger utbetaling`() {
        val tidligereUtbetaling = Utbetaling(
            sakId = SakId(RandomOSURId.generate()),
            behandlingId = BehandlingId(RandomOSURId.generate()),
            personident = Personident.random(),
            vedtakstidspunkt = 1.feb.atStartOfDay(),
            saksbehandlerId = Navident(TestData.DEFAULT_SAKSBEHANDLER),
            beslutterId = Navident(TestData.DEFAULT_BESLUTTER),
            perioder = listOf(
                Utbetalingsperiode(1.feb, 28.feb, 20u * 700u, Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR),
            )
        )
        val tidligereUtbetalingId = DatabaseFake.save(tidligereUtbetaling)

        val utbetaling = Utbetaling(
            ref = tidligereUtbetalingId,
            sakId = tidligereUtbetaling.sakId,
            behandlingId = BehandlingId(RandomOSURId.generate()),
            personident = tidligereUtbetaling.personident,
            vedtakstidspunkt = 1.mar.atStartOfDay(),
            saksbehandlerId = Navident(TestData.DEFAULT_SAKSBEHANDLER),
            beslutterId = Navident(TestData.DEFAULT_BESLUTTER),
            perioder = listOf(
                Utbetalingsperiode(1.mar, 31.mar, 23u * 700u, Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
            )
        )
        val actual = UtbetalingsoppdragService.create(utbetaling, FagsystemDto.DAGPENGER)
        val expected = UtbetalingsoppdragDto(
            erFørsteUtbetalingPåSak = false,
            fagsystem = FagsystemDto.DAGPENGER,
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = TestData.DEFAULT_SAKSBEHANDLER,
            beslutterId = TestData.DEFAULT_BESLUTTER,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = null,
            utbetalingsperiode = listOf(
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[0].id,
                    forrigeId = tidligereUtbetaling.sistePeriode().id,
                    vedtaksdato = 1.mar,
                    klassekode = "DPORAS",
                    fom = 1.mar,
                    tom = 31.mar,
                    sats = 23u * 700u,
                    satstype = Satstype.MÅNEDLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                ),
            )
        )
        assertEquals(expected, actual)
    }

    // TODO: se igjennom denne testen om vi skal validere noe mer,
    //  fordi den er identisk med `kjede med tidliger utbetaling`
    @Test
    fun `gjenbruk en behandlingId`() {
        val tidligereUtbetaling = Utbetaling(
            sakId = SakId(RandomOSURId.generate()),
            behandlingId = BehandlingId(RandomOSURId.generate()),
            personident = Personident.random(),
            vedtakstidspunkt = 1.feb.atStartOfDay(),
            saksbehandlerId = Navident(TestData.DEFAULT_SAKSBEHANDLER),
            beslutterId = Navident(TestData.DEFAULT_BESLUTTER),
            perioder = listOf(
                Utbetalingsperiode(1.feb, 28.feb, 20u * 700u, Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR),
            )
        )
        val tidligereUtbetalingId = DatabaseFake.save(tidligereUtbetaling)

        val utbetaling = Utbetaling(
            ref = tidligereUtbetalingId,
            sakId = tidligereUtbetaling.sakId,
            behandlingId = tidligereUtbetaling.behandlingId,
            personident = tidligereUtbetaling.personident,
            vedtakstidspunkt = 1.mar.atStartOfDay(),
            saksbehandlerId = Navident(TestData.DEFAULT_SAKSBEHANDLER),
            beslutterId = Navident(TestData.DEFAULT_BESLUTTER),
            perioder = listOf(
                Utbetalingsperiode(1.mar, 31.mar, 23u * 700u, Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
            )
        )
        val actual = UtbetalingsoppdragService.create(utbetaling, FagsystemDto.DAGPENGER)
        val expected = UtbetalingsoppdragDto(
            erFørsteUtbetalingPåSak = false,
            fagsystem = FagsystemDto.DAGPENGER,
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = TestData.DEFAULT_SAKSBEHANDLER,
            beslutterId = TestData.DEFAULT_BESLUTTER,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = null,
            utbetalingsperiode = listOf(
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[0].id,
                    forrigeId = tidligereUtbetaling.sistePeriode().id,
                    vedtaksdato = 1.mar,
                    klassekode = "DPORAS",
                    fom = 1.mar,
                    tom = 31.mar,
                    sats = 23u * 700u,
                    satstype = Satstype.MÅNEDLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                ),
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    @Disabled
    fun `forkorte siste periode`() {
        val tidligereUtbetaling = Utbetaling(
            sakId = SakId(RandomOSURId.generate()),
            behandlingId = BehandlingId(RandomOSURId.generate()),
            personident = Personident.random(),
            vedtakstidspunkt = 1.feb.atStartOfDay(),
            saksbehandlerId = Navident(TestData.DEFAULT_SAKSBEHANDLER),
            beslutterId = Navident(TestData.DEFAULT_BESLUTTER),
            perioder = expand(1.feb, 5.feb, 700u, Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        )
        val tidligereUtbetalingId = DatabaseFake.save(tidligereUtbetaling)

        val utbetaling = Utbetaling(
            ref = tidligereUtbetalingId,
            sakId = tidligereUtbetaling.sakId,
            behandlingId = BehandlingId(RandomOSURId.generate()),
            personident = tidligereUtbetaling.personident,
            vedtakstidspunkt = 1.feb.atStartOfDay(),
            saksbehandlerId = Navident(TestData.DEFAULT_SAKSBEHANDLER),
            beslutterId = Navident(TestData.DEFAULT_BESLUTTER),
            perioder = expand(1.feb, 3.feb, 700u, Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        )

        val actual = UtbetalingsoppdragService.update(tidligereUtbetalingId, utbetaling, FagsystemDto.DAGPENGER)
        val expected = UtbetalingsoppdragDto(
            erFørsteUtbetalingPåSak = false,
            fagsystem = FagsystemDto.DAGPENGER,
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = TestData.DEFAULT_SAKSBEHANDLER,
            beslutterId = TestData.DEFAULT_BESLUTTER,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = null,
            utbetalingsperiode = listOf(
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[0].id,
                    forrigeId = tidligereUtbetaling.førstePeriode().id,
                    vedtaksdato = 1.feb,
                    klassekode = "DPORAS",
                    fom = 1.feb,
                    tom = 1.feb,
                    sats = 700u,
                    satstype = Satstype.DAGLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                ),
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[1].id,
                    forrigeId = utbetaling.perioder[0].id,
                    vedtaksdato = 1.feb,
                    klassekode = "DPORAS",
                    fom = 2.feb,
                    tom = 2.feb,
                    sats = 700u,
                    satstype = Satstype.DAGLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                ),
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[2].id,
                    forrigeId = utbetaling.perioder[1].id,
                    vedtaksdato = 1.feb,
                    klassekode = "DPORAS",
                    fom = 3.feb,
                    tom = 3.feb,
                    sats = 700u,
                    satstype = Satstype.DAGLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                ),
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `forkort periode i starten`() {
        val tidligereUtbetaling = Utbetaling(
            sakId = SakId(RandomOSURId.generate()),
            behandlingId = BehandlingId(RandomOSURId.generate()),
            personident = Personident.random(),
            vedtakstidspunkt = 1.feb.atStartOfDay(),
            saksbehandlerId = Navident(TestData.DEFAULT_SAKSBEHANDLER),
            beslutterId = Navident(TestData.DEFAULT_BESLUTTER),
            perioder = expand(1.feb, 5.feb, 700u, Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        )
        val tidligereUtbetalingId = DatabaseFake.save(tidligereUtbetaling)

        val utbetaling = Utbetaling(
            ref = tidligereUtbetalingId,
            sakId = tidligereUtbetaling.sakId,
            behandlingId = BehandlingId(RandomOSURId.generate()),
            personident = tidligereUtbetaling.personident,
            vedtakstidspunkt = 8.feb.atStartOfDay(),
            saksbehandlerId = Navident(TestData.DEFAULT_SAKSBEHANDLER),
            beslutterId = Navident(TestData.DEFAULT_BESLUTTER),
            perioder = expand(3.feb, 5.feb, 700u, Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        )

        val actual = UtbetalingsoppdragService.update(tidligereUtbetalingId, utbetaling, FagsystemDto.DAGPENGER)

        fun expected(): UtbetalingsoppdragDto {
            val opphørsperiode = UtbetalingsperiodeDto(
                erEndringPåEksisterendePeriode = false,
                opphør = Opphør(1.feb),
                id = actual.utbetalingsperiode[0].id,
                forrigeId = actual.utbetalingsperiode[0].forrigeId,
                vedtaksdato = 1.feb,
                klassekode = "DPORAS",
                fom = 5.feb,
                tom = 5.feb,
                sats = 700u,
                satstype = Satstype.DAGLIG,
                utbetalesTil = tidligereUtbetaling.personident.ident,
                behandlingId = tidligereUtbetaling.behandlingId.id,
            )

            val førsteNyePeriode = UtbetalingsperiodeDto(
                erEndringPåEksisterendePeriode = false,
                opphør = null,
                id = actual.utbetalingsperiode[1].id,
                forrigeId = actual.utbetalingsperiode[1].forrigeId,
                vedtaksdato = 8.feb,
                klassekode = "DPORAS",
                fom = 3.feb,
                tom = 3.feb,
                sats = 700u,
                satstype = Satstype.DAGLIG,
                utbetalesTil = utbetaling.personident.ident,
                behandlingId = utbetaling.behandlingId.id,
            )

            val andreNyePeriode = UtbetalingsperiodeDto(
                erEndringPåEksisterendePeriode = false,
                opphør = null,
                id = actual.utbetalingsperiode[2].id,
                forrigeId = actual.utbetalingsperiode[2].forrigeId,
                vedtaksdato = 8.feb,
                klassekode = "DPORAS",
                fom = 4.feb,
                tom = 4.feb,
                sats = 700u,
                satstype = Satstype.DAGLIG,
                utbetalesTil = utbetaling.personident.ident,
                behandlingId = utbetaling.behandlingId.id,
            )

            val tredjeNyePeriode = UtbetalingsperiodeDto(
                erEndringPåEksisterendePeriode = false,
                opphør = null,
                id = actual.utbetalingsperiode[3].id,
                forrigeId = actual.utbetalingsperiode[3].forrigeId,
                vedtaksdato = 8.feb,
                klassekode = "DPORAS",
                fom = 5.feb,
                tom = 5.feb,
                sats = 700u,
                satstype = Satstype.DAGLIG,
                utbetalesTil = utbetaling.personident.ident,
                behandlingId = utbetaling.behandlingId.id,
            )

            return UtbetalingsoppdragDto(
                erFørsteUtbetalingPåSak = false,
                fagsystem = FagsystemDto.DAGPENGER,
                saksnummer = utbetaling.sakId.id,
                aktør = utbetaling.personident.ident,
                saksbehandlerId = TestData.DEFAULT_SAKSBEHANDLER,
                beslutterId = TestData.DEFAULT_BESLUTTER,
                avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
                brukersNavKontor = null,
                utbetalingsperiode = listOf(opphørsperiode, førsteNyePeriode, andreNyePeriode, tredjeNyePeriode)
            )
        }

        assertEquals(expected(), actual)
    }

    @Test
    @Disabled
    fun `forkort periode i begge ender`() {
        val tidligereUtbetaling = Utbetaling(
            sakId = SakId(RandomOSURId.generate()),
            behandlingId = BehandlingId(RandomOSURId.generate()),
            personident = Personident.random(),
            vedtakstidspunkt = 1.feb.atStartOfDay(),
            saksbehandlerId = Navident(TestData.DEFAULT_SAKSBEHANDLER),
            beslutterId = Navident(TestData.DEFAULT_BESLUTTER),
            perioder = expand(1.feb, 5.feb, 700u, Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        )
        val tidligereUtbetalingId = DatabaseFake.save(tidligereUtbetaling)

        val utbetaling = Utbetaling(
            ref = tidligereUtbetalingId,
            sakId = tidligereUtbetaling.sakId,
            behandlingId = BehandlingId(RandomOSURId.generate()),
            personident = tidligereUtbetaling.personident,
            vedtakstidspunkt = 4.feb.atStartOfDay(),
            saksbehandlerId = Navident(TestData.DEFAULT_SAKSBEHANDLER),
            beslutterId = Navident(TestData.DEFAULT_BESLUTTER),
            perioder = expand(2.feb, 4.feb, 700u, Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        )

        val actual = UtbetalingsoppdragService.update(tidligereUtbetalingId, utbetaling, FagsystemDto.DAGPENGER)
        val expected = UtbetalingsoppdragDto(
            erFørsteUtbetalingPåSak = false,
            fagsystem = FagsystemDto.DAGPENGER,
            saksnummer = utbetaling.sakId.id,
            aktør = utbetaling.personident.ident,
            saksbehandlerId = TestData.DEFAULT_SAKSBEHANDLER,
            beslutterId = TestData.DEFAULT_BESLUTTER,
            avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            brukersNavKontor = null,
            utbetalingsperiode = listOf(
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[0].id,
                    forrigeId = tidligereUtbetaling.førstePeriode().id, // TODO: eller 2.feb? (tidligereUtbetaling.perioder[1].id)
                    vedtaksdato = 4.feb,
                    klassekode = "DPORAS",
                    fom = 2.feb,
                    tom = 2.feb,
                    sats = 700u,
                    satstype = Satstype.DAGLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                ),
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[1].id,
                    forrigeId = utbetaling.perioder[0].id,
                    vedtaksdato = 4.feb,
                    klassekode = "DPORAS",
                    fom = 3.feb,
                    tom = 3.feb,
                    sats = 700u,
                    satstype = Satstype.DAGLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                ),
                UtbetalingsperiodeDto(
                    erEndringPåEksisterendePeriode = false,
                    opphør = null,
                    id = utbetaling.perioder[2].id,
                    forrigeId = utbetaling.perioder[1].id,
                    vedtaksdato = 4.feb,
                    klassekode = "DPORAS",
                    fom = 4.feb,
                    tom = 4.feb,
                    sats = 700u,
                    satstype = Satstype.DAGLIG,
                    utbetalesTil = utbetaling.personident.ident,
                    behandlingId = utbetaling.behandlingId.id,
                ),
            )
        )
        assertEquals(expected, actual)
    }
}

private fun Personident.Companion.random(): Personident {
    return Personident(no.nav.utsjekk.kontrakter.felles.Personident.random().verdi)
}


private fun expand(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    stønad: Stønadstype,
    expansionStrategy: (LocalDate) -> LocalDate = virkedager
): List<Utbetalingsperiode> = buildList {
    var date = fom
    while (date.isBefore(tom) || date.isEqual(tom)) {
        add(Utbetalingsperiode(date, date, beløp, stønad))
        date = expansionStrategy(date)
    }
}