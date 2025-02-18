package utsjekk.utbetaling

import TestRuntime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UtbetalingApiToDomainTest {

    /**
     * ╭───────────────╮          ╭────ENGANGS────╮
     * │ 4.feb - 4.feb │ skal bli │ 4.feb - 4.feb │
     * │ 500,-         │          │ 500,-         │
     * ╰───────────────╯          ╰───────────────╯
     */
    @Test
    fun `engangsperiode på 1 dag`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 4.feb,
            periodeType = PeriodeType.EN_GANG,
            perioder = listOf(UtbetalingsperiodeApi(4.feb, 4.feb, 500u)),
        )
        val domain = Utbetaling.from(api)
        val pid = domain.lastPeriodeId
        val expected = Utbetaling.dagpenger(
            vedtakstidspunkt = 4.feb,
            satstype = Satstype.ENGANGS,
            perioder = listOf(Utbetalingsperiode.dagpenger(4.feb, 4.feb, 500u)),
            lastPeriodeId = pid,
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
    fun `engangsperiode på under 1 mnd`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.aug,
            periodeType = PeriodeType.EN_GANG,
            perioder = listOf(UtbetalingsperiodeApi(1.aug, 24.aug, 7500u)),
        )
        val domain = Utbetaling.from(api)
        val pid = domain.lastPeriodeId
        val expected = Utbetaling.dagpenger(
            vedtakstidspunkt = 1.aug,
            satstype = Satstype.ENGANGS,
            perioder = listOf(Utbetalingsperiode.dagpenger(1.aug, 24.aug, 7500u)),
            lastPeriodeId = pid,
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
    fun `engangsperiode på 3 mnd`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 1.feb,
            periodeType = PeriodeType.EN_GANG,
            perioder = listOf(UtbetalingsperiodeApi(1.feb, 31.mar, 35_000u)),
        )
        val domain = Utbetaling.from(api)
        val pid = domain.lastPeriodeId
        val expected = Utbetaling.dagpenger(
            vedtakstidspunkt = 1.feb,
            satstype = Satstype.ENGANGS,
            perioder = listOf(Utbetalingsperiode.dagpenger(1.feb, 31.mar, 35_000u)),
            lastPeriodeId = pid,
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
    fun `slår sammen dager uten helg`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 5.aug,
            periodeType = PeriodeType.UKEDAG,
            listOf(
                UtbetalingsperiodeApi(1.aug, 1.aug, 100u),
                UtbetalingsperiodeApi(2.aug, 2.aug, 100u),
                UtbetalingsperiodeApi(5.aug, 5.aug, 100u),
            ),
        )
        val domain = Utbetaling.from(api)
        val pid = domain.lastPeriodeId
        val expected = Utbetaling.dagpenger(
            vedtakstidspunkt = 5.aug,
            satstype = Satstype.VIRKEDAG,
            perioder = listOf(Utbetalingsperiode.dagpenger(1.aug, 5.aug, 100u)),
            lastPeriodeId = pid,
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
    fun `slår sammen dager med helg`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 5.aug,
            periodeType = PeriodeType.DAG,
            listOf(
                UtbetalingsperiodeApi(1.aug, 1.aug, 100u),
                UtbetalingsperiodeApi(2.aug, 2.aug, 100u),
                UtbetalingsperiodeApi(3.aug, 3.aug, 100u),
                UtbetalingsperiodeApi(4.aug, 4.aug, 100u),
                UtbetalingsperiodeApi(5.aug, 5.aug, 100u),
            ),
        )
        val domain = Utbetaling.from(api)
        val pid = domain.lastPeriodeId
        val expected = Utbetaling.dagpenger(
            vedtakstidspunkt = 5.aug,
            satstype = Satstype.DAG,
            perioder = listOf(Utbetalingsperiode.dagpenger(1.aug, 5.aug, 100u)),
            lastPeriodeId = pid,
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
    fun `mapper en månedsperiode`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 29.feb,
            periodeType = PeriodeType.MND,
            perioder = listOf(
                UtbetalingsperiodeApi(1.feb, 29.feb, 26_000u),
            ),
        )
        val domain = Utbetaling.from(api)
        val lastPeriodeId = domain.lastPeriodeId
        val expected = Utbetaling.dagpenger(
            vedtakstidspunkt = 29.feb,
            lastPeriodeId = lastPeriodeId,
            satstype = Satstype.MND,
            perioder = listOf(Utbetalingsperiode.dagpenger(1.feb, 29.feb, 26_000u)),
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
    fun `slår sammen 2 måneder`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 31.mar,
            periodeType = PeriodeType.MND,
            perioder = listOf(
                UtbetalingsperiodeApi(1.feb, 29.feb, 8_000u),
                UtbetalingsperiodeApi(1.mar, 31.mar, 8_000u),
            ),
        )
        val domain = Utbetaling.from(api)
        val pid = domain.lastPeriodeId
        val expected = Utbetaling.dagpenger(
            lastPeriodeId = pid,
            vedtakstidspunkt = 31.mar,
            satstype = Satstype.MND,
            perioder = listOf(Utbetalingsperiode.dagpenger(1.feb, 31.mar, 8_000u)),
            sakId = SakId(api.sakId),
            personident = Personident(api.personident),
            behandlingId = BehandlingId(api.behandlingId),
            saksbehandlerId = Navident(api.saksbehandlerId),
            beslutterId = Navident(api.beslutterId),
        )
        assertEquals(expected, domain)
    }

    /**
     * ╭───────────────╮╭───────────────╮          ╭───────DAG─────╮
     * │ 1.jan - 1.jan ││ 2.jan - 2.jan │ skal bli │ 1.jan - 2.jan │
     * │ 1000,-        ││ 1000,-        │          │ 1000,-        │
     * ╰───────────────╯╰───────────────╯          ╰───────────────╯
     */
    @Test
    fun `slår sammen dager - oppdatering av utbetaling`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 3.jan,
            periodeType = PeriodeType.DAG,
            perioder = listOf(
                UtbetalingsperiodeApi(1.jan, 1.jan, 1_000u),
                UtbetalingsperiodeApi(2.jan, 2.jan, 1_000u),
            ),
        )
        val domain = Utbetaling.from(api, PeriodeId())
        val pid = domain.lastPeriodeId
        val expected = Utbetaling.dagpenger(
            lastPeriodeId = pid,
            vedtakstidspunkt = 3.jan,
            satstype = Satstype.DAG,
            perioder = listOf(Utbetalingsperiode.dagpenger(1.jan, 2.jan, 1_000u)),
            sakId = SakId(api.sakId),
            personident = Personident(api.personident),
            behandlingId = BehandlingId(api.behandlingId),
            saksbehandlerId = Navident(api.saksbehandlerId),
            beslutterId = Navident(api.beslutterId),
        )
        assertEquals(expected, domain)
    }

    /**
     * ╭───────╮╭───────╮          ╭──DAG──╮╭──DAG──╮
     * │ 1.jan ││ 3.jan │ skal bli │ 1.jan ││ 3.jan │
     * │ 1000,-││ 1000,-│          │ 1000,-││ 1000,-│
     * ╰───────╯╰───────╯          ╰───────╯╰───────╯
     */
    @Test
    fun `slår sammen dager med opphold - oppdatering av utbetaling`() = runTest(TestRuntime.context) {
        val api = UtbetalingApi.dagpenger(
            vedtakstidspunkt = 3.jan,
            periodeType = PeriodeType.DAG,
            perioder = listOf(
                UtbetalingsperiodeApi(1.jan, 1.jan, 1_000u),
                UtbetalingsperiodeApi(3.jan, 3.jan, 1_000u),
            ),
        )
        val domain = Utbetaling.from(api, PeriodeId())
        val pid = domain.lastPeriodeId
        val expected = Utbetaling.dagpenger(
            lastPeriodeId = pid,
            vedtakstidspunkt = 3.jan,
            satstype = Satstype.DAG,
            perioder = listOf(
                Utbetalingsperiode.dagpenger(1.jan, 1.jan, 1_000u),
                Utbetalingsperiode.dagpenger(3.jan, 3.jan, 1_000u)
            ),
            sakId = SakId(api.sakId),
            personident = Personident(api.personident),
            behandlingId = BehandlingId(api.behandlingId),
            saksbehandlerId = Navident(api.saksbehandlerId),
            beslutterId = Navident(api.beslutterId),
        )
        assertEquals(expected, domain)
    }
}

