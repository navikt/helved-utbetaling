package utsjekk.iverksetting.utbetalingsoppdrag

import TestData
import TestData.random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import utsjekk.avstemming.nesteVirkedag
import utsjekk.iverksetting.RandomOSURId
import utsjekk.iverksetting.v3.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

private val Int.feb: LocalDate get() = LocalDate.of(2021, 2, this)
private val Int.mar: LocalDate get() = LocalDate.of(2021, 3, this)
private val virkedager: (LocalDate) -> LocalDate = { it.nesteVirkedag() }
private val alleDager: (LocalDate) -> LocalDate = { it.plusDays(1) }

class UtbetalingServiceTest {

    /**
     * En liste med  ╭───────MND──────╮   ╭───────MND──────╮
     * to mnd        │ 1.feb - 28.feb │<──│ 1.mar - 31.mar │
     * mappes til    ╰────────────────╯   ╰────────────────╯
     */
    @Test
    fun `legg til to måneder`() {
        val feb = Utbetalingsperiode.dagpenger(1.feb, 28.feb, 20u * 700u)
        val mar = Utbetalingsperiode.dagpenger(1.mar, 31.mar, 23u * 700u)
        val utbetaling = Utbetaling.dagpenger(vedtakstidspunkt = 1.feb, listOf(feb, mar))
        val expected = UtbetalingsoppdragDto.dagpenger(
            from = utbetaling, listOf(
                UtbetalingsperiodeDto.mnd(utbetaling, feb.id, null, 1.feb, 28.feb, 20u * 700u, "DPORAS"),
                UtbetalingsperiodeDto.mnd(utbetaling, mar.id, feb.id, 1.mar, 31.mar, 23u * 700u, "DPORAS"),
            )
        )

        assertEquals(expected, UtbetalingsoppdragService.create(utbetaling, FagsystemDto.DAGPENGER))
    }

    /**
     * En periode      ╭───────ENG──────╮
     * 8.feb - 16.feb  │ 8.feb - 16.feb │
     * mappes til      ╰────────────────╯
     */
    @Test
    fun `lag en enkeltutbetaling`() {
        val engangs = Utbetalingsperiode.dagpenger(8.feb, 16.feb, 1500u)
        val utbetaling = Utbetaling.dagpenger(8.feb, listOf(engangs))
        val expected = UtbetalingsoppdragDto.dagpenger(
            from = utbetaling, listOf(
                UtbetalingsperiodeDto.eng(utbetaling, engangs.id, null, 8.feb, 16.feb, 1500u, "DPORAS")
            )
        )

        assertEquals(expected, UtbetalingsoppdragService.create(utbetaling, FagsystemDto.DAGPENGER))
    }

    /**
     * En liste med    ╭──────DAG──────╮
     * 5.feb .. 8.feb  │ 5.feb - 8.feb │
     * mappes til      ╰───────────────╯
     * TODO: nå lages det 2 linjer (5 og 8 feb) og ikke 1 tykk.
     */
    @Test
    fun `begrens til virkedager`() {
        val dager = expand(5.feb, 8.feb, 800u, virkedager)
        val utbetaling = Utbetaling.dagpenger(8.feb, dager)
//        val (fom, tom) = utbetaling.førstePeriode().fom to utbetaling.sistePeriode().tom
        val expected = UtbetalingsoppdragDto.dagpenger(
            from = utbetaling, listOf(
                UtbetalingsperiodeDto.dag(utbetaling, dager[0].id, null, 5.feb, 5.feb, 800u, "DPORAS"),
                UtbetalingsperiodeDto.dag(utbetaling, dager[1].id, dager[0].id, 8.feb, 8.feb, 800u, "DPORAS"),
//                UtbetalingsperiodeDto.virkedag(utbetaling, dager[0].id, null, fom, tom, 800u, "DPORAS")
            )
        )

        assertEquals(expected, UtbetalingsoppdragService.create(utbetaling, FagsystemDto.DAGPENGER))
    }

    /**
     * En liste med    ╭──────DAG7─────╮
     * 5.feb .. 8.feb  │ 5.feb - 8.feb │
     * mappes til      ╰───────────────╯
     * TODO: nå lages det 4 linjer (5, 6, 7, 8 feb) og ikke 1 tykk.
     */
    @Test
    fun `inkludere alle dager`() {
        val dager = expand(5.feb, 8.feb, 800u, alleDager)
        val utbetaling = Utbetaling.dagpenger(8.feb, dager)
        val expected = UtbetalingsoppdragDto.dagpenger(
            from = utbetaling, listOf(
                UtbetalingsperiodeDto.dag(utbetaling, dager[0].id, null, 5.feb, 5.feb, 800u, "DPORAS"),
                UtbetalingsperiodeDto.dag(utbetaling, dager[1].id, dager[0].id, 6.feb, 6.feb, 800u, "DPORAS"),
                UtbetalingsperiodeDto.dag(utbetaling, dager[2].id, dager[1].id, 7.feb, 7.feb, 800u, "DPORAS"),
                UtbetalingsperiodeDto.dag(utbetaling, dager[3].id, dager[2].id, 8.feb, 8.feb, 800u, "DPORAS"),
            )
        )

        assertEquals(expected, UtbetalingsoppdragService.create(utbetaling, FagsystemDto.DAGPENGER))
    }

