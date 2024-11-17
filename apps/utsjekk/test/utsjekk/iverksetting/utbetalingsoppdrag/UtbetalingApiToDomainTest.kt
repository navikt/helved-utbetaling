package utsjekk.iverksetting.utbetalingsoppdrag

// imports 
import TestData.random
import httpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import utsjekk.avstemming.nesteVirkedag
import utsjekk.avstemming.erHelligdag
import utsjekk.iverksetting.RandomOSURId
import utsjekk.iverksetting.v3.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertTrue

private val Int.feb: LocalDate get() = LocalDate.of(2024, 2, this)
private val Int.mar: LocalDate get() = LocalDate.of(2024, 3, this)
private val Int.aug: LocalDate get() = LocalDate.of(2024, 8, this)
private val virkedager: (LocalDate) -> LocalDate = { it.nesteVirkedag() }
private val alleDager: (LocalDate) -> LocalDate = { it.plusDays(1) }

class UtbetalingApiToDomainTest {

    /*
     * ╭───────────────╮          ╭────ENGANGS────╮
     * │ 4.feb - 4.feb │ skal bli │ 4.feb - 4.feb │
     * │ 500,-         │          │ 500,-         │
     * ╰───────────────╯          ╰───────────────╯
     */
    @Test
    fun `1 dag utledes til ENGANGS`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 4.feb,
            listOf(UtbetalingsperiodeApi(4.feb, 4.feb, 500u)),
        )
        val domain = Utbetaling.from(api)
        val expected = Utbetaling.dagpenger(
            vedtakstidspunkt = 4.feb, 
            periode = Utbetalingsperiode.dagpenger(4.feb, 4.feb, 500u, Satstype.ENGANGS),
            sakId = SakId(api.sakId),
            personident = Personident(api.personident),
            behandlingId = BehandlingId(api.behandlingId),
            saksbehandlerId = Navident(api.saksbehandlerId),
            beslutterId = Navident(api.beslutterId),
        )
        assertEquals(expected, domain)
    }

    /**
     * ╭────────────────╮          ╭─────ENGANGS────╮
     * │ 1.aug - 24.aug │ skal bli │ 1.aug - 24.aug │
     * │ 7500,-         │          │ 7500,-         │
     * ╰────────────────╯          ╰────────────────╯
     */
    @Test
    fun `èn periode på mer enn 1 dag utledes til ENGANGS`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.aug,
            listOf(UtbetalingsperiodeApi(1.aug, 24.aug, 7500u)),
        )
        val domain = Utbetaling.from(api)
        val expected = Utbetaling.dagpenger(
            vedtakstidspunkt = 1.aug, 
            periode = Utbetalingsperiode.dagpenger(1.aug, 24.aug, 7500u, Satstype.ENGANGS),
            sakId = SakId(api.sakId),
            personident = Personident(api.personident),
            behandlingId = BehandlingId(api.behandlingId),
            saksbehandlerId = Navident(api.saksbehandlerId),
            beslutterId = Navident(api.beslutterId),
        )
        assertEquals(expected, domain)
    }

    /**
     * ╭────────────────╮          ╭─────ENGANGS────╮
     * │ 1.feb - 31.mar │ skal bli │ 1.feb - 31.mar │
     * │ 35000,-        │          │ 35000,-        │
     * ╰────────────────╯          ╰────────────────╯
     */
    @Test
    fun `1 periode på 3 fulle MND utledes til ENGANGS`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            listOf(UtbetalingsperiodeApi(1.feb, 31.mar, 35_000u)),
        )
        val domain = Utbetaling.from(api)
        val expected = Utbetaling.dagpenger(
            vedtakstidspunkt = 1.feb, 
            periode = Utbetalingsperiode.dagpenger(1.feb, 31.mar, 35_000u, Satstype.ENGANGS),
            sakId = SakId(api.sakId),
            personident = Personident(api.personident),
            behandlingId = BehandlingId(api.behandlingId),
            saksbehandlerId = Navident(api.saksbehandlerId),
            beslutterId = Navident(api.beslutterId),
        )
        assertEquals(expected, domain)
    }

    /**
     * ╭───────╮╭───────╮╭───────╮          ╭────VIRKEDAG───╮
     * │ 1.aug ││ 2.aug ││ 5.aug │ skal bli │ 1.aug - 5.aug │
     * │ 100,- ││ 100,- ││ 100,- │          │ 100,-         │
     * ╰───────╯╰───────╯╰───────╯          ╰───────────────╯
     */
    @Test
    fun `3 virkedager utledes til VIRKEDAG`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 5.aug, listOf(
                UtbetalingsperiodeApi(1.aug, 1.aug, 100u),
                UtbetalingsperiodeApi(2.aug, 2.aug, 100u),
                UtbetalingsperiodeApi(5.aug, 5.aug, 100u),
            ),
        )
        val domain = Utbetaling.from(api)
        val expected = Utbetaling.dagpenger(
            vedtakstidspunkt = 5.aug, 
            periode = Utbetalingsperiode.dagpenger(1.aug, 5.aug, 100u, Satstype.VIRKEDAG),
            sakId = SakId(api.sakId),
            personident = Personident(api.personident),
            behandlingId = BehandlingId(api.behandlingId),
            saksbehandlerId = Navident(api.saksbehandlerId),
            beslutterId = Navident(api.beslutterId),
        )
        assertEquals(expected, domain)
    }

    /**                     lør      søn
     * ╭───────╮╭───────╮╭───────╮╭───────╮╭───────╮          ╭──────DAG──────╮
     * │ 1.aug ││ 2.aug ││ 3.aug ││ 4.aug ││ 5.aug │ skal bli │ 1.aug - 5.aug │
     * │ 50,-  ││ 50,-  ││ 50,-  ││ 50,-  ││ 50,-  │          │ 50,-          │
     * ╰───────╯╰───────╯╰───────╯╰───────╯╰───────╯          ╰───────────────╯
     */
    @Test
    fun `5 dager og virkedager utledes til DAG`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 5.aug, listOf(
                UtbetalingsperiodeApi(1.aug, 1.aug, 100u),
                UtbetalingsperiodeApi(2.aug, 2.aug, 100u),
                UtbetalingsperiodeApi(3.aug, 3.aug, 100u),
                UtbetalingsperiodeApi(4.aug, 4.aug, 100u),
                UtbetalingsperiodeApi(5.aug, 5.aug, 100u),
            ),
        )
        val domain = Utbetaling.from(api)
        val expected = Utbetaling.dagpenger(
            vedtakstidspunkt = 5.aug, 
            periode = Utbetalingsperiode.dagpenger(1.aug, 5.aug, 100u, Satstype.DAG),
            sakId = SakId(api.sakId),
            personident = Personident(api.personident),
            behandlingId = BehandlingId(api.behandlingId),
            saksbehandlerId = Navident(api.saksbehandlerId),
            beslutterId = Navident(api.beslutterId),
        )
        assertEquals(expected, domain)
    }

    /**
     * ╭────────────────╮          ╭───────MND──────╮
     * │ 1.feb - 29.feb │ skal bli │ 1.feb - 29.feb │
     * │ 26000,-        │          │ 26000,-        │
     * ╰────────────────╯          ╰────────────────╯
     */
    @Test
    fun `1 MND utledes til MND`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 29.feb, listOf(
                UtbetalingsperiodeApi(1.feb, 29.feb, 26_000u),
            ),
        )
        val domain = Utbetaling.from(api)
        val expected = Utbetaling.dagpenger(
            vedtakstidspunkt = 29.feb, 
            periode = Utbetalingsperiode.dagpenger(1.feb, 29.feb, 26_000u, Satstype.MND),
            sakId = SakId(api.sakId),
            personident = Personident(api.personident),
            behandlingId = BehandlingId(api.behandlingId),
            saksbehandlerId = Navident(api.saksbehandlerId),
            beslutterId = Navident(api.beslutterId),
        )
        assertEquals(expected, domain)
    }

    /**
     * ╭────────────────╮╭────────────────╮          ╭───────MND──────╮
     * │ 1.feb - 29.feb ││ 1.mar - 31.mar │ skal bli │ 1.feb - 31.mar │
     * │ 8000,-         ││ 8000,-         │          │ 8000,-         │
     * ╰────────────────╯╰────────────────╯          ╰────────────────╯
     */
    @Test
    fun `2 MND utledes til MND`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 31.mar, listOf(
                UtbetalingsperiodeApi(1.feb, 29.feb, 8_000u),
                UtbetalingsperiodeApi(1.mar, 31.mar, 8_000u),
            ),
        )
        val domain = Utbetaling.from(api)
        val expected = Utbetaling.dagpenger(
            vedtakstidspunkt = 31.mar, 
            periode = Utbetalingsperiode.dagpenger(1.feb, 31.mar, 8_000u, Satstype.MND),
            sakId = SakId(api.sakId),
            personident = Personident(api.personident),
            behandlingId = BehandlingId(api.behandlingId),
            saksbehandlerId = Navident(api.saksbehandlerId),
            beslutterId = Navident(api.beslutterId),
        )
        assertEquals(expected, domain)
    }
}

