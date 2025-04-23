package abetal

import abetal.models.*
import java.time.LocalDate
import kotlin.test.assertEquals
import models.*
import org.junit.jupiter.api.Test

internal class AbetalTest {

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

    @Test
    fun `is idempotent`() {
        val uid = randomUtbetalingId()
        val utbet = Aap.utbetaling(Action.CREATE) {
            listOf(
                Aap.dag(1.jan),
                Aap.dag(3.jan),
            )
        }
        TestTopics.aap.produce("${uid.id}") { utbet }
        TestTopics.aap.produce("${uid.id}") { utbet }

        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey(uid.id.toString(), 1)
            .hasValueEquals(uid.id.toString(), 0) {
                StatusReply(Status.MOTTATT, null)
            }
        TestTopics.utbetalinger.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasValueMatching("${uid.id}", 0) {
                assertEquals(2, it.perioder.size)
            }
        TestTopics.oppdrag.assertThat()
            .hasValuesForPredicate(uid.id.toString(), 1) {
                it.oppdrag110.kodeEndring == "NY"
            }
        TestTopics.saker.assertThat()
            .hasNumberOfRecords(1)
            .hasNumberOfRecordsForKey(SakKey(utbet.sakId, Fagsystem.AAP), 1)
            .hasLastValue(SakKey(utbet.sakId, Fagsystem.AAP)) {
                assertEquals(1, uids.size)
            }
    }

    @Test
    fun `error ved årsskifte`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE, periodetype = Periodetype.EN_GANG) {
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
    fun `error ved for lang sakId`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE, sakId = SakId("123456789123456789123456789123456789")) {
                listOf(
                    Aap.dag(31.des),
                )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasValueMatching("${uid.id}", 0) {
                assertEquals(Status.FEILET, it.status)
                assertEquals("sakId kan være maks 30 tegn langt", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().isEmptyForKey("${uid.id}")
        TestTopics.oppdrag.assertThat().isEmptyForKey("${uid.id}")
    }

    @Test
    fun `error ved for lang behandlingId`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE, behId = BehandlingId("123456789123456789123456789123456789")) {
                listOf(
                    Aap.dag(31.des),
                )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasValueMatching("${uid.id}", 0) {
                assertEquals(Status.FEILET, it.status)
                assertEquals("behandlingId kan være maks 30 tegn langt", it.error!!.msg)
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
                    Aap.dag(LocalDate.now().nesteVirkedag()),
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
                (1L..1001L).map {
                    Aap.dag(1.jan.minusDays(it))
                }
            }.copy(periodetype = Periodetype.DAG)
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasValueMatching("${uid.id}", 0) {
                assertEquals("DAG støtter maks periode på 1000 dager", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().isEmptyForKey("${uid.id}")
        TestTopics.oppdrag.assertThat().isEmptyForKey("${uid.id}")
    }

    @Test
    fun `error ved manglende perioder`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
                Aap.utbetaling(Action.CREATE) {
                    listOf()
                }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasValueMatching("${uid.id}", 0) {
                assertEquals("perioder kan ikke være tom", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().isEmptyForKey("${uid.id}")
        TestTopics.oppdrag.assertThat().isEmptyForKey("${uid.id}")
    }
}