    @Test
    fun `kjede med tidliger utbetaling`() {
        val feb = Utbetalingsperiode.dagpenger(1.feb, 28.feb, 20u * 700u)
        val utbet1 = Utbetaling.dagpenger(1.feb, listOf(feb))
        val utbet1ID = DatabaseFake.save(utbet1)
        val mar = Utbetalingsperiode.dagpenger(1.mar, 31.mar, 23u * 700u)
        val utbet2 = Utbetaling.dagpenger(ref = utbet1ID to utbet1, 1.mar, listOf(mar))
        val expected = UtbetalingsoppdragDto.dagpenger(
            from = utbet2,
            perioder = listOf(UtbetalingsperiodeDto.mnd(utbet2, mar.id, feb.id, 1.mar, 31.mar, 23u * 700u, "DPORAS")),
            erFørsteUtbetalingPåSak = false,
        )
        assertEquals(expected, UtbetalingsoppdragService.create(utbet2, FagsystemDto.DAGPENGER))
    }

    /**
     * ╭────────────────╮              ╭────────────────╮   ╭────────────────╮
     * │ 1.feb - 28.feb │ skal bli til │ 1.feb - 28.feb │<──│ 1.mar - 31.feb │
     * ╰────────────────╯              ╰────────────────╯   ╰────────────────╯
     * TODO: erEndringPåEksisterendePeriode skal være true ved gjennbruk av behandlingId
     */
    @Test
    fun `gjenbruk en behandlingId`() {
        val feb = Utbetalingsperiode.dagpenger(1.feb, 28.feb, 20u * 700u)
        val utbet1 = Utbetaling.dagpenger(1.feb, listOf(feb))
        val utbet1ID = DatabaseFake.save(utbet1)
        val mar = Utbetalingsperiode.dagpenger(1.mar, 31.mar, 23u * 700u)
        val utbet2 = Utbetaling.dagpenger(ref = utbet1ID to utbet1, 1.mar, listOf(mar), utbet1.behandlingId)
        val expected = UtbetalingsoppdragDto.dagpenger(
            from = utbet2,
            perioder = listOf(
                UtbetalingsperiodeDto.default(
                    from = utbet2,
                    id = mar.id,
                    forrigeId = feb.id,
                    fom = 1.mar,
                    tom = 31.mar,
                    sats = 23u * 700u,
                    klassekode = "DPORAS",
                    satstype = Satstype.MND,
                    erEndringPåEsksisterendePeriode = false // FIXME
                )
            ),
            erFørsteUtbetalingPåSak = false,
        )
        assertEquals(expected, UtbetalingsoppdragService.create(utbet2, FagsystemDto.DAGPENGER))
    }

    /**
     * ╭───────────────╮              ╭───────────────╮
     * │ 1.feb - 5.feb │ skal bli til │ 1.feb - 3.feb │
     * ╰───────────────╯              ╰───────────────╯
     * TODO: nå blir 5 linjer til 3
     */
    @Test
    fun `forkorte siste periode`() {
        val dager1 = expand(1.feb, 5.feb, 800u, virkedager)
        val utbet1 = Utbetaling.dagpenger(1.feb, dager1)
        val utbet1ID = DatabaseFake.save(utbet1)

        val dager2 = expand(1.feb, 3.feb, 800u, virkedager)
        val utbet2 = Utbetaling.dagpenger(utbet1ID to utbet1, 1.feb, dager2)

        val expected = UtbetalingsoppdragDto.dagpenger(
            utbet2, listOf(
                UtbetalingsperiodeDto.dag(utbet2, dager2[0].id, null, 1.feb, 1.feb, 800u, "DPORAS"),
                UtbetalingsperiodeDto.dag(utbet2, dager2[1].id, dager1[0].id, 2.feb, 2.feb, 800u, "DPORAS"),
                UtbetalingsperiodeDto.dag(utbet2, dager2[2].id, dager1[1].id, 3.feb, 3.feb, 800u, "DPORAS"),
            )
        )
        assertThrows<NotImplementedError> { // FIXME: remove catch
            assertEquals(expected, UtbetalingsoppdragService.update(utbet1ID, utbet2, FagsystemDto.DAGPENGER))
        }
    }

