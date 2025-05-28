package abetal

import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import models.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

internal class AbetalTest {

    @Test
    fun `lagrer ny sakId`() {
        val uid = randomUtbetalingId()
        val sid = SakId("$nextInt")
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE, sid) {
                Aap.dag(1.jan) +
                Aap.dag(2.jan)
            }
        }
        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP))
            .with(SakKey(sid, Fagsystem.AAP)) {
                assertEquals(uid, it.single())
            }
    }

    @Test
    fun `appender uid på eksisterende sakId`() {
        val uid1 = randomUtbetalingId()
        val uid2 = randomUtbetalingId()
        val sid = SakId("$nextInt")
        TestTopics.aap.produce("${uid1.id}") {
            Aap.utbetaling(Action.CREATE, sid) {
                Aap.dag(1.jan) +
                Aap.dag(2.jan)
            }
        }
        TestTopics.aap.produce("${uid2.id}") {
            Aap.utbetaling(Action.CREATE, sid) {
                Aap.dag(3.jan) +
                Aap.dag(6.jan)
            }
        }
        TestTopics.status.assertThat()
            .hasTotal(2)
            .has("${uid1.id}")
            .has("${uid2.id}")
            .with("${uid1.id}") {
                assertEquals(null, it.error)
            }
            .with("${uid2.id}") {
                assertEquals(null, it.error)
            }

        TestTopics.saker.assertThat()
            .hasTotal(2)
            .has(SakKey(sid, Fagsystem.AAP), 2)
            .with(SakKey(sid, Fagsystem.AAP), index = 0) {
                assertEquals(1, it.size)
            }
            .with(SakKey(sid, Fagsystem.AAP), index = 1) {
                assertEquals(2, it.size)
            }
    }

    @Test
    fun `setter andre utbetaling på sak til ENDR`() {
        val uid1 = randomUtbetalingId()
        val uid2 = randomUtbetalingId()
        val utbet = Aap.utbetaling(Action.CREATE) {
            Aap.dag(1.jan) +
            Aap.dag(2.jan)
        }
        TestTopics.aap.produce("${uid1.id}") { utbet }
        TestTopics.aap.produce("${uid2.id}") { utbet }

        TestTopics.oppdrag.assertThat()
            .with("${uid1.id}") {
                assertEquals("NY", it.oppdrag110.kodeEndring)
            }
            .with("${uid2.id}") {
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
            }
    }

    @Test
    fun `is idempotent`() {
        val uid = randomUtbetalingId()
        val utbet = Aap.utbetaling(Action.CREATE) {
            Aap.dag(1.jan) + 
            Aap.dag(3.jan)
        }
        TestTopics.aap.produce("${uid.id}") { utbet }
        TestTopics.aap.produce("${uid.id}") { utbet }

        TestTopics.status.assertThat()
            .has(uid.id.toString(), StatusReply(Status.MOTTATT, null)) 
        TestTopics.utbetalinger.assertThat()
            .has("${uid.id}")
            .with("${uid.id}") {
                assertEquals(2, it.perioder.size)
            }
        TestTopics.oppdrag.assertThat()
            .with(uid.id.toString()) {
                it.oppdrag110.kodeEndring == "NY"
            }
        TestTopics.saker.assertThat()
            .hasTotal(1)
            .has(SakKey(utbet.sakId, Fagsystem.AAP))
            .with(SakKey(utbet.sakId, Fagsystem.AAP)) {
                assertEquals(1, it.size)
            }
    }

    @Test
    fun `error ved årsskifte`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE, periodetype = Periodetype.EN_GANG) {
                Aap.dag(31.des) +
                Aap.dag(1.jan)
            }
        }
        TestTopics.status.assertThat()
            .has("${uid.id}")
            .with("${uid.id}") {
                assertEquals(Status.FEILET, it.status)
                assertEquals("periode strekker seg over årsskifte", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().hasNot("${uid.id}")
        TestTopics.oppdrag.assertThat().hasNot("${uid.id}")
    }

    @Test
    fun `error ved for lang sakId`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE, sakId = SakId("123456789123456789123456789123456789")) {
                Aap.dag(31.des)
            }
        }
        TestTopics.status.assertThat()
            .has("${uid.id}")
            .with("${uid.id}") {
                assertEquals(Status.FEILET, it.status)
                assertEquals("sakId kan være maks 30 tegn langt", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().hasNot("${uid.id}")
        TestTopics.oppdrag.assertThat().hasNot("${uid.id}")
    }

    @Test
    fun `error ved for lang behandlingId`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE, behId = BehandlingId("123456789123456789123456789123456789")) {
                Aap.dag(31.des)
            }
        }
        TestTopics.status.assertThat()
            .has("${uid.id}")
            .with("${uid.id}") {
                assertEquals(Status.FEILET, it.status)
                assertEquals("behandlingId kan være maks 30 tegn langt", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().hasNot("${uid.id}")
        TestTopics.oppdrag.assertThat().hasNot("${uid.id}")
    }

    @Test
    fun `error ved to perioder med samme fom`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE) {
                periode(fom = 1.jan, tom = 2.jan) +
                periode(fom = 1.jan, tom = 3.jan)
            }
        }
        TestTopics.status.assertThat()
            .hasTotal(1)
            .has("${uid.id}", 1)
            .with("${uid.id}") {
                assertEquals("kan ikke sende inn duplikate perioder", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().isEmpty()
        TestTopics.oppdrag.assertThat().isEmpty()
    }

    @Test
    fun `error ved to perioder med samme tom`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE) {
                periode(fom = 1.jan, tom = 2.jan) +
                periode(fom = 2.jan, tom = 2.jan)
            }
        }
        TestTopics.status.assertThat()
            .hasTotal(1)
            .has("${uid.id}")
            .with("${uid.id}") {
                assertEquals("kan ikke sende inn duplikate perioder", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().isEmpty()
        TestTopics.oppdrag.assertThat().isEmpty()
    }

    @Test
    fun `error ved tom før fom`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE) {
                periode(fom = 2.jan, tom = 1.jan)
            }
        }
        TestTopics.status.assertThat()
            .has("${uid.id}")
            .with("${uid.id}") {
                assertEquals("fom må være før eller lik tom", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().hasNot("${uid.id}")
        TestTopics.oppdrag.assertThat().hasNot("${uid.id}")
    }

    @Test
    fun `error ved blanding av periodetyper`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE) {
                Aap.dag(2.jan) +
                periode(fom = 1.jan, tom = 31.jan)
            }
        }
        TestTopics.status.assertThat()
            .hasTotal(1)
            .has("${uid.id}")
            .with("${uid.id}") {
                assertEquals("inkonsistens blant datoene i periodene", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().hasNot("${uid.id}")
        TestTopics.oppdrag.assertThat().hasNot("${uid.id}")
    }

    @Test
    fun `error ved ulovlig fremtidig utbetaling`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE) {
                Aap.dag(LocalDate.now().nesteVirkedag())
            }
        }
        TestTopics.status.assertThat()
            .has("${uid.id}")
            .with("${uid.id}") {
                assertEquals("fremtidige utbetalinger er ikke støttet for periode dag/ukedag", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().hasNot("${uid.id}")
        TestTopics.oppdrag.assertThat().hasNot("${uid.id}")
    }

    @Test
    fun `error ved for lange perioder`() {
        val uid = randomUtbetalingId()
        val sid = SakId("$nextInt")
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE, sid) {
                (1L..1001L).fold(emptyList()) { acc, next ->
                    acc + Aap.dag(1.jan.minusDays(next))
                }
            }.copy(periodetype = Periodetype.DAG)
        }
        TestTopics.status.assertThat()
            .has("${uid.id}")
            .with("${uid.id}") {
                assertEquals("DAG støtter maks periode på 1000 dager", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().hasNot("${uid.id}")
        TestTopics.oppdrag.assertThat().hasNot("${uid.id}")
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
            .has("${uid.id}")
            .with("${uid.id}") {
                assertEquals("perioder kan ikke være tom", it.error!!.msg)
            }
        TestTopics.utbetalinger.assertThat().hasNot("${uid.id}")
        TestTopics.oppdrag.assertThat().hasNot("${uid.id}")
    }

    @Test
    fun `get utbetaling from api`() {
        val uid = randomUtbetalingId()
        TestTopics.aap.produce("${uid.id}") {
            Aap.utbetaling(Action.CREATE) {
                Aap.dag(1.jan)
            }
        }
        TestTopics.saker.assertThat().hasTotal(1)

        val res = runBlocking {
            httpClient.get("/api/utbetalinger/$uid") {
                accept(ContentType.Application.Json)
            }
        }

        assertEquals(HttpStatusCode.OK, res.status)
    }
}

