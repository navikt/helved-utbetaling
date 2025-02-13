package abetal

import abetal.models.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

val Int.jan: LocalDate
    get() = LocalDate.of(2025, 1, this)

val Int.des: LocalDate
    get() = LocalDate.of(2024, 12, this)

internal class AapTest {

    @Test
    fun `add to utbetalinger`() {
        val uid = UtbetalingId(UUID.randomUUID())
        TestTopics.aap.produce("${uid.id}") {
            AapUtbetaling(
                action = Action.CREATE,
                data = TestData.utbetaling(
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    periodetype = Periodetype.DAG,
                    perioder = listOf(
                        TestData.dag(1.jan),
                        TestData.dag(2.jan),
                    )
                )
            )
        }

        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey(uid.id.toString(), 1)
            .hasValue(StatusReply(SakId("1"), Status.MOTTATT))

        TestTopics.utbetalinger.assertThat()
            .hasValuesForPredicate(uid.id.toString(), 1) {
                it.perioder.size == 2
            }

        TestTopics.oppdrag.assertThat().hasNumberOfRecordsForKey(uid.id.toString(), 1)
            .hasLastValueMatching {
                assertEquals("NY", it!!.oppdrag110.kodeEndring)
            }
    }

    // @Test
    // fun `is idempotent`() {
    //     val uid = UtbetalingId(UUID.randomUUID())
    //     val aapUtbet = AapUtbetaling(
    //         action = Action.CREATE,
    //         data = TestData.utbetaling(
    //             stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
    //             periodetype = Periodetype.DAG,
    //             perioder = listOf(
    //                 TestData.dag(1.jan),
    //                 TestData.dag(2.jan),
    //             )
    //         )
    //     )
    //     TestTopics.aap.produce("${uid.id}") {
    //         aapUtbet
    //     }
    //     TestTopics.aap.produce("${uid.id}") {
    //         aapUtbet
    //     }
    //
    //     TestTopics.status.assertThat()
    //         .hasNumberOfRecordsForKey(uid.id.toString(), 2)
    //         .hasValueEquals(uid.id.toString(), 0) {
    //             StatusReply(
    //                 sakId = SakId("1"),
    //                 status = Status.MOTTATT, 
    //                 error = null,
    //             )
    //         }
    //         .hasValueEquals(uid.id.toString(), 1) {
    //             StatusReply(
    //                 sakId = SakId("1"),
    //                 status = Status.FEILET, 
    //                 error = ApiError(
    //                     statusCode = 409, 
    //                     msg = "Denne meldingen har du allerede sendt inn",
    //                     field = null,
    //                     doc = null,
    //                 )
    //             )
    //         }
    //
    //     TestTopics.utbetalinger.assertThat()
    //         .hasValuesForPredicate(uid.id.toString(), 1) {
    //             it.perioder.size == 2
    //         }
    //
    //     TestTopics.oppdrag.assertThat()
    //         .hasValuesForPredicate(uid.id.toString(), 1) {
    //             it.oppdrag110.kodeEndring == "NY"
    //         }
    // }

