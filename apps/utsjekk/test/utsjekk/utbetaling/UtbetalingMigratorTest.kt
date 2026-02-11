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

class UtbetalingMigratorTest {

    @Test
    fun `uid not found`() = runTest(TestRuntime.context) {
        val uid = UUID.randomUUID()
        val meldeperiode = "123"

        val res = httpClient.post("/utbetalinger/$uid/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            // setBody(MigrationRequest(meldeperiode, LocalDate.now(), LocalDate.now()))
            setBody(MigrationRequest(meldeperiode))
        }

        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `can migrate uid`() = runTest(TestRuntime.context) {
        val meldeperiode = "123"
        val uid = UUID.randomUUID()
        val data = Utbetaling.aap(
            vedtakstidspunkt = 1.mar, 
            satstype = Satstype.ENGANGS,
            perioder = listOf(Utbetalingsperiode.dagpenger(4.feb, 4.feb, 500u)),
        )
        val new_uid = models.aapUId(data.sakId.id, meldeperiode, models.StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        transaction {
            UtbetalingDao(data, Status.OK).insert(UtbetalingId(uid))
        }

        val res = httpClient.post("/utbetalinger/$uid/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            // setBody(MigrationRequest(meldeperiode, 4.feb, 4.feb))
            setBody(MigrationRequest(meldeperiode))
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
}

