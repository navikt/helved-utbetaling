package utsjekk.utbetaling

import TestRuntime
import httpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import libs.jdbc.concurrency.transaction
import org.junit.jupiter.api.Test
import utsjekk.Topics
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UtbetalingMigratorTest {

    @Test
    fun `uid not found`() = runTest(TestRuntime.context) {
        val uid = UUID.randomUUID()

        val res = httpClient.post("/utbetalinger/$uid/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(uid))
        }

        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `can migrate uid`() = runTest(TestRuntime.context) {
        val uid = UUID.randomUUID()
        val data = Utbetaling.aap(
            vedtakstidspunkt = 1.mar, 
            satstype = Satstype.ENGANGS,
            perioder = listOf(Utbetalingsperiode.dagpenger(4.feb, 4.feb, 500u)),
        )
        val new_uid = models.UtbetalingId(UUID.randomUUID())

        transaction {
            UtbetalingDao(data, Status.OK).insert(UtbetalingId(uid))
        }

        val res = httpClient.post("/utbetalinger/$uid/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(new_uid.id))
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val utbetaling = TestRuntime.kafka.getProducer(Topics.utbetaling)
        assertEquals(0, utbetaling.uncommitted().size)
        val actual = utbetaling.history() .single { (key, _) -> key == new_uid.toString() }.second

        assertEquals(false, actual.dryrun)
        assertEquals(uid.toString(), actual.originalKey)
        assertEquals(models.Fagsystem.AAP, actual.fagsystem)
        assertEquals(new_uid, actual.uid)
        assertEquals(models.Action.CREATE, actual.action)
        assertEquals(false, actual.førsteUtbetalingPåSak)
        assertEquals(models.SakId(data.sakId.id), actual.sakId)
        assertEquals(models.BehandlingId(data.behandlingId.id), actual.behandlingId)
        assertEquals(models.PeriodeId.decode(data.lastPeriodeId.toString()), actual.lastPeriodeId)
        assertEquals(models.Personident(data.personident.ident), actual.personident)
        assertEquals(1.mar.atStartOfDay(), actual.vedtakstidspunkt)
        assertEquals(models.StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING, actual.stønad)
        assertEquals(TestData.DEFAULT_BESLUTTER, actual.beslutterId.ident)
        assertEquals(TestData.DEFAULT_SAKSBEHANDLER, actual.saksbehandlerId.ident)
        assertEquals(models.Periodetype.UKEDAG, actual.periodetype)
        assertEquals(null, actual.avvent)
        assertEquals(1, actual.perioder.size)
        assertEquals(500u, actual.perioder[0].beløp)
        assertEquals(500u, actual.perioder[0].vedtakssats)
        assertEquals(4.feb, actual.perioder[0].fom)
        assertEquals(4.feb, actual.perioder[0].tom)
        assertEquals(null, actual.perioder[0].betalendeEnhet)
    }

    @Test
    fun `migrate uid uses sak-level avvent`() = runTest(TestRuntime.context) {
        val sakId = SakId("sak-avvent-${UUID.randomUUID()}")
        val uid1 = UUID.randomUUID()
        val uid2 = UUID.randomUUID()

        val avvent = Avvent(
            fom = 1.feb,
            tom = 28.feb,
            årsak = Årsak.AVVENT_AVREGNING,
        )

        // First utbetaling without avvent
        val data1 = Utbetaling.aap(
            vedtakstidspunkt = 1.mar,
            sakId = sakId,
            satstype = Satstype.ENGANGS,
            perioder = listOf(Utbetalingsperiode.dagpenger(4.feb, 4.feb, 500u)),
        )
        // Second utbetaling with avvent (later created_at)
        val data2 = Utbetaling.aap(
            vedtakstidspunkt = 1.mar,
            sakId = sakId,
            satstype = Satstype.ENGANGS,
            perioder = listOf(Utbetalingsperiode.dagpenger(5.feb, 5.feb, 600u)),
            avvent = avvent,
        )

        transaction {
            UtbetalingDao(data1, Status.OK).insert(UtbetalingId(uid1))
            UtbetalingDao(data2, Status.OK).insert(UtbetalingId(uid2))
        }

        val res = httpClient.post("/utbetalinger/$uid1/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(uid1))
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val utbetaling = TestRuntime.kafka.getProducer(Topics.utbetaling)
        val actual = utbetaling.history().single { (key, _) -> key == uid1.toString() }.second

        // Avvent from the second utbetaling (latest on sak) should be used
        assertNotNull(actual.avvent, "avvent should be set from sak-level lookup")
        assertEquals(1.feb, actual.avvent!!.fom)
        assertEquals(28.feb, actual.avvent!!.tom)
        assertEquals(models.Årsak.AVVENT_AVREGNING, actual.avvent!!.årsak)
    }

    @Test
    fun `batch migrate transfers all items with sak-level avvent`() = runTest(TestRuntime.context) {
        val sakId = SakId("sak-batch-${UUID.randomUUID()}")
        val uid1 = UUID.randomUUID()
        val uid2 = UUID.randomUUID()

        val avvent = Avvent(
            fom = 1.feb,
            tom = 28.feb,
            årsak = Årsak.AVVENT_REFUSJONSKRAV,
        )

        val data1 = Utbetaling.aap(
            vedtakstidspunkt = 1.mar,
            sakId = sakId,
            satstype = Satstype.ENGANGS,
            perioder = listOf(Utbetalingsperiode.dagpenger(4.feb, 4.feb, 500u)),
            avvent = avvent,
        )
        val data2 = Utbetaling.aap(
            vedtakstidspunkt = 1.mar,
            sakId = sakId,
            satstype = Satstype.ENGANGS,
            perioder = listOf(Utbetalingsperiode.dagpenger(5.feb, 5.feb, 600u)),
        )

        transaction {
            UtbetalingDao(data1, Status.OK).insert(UtbetalingId(uid1))
            UtbetalingDao(data2, Status.OK).insert(UtbetalingId(uid2))
        }

        val newUid1 = models.UtbetalingId(UUID.randomUUID())
        val newUid2 = models.UtbetalingId(UUID.randomUUID())

        val res = httpClient.post("/utbetalinger/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(MigrationBatchRequest(listOf(
                MigrationBatchItem(uid1, newUid1.id),
                MigrationBatchItem(uid2, newUid2.id),
            )))
        }

        assertEquals(HttpStatusCode.OK, res.status)

        val producer = TestRuntime.kafka.getProducer(Topics.utbetaling)
        val history = producer.history()

        val actual1 = history.single { (key, _) -> key == newUid1.toString() }.second
        val actual2 = history.single { (key, _) -> key == newUid2.toString() }.second

        // Both should have the sak-level avvent from data1
        assertNotNull(actual1.avvent, "first migrated utbetaling should have sak-level avvent")
        assertEquals(models.Årsak.AVVENT_REFUSJONSKRAV, actual1.avvent!!.årsak)

        assertNotNull(actual2.avvent, "second migrated utbetaling should have sak-level avvent")
        assertEquals(models.Årsak.AVVENT_REFUSJONSKRAV, actual2.avvent!!.årsak)
    }

    @Test
    fun `batch migrate validates items from different saker`() = runTest(TestRuntime.context) {
        val uid1 = UUID.randomUUID()
        val uid2 = UUID.randomUUID()

        val data1 = Utbetaling.aap(
            vedtakstidspunkt = 1.mar,
            sakId = SakId("sak-A-${UUID.randomUUID()}"),
            satstype = Satstype.ENGANGS,
            perioder = listOf(Utbetalingsperiode.dagpenger(4.feb, 4.feb, 500u)),
        )
        val data2 = Utbetaling.aap(
            vedtakstidspunkt = 1.mar,
            sakId = SakId("sak-B-${UUID.randomUUID()}"),
            satstype = Satstype.ENGANGS,
            perioder = listOf(Utbetalingsperiode.dagpenger(5.feb, 5.feb, 600u)),
        )

        transaction {
            UtbetalingDao(data1, Status.OK).insert(UtbetalingId(uid1))
            UtbetalingDao(data2, Status.OK).insert(UtbetalingId(uid2))
        }

        val res = httpClient.post("/utbetalinger/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(MigrationBatchRequest(listOf(
                MigrationBatchItem(uid1, id = UUID.randomUUID()),
                MigrationBatchItem(uid2, id = UUID.randomUUID()),
            )))
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun `batch migrate validates empty items`() = runTest(TestRuntime.context) {
        val res = httpClient.post("/utbetalinger/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(MigrationBatchRequest(emptyList()))
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
    }
}