private fun Personident.Companion.random(): Personident {
    return Personident(no.nav.utsjekk.kontrakter.felles.Personident.random().verdi)
}

private fun Utbetalingsperiode.Companion.dagpenger(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    satstype: Satstype,  
    betalendeEnhet: NavEnhet? = null,
    fastsattDagpengesats: UInt? = null,
): Utbetalingsperiode = Utbetalingsperiode(
    fom,
    tom,
    beløp,
    satstype,
    betalendeEnhet,
    fastsattDagpengesats,
)

private fun UtbetalingsperiodeDto.Companion.opphør(
    from: Utbetaling,
    opphør: LocalDate,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
) = UtbetalingsperiodeDto.default(from, fom, tom, sats, klassekode, Satstype.DAG, opphør = opphør)

private fun UtbetalingsperiodeDto.Companion.default(
    from: Utbetaling,
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
    vedtaksdato = from.vedtakstidspunkt.toLocalDate(),
    klassekode = klassekode,
    fom = fom,
    tom = tom,
    sats = sats,
    satstype = satstype,
    utbetalesTil = from.personident.ident,
    behandlingId = from.behandlingId.id,
    id = UUID.randomUUID(), // TODO: gjør dette enklere å teste
)

private fun UtbetalingsperiodeDto.Companion.dag(
    from: Utbetaling,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
) = UtbetalingsperiodeDto.default(from, fom, tom, sats, klassekode, Satstype.DAG)