    @Test
    fun `error ved årsskifte`() {
        val uid = UtbetalingId(UUID.randomUUID())
        TestTopics.aap.produce("${uid.id}") {
            AapUtbetaling(
                action = Action.CREATE,
                data = TestData.utbetaling(
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    periodetype = Periodetype.DAG,
                    perioder = listOf(
                        TestData.dag(31.des),
                        TestData.dag(1.jan),
                    )
                )
            )
        }

        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey(uid.id.toString(), 2)
            .hasValueMatching("${uid.id}", 0) {
                assertEquals(Status.MOTTATT, it.status)
            }
            .hasValueMatching("${uid.id}", 1) {
                assertEquals(Status.FEILET, it.status)
                assertEquals("periode strekker seg over årsskifte", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().isEmpty()
        TestTopics.oppdrag.assertThat().isEmpty()
    }

    @Test
    fun `error ved to perioder med samme fom`() {
        val uid = UtbetalingId(UUID.randomUUID())
        TestTopics.aap.produce("${uid.id}") {
            AapUtbetaling(
                action = Action.CREATE,
                data = TestData.utbetaling(
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    periodetype = Periodetype.DAG,
                    perioder = listOf(
                        TestData.utbetalingsperiode(fom = 1.jan, tom = 2.jan),
                        TestData.utbetalingsperiode(fom = 1.jan, tom = 3.jan),
                    )
                )
            )
        }

        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey(uid.id.toString(), 2)
            .hasNumberOfRecords(2)
            .hasLastValueMatching {
                assertEquals("kan ikke sende inn duplikate perioder", it!!.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().isEmpty()
        TestTopics.oppdrag.assertThat().isEmpty()
    }

    @Test
    fun `error ved to perioder med samme tom`() {
        val uid = UtbetalingId(UUID.randomUUID())
        TestTopics.aap.produce("${uid.id}") {
            AapUtbetaling(
                action = Action.CREATE,
                data = TestData.utbetaling(
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    periodetype = Periodetype.DAG,
                    perioder = listOf(
                        TestData.utbetalingsperiode(fom = 1.jan, tom = 2.jan),
                        TestData.utbetalingsperiode(fom = 2.jan, tom = 2.jan),
                    )
                )
            )
        }

        TestTopics.status.assertThat().hasValueMatching("${uid.id}", 1) {
            assertEquals("kan ikke sende inn duplikate perioder", it.error!!.msg)
        }
        TestTopics.utbetalinger.assertThat().isEmpty()
    }

    @Test
    fun `error ved tom før fom`() {
        val uid = UtbetalingId(UUID.randomUUID())
        TestTopics.aap.produce("${uid.id}") {
            AapUtbetaling(
                action = Action.CREATE,
                data = TestData.utbetaling(
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    periodetype = Periodetype.DAG,
                    perioder = listOf(
                        TestData.utbetalingsperiode(fom = 2.jan, tom = 1.jan),
                    )
                )
            )
        }

        TestTopics.status.assertThat().hasValueMatching("${uid.id}", 1) {
            assertEquals("fom må være før eller lik tom", it.error!!.msg)
        }
        TestTopics.utbetalinger.assertThat().isEmpty()
    }

    @Test
    fun `error ved ulovlig bruk av fastsatt dagsats`() {
        val uid = UtbetalingId(UUID.randomUUID())
        TestTopics.aap.produce("${uid.id}") {
            AapUtbetaling(
                action = Action.CREATE,
                data = TestData.utbetaling(
                    stønad = StønadTypeTiltakspenger.JOBBKLUBB,
                    periodetype = Periodetype.DAG,
                    perioder = listOf(
                        TestData.dag(1.jan),
                    )
                )
            )
        }

        TestTopics.status.assertThat().hasValueMatching("${uid.id}", 1) {
            assertEquals("reservert felt for Dagpenger og AAP", it.error!!.msg)
        }
        TestTopics.utbetalinger.assertThat().isEmpty()
    }

    @Test
    fun `error ved blanding av periodetyper`() {
        val uid = UtbetalingId(UUID.randomUUID())
        TestTopics.aap.produce("${uid.id}") {
            AapUtbetaling(
                action = Action.CREATE,
                data = TestData.utbetaling(
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    periodetype = Periodetype.DAG,
                    perioder = listOf(
                        TestData.dag(2.jan),
                        TestData.utbetalingsperiode(fom = 1.jan, tom = 31.jan),
                    )
                )
            )
        }

        TestTopics.status.assertThat().hasValueMatching("${uid.id}", 1) {
            assertEquals("inkonsistens blant datoene i periodene", it.error!!.msg)
        }
        TestTopics.utbetalinger.assertThat().isEmpty()
    }

    @Test
    fun `error ved ulovlig fremtidig utbetaling`() {
        val uid = UtbetalingId(UUID.randomUUID())
        TestTopics.aap.produce("${uid.id}") {
            AapUtbetaling(
                action = Action.CREATE,
                data = TestData.utbetaling(
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    periodetype = Periodetype.DAG,
                    perioder = listOf(
                        TestData.dag(LocalDate.now().plusDays(1)),
                    )
                )
            )
        }

        TestTopics.status.assertThat().hasValueMatching("${uid.id}", 1) {
            assertEquals("fremtidige utbetalinger er ikke støttet for periode dag/ukedag", it.error!!.msg)
        }
        TestTopics.utbetalinger.assertThat().isEmpty()
    }

    @Test
    fun `error ved for lange perioder`() {
        val uid = UtbetalingId(UUID.randomUUID())
        TestTopics.aap.produce("${uid.id}") {
            AapUtbetaling(
                action = Action.CREATE,
                data = TestData.utbetaling(
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    periodetype = Periodetype.DAG,
                    perioder = (1L..93L).map {
                        TestData.dag(1.jan.minusDays(it))
                    }
                )
            )
        }

        TestTopics.status.assertThat().hasValueMatching("${uid.id}", 1) {
            assertEquals("DAG støtter maks periode på 92 dager", it.error!!.msg)
        }
        TestTopics.utbetalinger.assertThat().isEmpty()
    }
}