    /**
     * ╭───────────────╮              ╭───────────────╮
     * │ 1.feb - 5.feb │ skal bli til │ 3.feb - 5.feb │
     * ╰───────────────╯              ╰───────────────╯
     * TODO: bytt ut fra tynne til tykke perioder
     */
    @Test
    fun `forkort periode i starten`() {
        val dager1 = expand(1.feb, 5.feb, 700u, virkedager)
        val u1 = Utbetaling.dagpenger(1.feb, dager1)
        val u1_id = DatabaseFake.save(u1)

        val dager2 = expand(3.feb, 5.feb, 700u, virkedager)
        val u2 = Utbetaling.dagpenger(8.feb, dager2)

        val actual = UtbetalingsoppdragService.update(u1_id, u2, FagsystemDto.DAGPENGER)

        fun expected(): UtbetalingsoppdragDto {
            fun id(i: Int): UUID = actual.utbetalingsperiode[i].id
            fun ref(i: Int): UUID? = actual.utbetalingsperiode[i].forrigeId
            val opphør = UtbetalingsperiodeDto.opphør(u1, 1.feb, id(0), ref(0), 5.feb, 5.feb, 700u, "DPORAS")
            val førsteNyePeriode = UtbetalingsperiodeDto.dag(u2, id(1), ref(1), 3.feb, 3.feb, 700u, "DPORAS")
            val andreNyePeriode = UtbetalingsperiodeDto.dag(u2, id(2), ref(2), 4.feb, 4.feb, 700u, "DPORAS")
            val tredjeNyePeriode = UtbetalingsperiodeDto.dag(u2, id(3), ref(3), 5.feb, 5.feb, 700u, "DPORAS")
            return UtbetalingsoppdragDto.dagpenger(
                from = u2,
                perioder = listOf(opphør, førsteNyePeriode, andreNyePeriode, tredjeNyePeriode),
                erFørsteUtbetalingPåSak = true // FIXME
            )
        }

        assertEquals(expected(), actual)
    }
    /**
     * ╭───────────────╮              ╭───────────────╮
     * │ 1.feb - 5.feb │ skal bli til │ 2.feb - 4.feb │
     * ╰───────────────╯              ╰───────────────╯
     * TODO: bytt ut fra tynne til tykke perioder
     */
    @Test
    @Disabled
    fun `forkort periode i begge ender`() {
        val u1Perioder = expand(1.feb, 5.feb, 700u, virkedager)
        val u1 = Utbetaling.dagpenger(1.feb, u1Perioder)
        val u1Id = DatabaseFake.save(u1)
        val u2Perioder = expand(2.feb, 4.feb, 700u, virkedager)
        val u2 = Utbetaling.dagpenger(u1Id to u1, 4.feb, u2Perioder)

        val actual = UtbetalingsoppdragService.update(u1Id, u1, FagsystemDto.DAGPENGER)

        fun id(i: Int): UUID = actual.utbetalingsperiode[i].id
        fun ref(i: Int): UUID? = actual.utbetalingsperiode[i].forrigeId
        val expected = UtbetalingsoppdragDto.dagpenger(
            u2, listOf(
                UtbetalingsperiodeDto.dag(u2, id(0), ref(0), 5.feb, 5.feb, 700u, "DPORAS"),
                UtbetalingsperiodeDto.dag(u2, id(1), ref(1), 2.feb, 2.feb, 700u, "DPORAS"),
                UtbetalingsperiodeDto.dag(u2, id(2), ref(2), 3.feb, 3.feb, 700u, "DPORAS"),
                UtbetalingsperiodeDto.dag(u2, id(3), ref(3), 4.feb, 4.feb, 700u, "DPORAS"),
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
    expansionStrategy: (LocalDate) -> LocalDate,
    stønad: Stønadstype = Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
): List<Utbetalingsperiode> = buildList {
    var date = fom
    while (date.isBefore(tom) || date.isEqual(tom)) {
        add(Utbetalingsperiode(date, date, beløp, stønad))
        date = expansionStrategy(date)
    }
}

private fun Utbetalingsperiode.Companion.dagpenger(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    stønad: Stønadstype = Stønadstype.StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
    id: UUID = UUID.randomUUID(),
    brukersNavKontor: NavEnhet? = null,
    fastsattDagsats: UInt? = null,
): Utbetalingsperiode = Utbetalingsperiode(
    fom,
    tom,
    beløp,
    stønad,
    id,
    brukersNavKontor,
    fastsattDagsats,
)

private fun Utbetaling.Companion.dagpenger(
    ref: Pair<UtbetalingId, Utbetaling>,
    vedtakstidspunkt: LocalDate,
    perioder: List<Utbetalingsperiode>,
    behandlingId: BehandlingId = BehandlingId(RandomOSURId.generate()),
): Utbetaling = Utbetaling(
    ref.second.sakId,
    behandlingId,
    ref.second.personident,
    vedtakstidspunkt.atStartOfDay(),
    ref.second.saksbehandlerId,
    ref.second.beslutterId,
    perioder.toList(),
    ref.first
)

private fun Utbetaling.Companion.dagpenger(
    vedtakstidspunkt: LocalDate,
    perioder: List<Utbetalingsperiode>,
    sakId: SakId = SakId(RandomOSURId.generate()),
    personident: Personident = Personident.random(),
    behandlingId: BehandlingId = BehandlingId(RandomOSURId.generate()),
    saksbehandlerId: Navident = Navident(TestData.DEFAULT_SAKSBEHANDLER),
    beslutterId: Navident = Navident(TestData.DEFAULT_BESLUTTER),
): Utbetaling = Utbetaling(
    sakId,
    behandlingId,
    personident,
    vedtakstidspunkt.atStartOfDay(),
    saksbehandlerId,
    beslutterId,
    perioder.toList(),
    null
)

private fun UtbetalingsperiodeDto.Companion.opphør(
    from: Utbetaling,
    opphør: LocalDate,
    id: UUID,
    forrigeId: UUID?,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
): UtbetalingsperiodeDto =
    UtbetalingsperiodeDto.default(from, id, forrigeId, fom, tom, sats, klassekode, Satstype.DAG, opphør = opphør)

private fun UtbetalingsperiodeDto.Companion.default(
    from: Utbetaling,
    id: UUID,
    forrigeId: UUID?,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
    satstype: Satstype = Satstype.MND,
    erEndringPåEsksisterendePeriode: Boolean = false,
    opphør: LocalDate? = null,
): UtbetalingsperiodeDto = UtbetalingsperiodeDto(
    erEndringPåEksisterendePeriode = erEndringPåEsksisterendePeriode,
    opphør = opphør?.let(::Opphør),
    id = id,
    forrigeId = forrigeId,
    vedtaksdato = from.vedtakstidspunkt.toLocalDate(),
    klassekode = klassekode,
    fom = fom,
    tom = tom,
    sats = sats,
    satstype = satstype,
    utbetalesTil = from.personident.ident,
    behandlingId = from.behandlingId.id,
)

private fun UtbetalingsperiodeDto.Companion.dag(
    from: Utbetaling,
    id: UUID,
    forrigeId: UUID?,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
): UtbetalingsperiodeDto {
    return UtbetalingsperiodeDto.default(from, id, forrigeId, fom, tom, sats, klassekode, Satstype.DAG)
}

private fun UtbetalingsperiodeDto.Companion.virkedag(
    from: Utbetaling,
    id: UUID,
    forrigeId: UUID?,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
): UtbetalingsperiodeDto {
    return UtbetalingsperiodeDto.default(from, id, forrigeId, fom, tom, sats, klassekode, Satstype.VIRKEDAG)
}

private fun UtbetalingsperiodeDto.Companion.mnd(
    from: Utbetaling,
    id: UUID,
    forrigeId: UUID?,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
): UtbetalingsperiodeDto {
    return UtbetalingsperiodeDto.default(from, id, forrigeId, fom, tom, sats, klassekode, Satstype.MND)
}

private fun UtbetalingsperiodeDto.Companion.eng(
    from: Utbetaling,
    id: UUID,
    forrigeId: UUID?,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
): UtbetalingsperiodeDto {
    return UtbetalingsperiodeDto.default(from, id, forrigeId, fom, tom, sats, klassekode, Satstype.ENGANGS)
}

private fun UtbetalingsoppdragDto.Companion.dagpenger(
    from: Utbetaling,
    perioder: List<UtbetalingsperiodeDto>,
    erFørsteUtbetalingPåSak: Boolean = true,
    fagsystem: FagsystemDto = FagsystemDto.DAGPENGER,
    saksbehandlerId: String = TestData.DEFAULT_SAKSBEHANDLER,
    beslutterId: String = TestData.DEFAULT_BESLUTTER,
    avstemmingstidspunkt: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
    brukersNavKontor: String? = null,
): UtbetalingsoppdragDto = UtbetalingsoppdragDto(
    erFørsteUtbetalingPåSak = erFørsteUtbetalingPåSak,
    fagsystem = fagsystem,
    saksnummer = from.sakId.id,
    aktør = from.personident.ident,
    saksbehandlerId = saksbehandlerId,
    beslutterId = beslutterId,
    avstemmingstidspunkt = avstemmingstidspunkt,
    brukersNavKontor = brukersNavKontor,
    utbetalingsperiode = perioder,
)
