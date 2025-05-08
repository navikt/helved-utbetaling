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
            Dp.utbetaling(sid) {
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
            .hasNumberOfRecordsForKey(uuid, 1)
            .hasValue(StatusReply(Status.MOTTATT))

        TestTopics.utbetalinger.assertThat()
            .hasNumberOfRecordsForKey(uuid, 1)
            .hasLastValue(uuid) {
                utbetaling(Action.CREATE, uid, SakId(sid), BehandlingId(bid)) {
                    listOf(
                        periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 7), 553u),
                        periode(LocalDate.of(2021, 6, 8), LocalDate.of(2021, 6, 8), 553u),
                        periode(LocalDate.of(2021, 6, 9), LocalDate.of(2021, 6, 9), 553u),
                        periode(LocalDate.of(2021, 6, 10), LocalDate.of(2021, 6, 10), 553u),
                        periode(LocalDate.of(2021, 6, 11), LocalDate.of(2021, 6, 11), 553u),
                        periode(LocalDate.of(2021, 6, 14), LocalDate.of(2021, 6, 14), 553u),
                        periode(LocalDate.of(2021, 6, 15), LocalDate.of(2021, 6, 15), 553u),
                        periode(LocalDate.of(2021, 6, 16), LocalDate.of(2021, 6, 16), 553u),
                        periode(LocalDate.of(2021, 6, 17), LocalDate.of(2021, 6, 17), 553u),
                        periode(LocalDate.of(2021, 6, 18), LocalDate.of(2021, 6, 18), 553u),
                    )
                }
            }
        TestTopics.oppdrag.assertThat()
            .hasNumberOfRecordsForKey(uuid, 1)
            .withLastValue {
                assertEquals("NY", it!!.oppdrag110.kodeEndring)
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
                ) +
                        Dp.meldekort(
                            meldeperiode = "232460781",
                            fom = LocalDate.of(2021, 7, 7),
                            tom = LocalDate.of(2021, 7, 20),
                            sats = 2377u,
                            utbetaling = 779u,
                        )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey(meldekort1, 1)
            .hasValueEquals(meldekort1, 0) {
                StatusReply(Status.MOTTATT)
            }
            .hasNumberOfRecordsForKey(meldekort2, 1)
            .hasValueEquals(meldekort2, 0) {
                StatusReply(Status.MOTTATT)
            }

        TestTopics.utbetalinger.assertThat()
            .hasNumberOfRecordsForKey(meldekort1, 1)
            .hasValueEquals(meldekort1) {
                utbetaling(
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
            }

            .hasNumberOfRecordsForKey(meldekort2, 1)
            .hasValueEquals(meldekort2) {
                utbetaling(
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
            }
        TestTopics.oppdrag.assertThat()
            .hasNumberOfRecordsForKey(meldekort1, 1)
            .withLastValue {
                assertEquals("NY", it!!.oppdrag110.kodeEndring)
            }
            .hasNumberOfRecordsForKey(meldekort2, 1)
            .withLastValue {
                assertEquals("NY", it!!.oppdrag110.kodeEndring)
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
