package abetal.consumers

import abetal.*
import abetal.models.uuid
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import models.*
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class DpTest {

    @Test
    fun `1 meldekort blir til 1 utbetaling`() {
        val uid = randomUtbetalingId()
        val sid = "$nextInt"
        val bid = "$nextInt"
        val uuid = "ac563362-78d2-6295-63f8-05f7cacfc1eb"

        TestTopics.dp.produce("${uid.id}") {
            Dp.utbetaling(sid, bid) {
                Dp.meldekort(
                    meldeperiode = "132460781",
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaling = 553u,
                )
            }
        }
        TestTopics.status.assertThat()
            .has(uuid)
            .has(uuid, StatusReply(Status.MOTTATT))

        TestTopics.utbetalinger.assertThat()
            .has(uuid)
            .with(uuid) {
                val expected = utbetaling(
                    Action.CREATE,
                    UtbetalingId(UUID.fromString(uuid)),
                    SakId(sid), BehandlingId(bid),
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    listOf(
                        periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 21), 553u, 1077u),
                    )
                }
                assertEquals(expected, it)
            }
        TestTopics.oppdrag.assertThat()
            .has(uuid)
            .with(uuid) {
                assertEquals("NY", it.oppdrag110.kodeEndring)
            }

    }

    @Test
    fun `2 meldekort blir til 2 utbetaling`() {
        val sid = "$nextInt"
        val bid = "$nextInt"
        val meldekort1 = "ac563362-78d2-6295-63f8-05f7cacfc1eb"
        val meldekort2 = "53aa88ef-0147-d9ef-ff6e-276672cc9c02"


        TestTopics.dp.produce(UUID.randomUUID().toString()) {
            Dp.utbetaling(sid, bid) {
                Dp.meldekort(
                    meldeperiode = "132460781",
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaling = 553u,
                ) + Dp.meldekort(
                        meldeperiode = "232460781",
                        fom = LocalDate.of(2021, 7, 7),
                        tom = LocalDate.of(2021, 7, 20),
                        sats = 2377u,
                        utbetaling = 779u,
                    )
            }
        }
        TestTopics.status.assertThat()
            .has(meldekort1)
            .has(meldekort1, StatusReply(Status.MOTTATT))
            .has(meldekort2)
            .has(meldekort2, StatusReply(Status.MOTTATT))

        TestTopics.utbetalinger.assertThat()
            .has(meldekort1)
            .with(meldekort1) {
                val expected = utbetaling(
                    Action.CREATE,
                    UtbetalingId(UUID.fromString(meldekort1)),
                    SakId(sid), BehandlingId(bid),
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    listOf(
                        periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 21), 553u, 1077u),
                    )
                }
                assertEquals(expected, it)
            }
            .has(meldekort2)
            .with(meldekort2) {
                val expected = utbetaling(
                    Action.CREATE,
                    UtbetalingId(UUID.fromString(meldekort2)),
                    SakId(sid), BehandlingId(bid),
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    listOf(
                        periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 21), 779u, 2377u),
                    )
                }
                assertEquals(expected, it)
            }
        TestTopics.oppdrag.assertThat()
            .has(meldekort1)
            .with(meldekort1) {
                assertEquals("NY", it.oppdrag110.kodeEndring)
            }
            .has(meldekort2)
            .with(meldekort2) {
                assertEquals("NY", it.oppdrag110.kodeEndring)
            }
    }

    @Test
    fun deterministiskUUIDGenerator() {
        val fagsystem = Fagsystem.DAGPENGER
        val m1 = "132460781"
        val m2 = "232460781"

        assertEquals(uuid(fagsystem, m1), uuid(fagsystem, m1))
        assertEquals(uuid(fagsystem, m2), uuid(fagsystem, m2))
        assertNotEquals(uuid(fagsystem, m1), uuid(fagsystem, m2))
    }
}
