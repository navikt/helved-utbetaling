package abetal

import abetal.models.*
import java.time.LocalDate
import kotlin.test.assertEquals
import models.*
import org.junit.jupiter.api.Test

internal class AetalTest {

    @Test
    fun `lagrer ny sakId`() {
        val uid = randomUtbetalingId()
        val sid = SakId("$nextInt")
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE, sid) {
                listOf(
                    Aap.dag(1.jan),
                    Aap.dag(2.jan),
                )
            }
        }
        TestTopics.saker.assertThat()
            .hasNumberOfRecordsForKey(SakKey(sid, Fagsystem.AAP), 1)
            .hasValueMatching(SakKey(sid, Fagsystem.AAP), 0) {
                assertEquals(uid, it.uids.single())
            }
    }

    @Test
    fun `appender uid på eksisterende sakId`() {
        val uid1 = randomUtbetalingId()
        val uid2 = randomUtbetalingId()
        val sid = SakId("$nextInt")
        TestTopics.aap.produce("${uid1.id}") {
            Aap.utbetaling(Action.CREATE, sid) {
                listOf(
                    Aap.dag(1.jan),
                    Aap.dag(2.jan),
                )
            }
        }
        TestTopics.aap.produce("${uid2.id}") {
            Aap.utbetaling(Action.CREATE, sid) {
                listOf(
                    Aap.dag(3.jan),
                    Aap.dag(6.jan),
                )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecords(2)
            .hasNumberOfRecordsForKey("${uid1.id}", 1)
            .hasNumberOfRecordsForKey("${uid2.id}", 1)
            .hasValueMatching("${uid1.id}", 0) {
                assertEquals(null, it.error)
            }
            .hasValueMatching("${uid2.id}", 0) {
                assertEquals(null, it.error)
            }

        TestTopics.saker.assertThat()
            .hasNumberOfRecords(2)
            .hasNumberOfRecordsForKey(SakKey(sid, Fagsystem.AAP), 2)
            .hasLastValue(SakKey(sid, Fagsystem.AAP)) {
                assertEquals(2, uids.size)
            }
    }

    @Test
    fun `setter andre utbetaling på sak til ENDR`() {
        val uid1 = randomUtbetalingId()
        val uid2 = randomUtbetalingId()
        val utbet = Aap.utbetaling(Action.CREATE) {
            listOf(
                Aap.dag(1.jan),
                Aap.dag(2.jan),
            )
        }
        TestTopics.aap.produce("${uid1.id}") { utbet }
        TestTopics.aap.produce("${uid2.id}") { utbet }

        TestTopics.oppdrag.assertThat()
            .hasValueMatching("${uid1.id}", 0) {
                assertEquals("NY", it.oppdrag110.kodeEndring)
            }
            .hasValueMatching("${uid2.id}", 0) {
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
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
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE) {
                listOf(
                    Aap.dag(31.des),
                    Aap.dag(1.jan),
                )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasValueMatching("${uid.id}", 0) {
                assertEquals(Status.FEILET, it.status)
                assertEquals("periode strekker seg over årsskifte", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().isEmptyForKey("${uid.id}")
        TestTopics.oppdrag.assertThat().isEmptyForKey("${uid.id}")
    }

    @Test
    fun `error ved to perioder med samme fom`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE) {
                listOf(
                    periode(fom = 1.jan, tom = 2.jan),
                    periode(fom = 1.jan, tom = 3.jan),
                )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasNumberOfRecords(1)
            .withLastValue {
                assertEquals("kan ikke sende inn duplikate perioder", it!!.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().isEmpty()
        TestTopics.oppdrag.assertThat().isEmpty()
    }

    @Test
    fun `error ved to perioder med samme tom`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE) {
                listOf(
                    periode(fom = 1.jan, tom = 2.jan),
                    periode(fom = 2.jan, tom = 2.jan),
                )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasNumberOfRecords(1)
            .withLastValue {
                assertEquals("kan ikke sende inn duplikate perioder", it!!.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().isEmpty()
        TestTopics.oppdrag.assertThat().isEmpty()
    }

    @Test
    fun `error ved tom før fom`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE) {
                listOf(
                    periode(fom = 2.jan, tom = 1.jan),
                )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasValueMatching("${uid.id}", 0) {
                assertEquals("fom må være før eller lik tom", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().isEmptyForKey("${uid.id}")
        TestTopics.oppdrag.assertThat().isEmptyForKey("${uid.id}")
    }

    @Test
    fun `error ved blanding av periodetyper`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE) {
                listOf(
                    Aap.dag(2.jan),
                    periode(fom = 1.jan, tom = 31.jan),
                )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasNumberOfRecords(1)
            .hasValueMatching("${uid.id}", 0) {
                assertEquals("inkonsistens blant datoene i periodene", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat()
            .isEmptyForKey("${uid.id}")
        TestTopics.oppdrag.assertThat()
            .isEmptyForKey("${uid.id}")
    }

    @Test
    fun `error ved ulovlig fremtidig utbetaling`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE) {
                listOf(
                    Aap.dag(LocalDate.now().plusDays(1)),
                )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasValueMatching("${uid.id}", 0) {
                assertEquals("fremtidige utbetalinger er ikke støttet for periode dag/ukedag", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().isEmptyForKey("${uid.id}")
        TestTopics.oppdrag.assertThat().isEmptyForKey("${uid.id}")
    }

    @Test
    fun `error ved for lange perioder`() {
        val uid = randomUtbetalingId()
        val sid = SakId("$nextInt")
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE, sid) {
                (1L..93L).map {
                    Aap.dag(1.jan.minusDays(it))
                }
            }.copy(periodetype = Periodetype.DAG)
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasValueMatching("${uid.id}", 0) {
                assertEquals("DAG støtter maks periode på 92 dager", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().isEmptyForKey("${uid.id}")
        TestTopics.oppdrag.assertThat().isEmptyForKey("${uid.id}")
    }
}