private fun UtbetalingsperiodeDto.Companion.virkedag(
    from: Utbetaling,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
) = UtbetalingsperiodeDto.default(from, fom, tom, sats, klassekode, Satstype.VIRKEDAG)

private fun UtbetalingsperiodeDto.Companion.mnd(
    from: Utbetaling,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
) = UtbetalingsperiodeDto.default(from, fom, tom, sats, klassekode, Satstype.MND)

private fun UtbetalingsperiodeDto.Companion.eng(
    from: Utbetaling,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
) = UtbetalingsperiodeDto.default(from, fom, tom, sats, klassekode, Satstype.ENGANGS)

private fun UtbetalingsoppdragDto.Companion.dagpenger(
    from: Utbetaling,
    periode: UtbetalingsperiodeDto,
    erFørsteUtbetalingPåSak: Boolean = true,
    fagsystem: FagsystemDto = FagsystemDto.DAGPENGER,
    avstemmingstidspunkt: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
    brukersNavKontor: String? = null,
): UtbetalingsoppdragDto = UtbetalingsoppdragDto(
    erFørsteUtbetalingPåSak = erFørsteUtbetalingPåSak,
    fagsystem = fagsystem,
    saksnummer = from.sakId.id,
    aktør = from.personident.ident,
    saksbehandlerId = from.saksbehandlerId.ident,
    beslutterId = from.beslutterId.ident,
    avstemmingstidspunkt = avstemmingstidspunkt,
    brukersNavKontor = brukersNavKontor,
    utbetalingsperiode = periode,
)
