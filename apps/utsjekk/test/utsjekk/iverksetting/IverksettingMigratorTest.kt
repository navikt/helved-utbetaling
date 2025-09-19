package utsjekk.iverksetting

import TestData
import TestRuntime
import httpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import libs.jdbc.concurrency.transaction
import models.kontrakter.felles.BrukersNavKontor
import models.kontrakter.felles.StønadTypeTilleggsstønader
import models.kontrakter.felles.StønadTypeTiltakspenger
import models.kontrakter.oppdrag.OppdragStatus
import org.junit.jupiter.api.Test
import utsjekk.Topics
import utsjekk.utbetaling.feb
import utsjekk.utbetaling.mar
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

class IverksettingMigratorTest {

    @Test
    fun `iverksetting not found`() = runTest(TestRuntime.context) {
        val sid = "s1"
        val bid = "b1"
        val iid = "i1"
        val meldeperiode = "123"

        val res = httpClient.post("/api/iverksetting/v2/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(sid, bid, iid, meldeperiode))
        }

        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `iverksettingresultat not found`() = runTest(TestRuntime.context) {
        val sid = RandomOSURId.generate()
        val bid = RandomOSURId.generate()
        val iid = RandomOSURId.generate()
        val meldeperiode = "123"

        transaction {
            TestData.dao.iverksetting(
                mottattTidspunkt = LocalDateTime.now().minusDays(2),
                iverksetting = TestData.domain.iverksetting(
                    fagsystem = models.kontrakter.felles.Fagsystem.TILLEGGSSTØNADER,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(bid),
                    iverksettingId = IverksettingId(iid),
                ),
            ).also { it.insert(utsjekk.utbetaling.UtbetalingId(UUID.randomUUID())) }
        }

        val res = httpClient.post("/api/iverksetting/v2/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(sid, bid, iid, meldeperiode))
        }

        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `pågående iverksetting er locked`() = runTest(TestRuntime.context) {
        val sid = RandomOSURId.generate()
        val bid = RandomOSURId.generate()
        val iid = RandomOSURId.generate()
        val meldeperiode = "123"

        transaction {
            TestData.dao.iverksetting(
                mottattTidspunkt = LocalDateTime.now().minusDays(2),
                iverksetting = TestData.domain.iverksetting(
                    fagsystem = models.kontrakter.felles.Fagsystem.TILLEGGSSTØNADER,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(bid),
                    iverksettingId = IverksettingId(iid),
                ),
            ).also { it.insert(utsjekk.utbetaling.UtbetalingId(UUID.randomUUID())) }

            TestData.dao.iverksettingResultat(
                fagsystem =models.kontrakter.felles.Fagsystem.TILLEGGSSTØNADER, 
                sakId = SakId(sid),
                behandlingId = BehandlingId(bid),
                iverksettingId = IverksettingId(iid),
            ).insert(utsjekk.utbetaling.UtbetalingId(UUID.randomUUID()))
        }

        val res = httpClient.post("/api/iverksetting/v2/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(sid, bid, iid, meldeperiode))
        }

        assertEquals(HttpStatusCode.Locked, res.status)

    }

    @Test
    fun `can migrate iverksetting for tilleggsstønader`() = runTest(TestRuntime.context) {
        val sid = RandomOSURId.generate()
        val bid = RandomOSURId.generate()
        val iid = RandomOSURId.generate()
        val pid = 4L
        val meldeperiode = "123"

        val personident = transaction {
            val iv = TestData.dao.iverksetting(
                mottattTidspunkt = LocalDateTime.now().minusDays(2),
                iverksetting = TestData.domain.iverksetting(
                    fagsystem = models.kontrakter.felles.Fagsystem.TILLEGGSSTØNADER,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(bid),
                    iverksettingId = IverksettingId(iid),
                    vedtakstidspunkt = 1.mar.atStartOfDay(),
                ),
            ).also { it.insert(utsjekk.utbetaling.UtbetalingId(UUID.randomUUID())) }


            TestData.dao.iverksettingResultat(
                fagsystem =models.kontrakter.felles.Fagsystem.TILLEGGSSTØNADER, 
                sakId = SakId(sid),
                behandlingId = BehandlingId(bid),
                iverksettingId = IverksettingId(iid),
                tilkjentYtelse = TestData.domain.enTilkjentYtelse(
                    listOf(
                        TestData.domain.enAndelTilkjentYtelse(
                            beløp = 500,
                            fom = 4.feb,
                            tom = 4.feb,
                            periodeId = pid,
                            stønadsdata = StønadsdataTilleggsstønader(StønadTypeTilleggsstønader.TILSYN_BARN_AAP)
                        )
                    )
                ),
                resultat = OppdragResultat(OppdragStatus.KVITTERT_OK),
            ).insert(utsjekk.utbetaling.UtbetalingId(UUID.randomUUID()))

            iv.data.personident
        }

        val res = httpClient.post("/api/iverksetting/v2/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(sid, bid, iid, meldeperiode))
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val utbetaling = TestRuntime.kafka.getProducer(Topics.utbetaling)
        assertEquals(0, utbetaling.uncommitted().size)
        val new_uid = tsUId(sid, meldeperiode, models.StønadTypeTilleggsstønader.TILSYN_BARN_AAP, models.Fagsystem.TILLEGGSSTØNADER)
        val actual = utbetaling.history() .single { (key, _) -> key == new_uid.toString() }.second
        assertEquals(false, actual.dryrun)
        assertEquals(iid, actual.originalKey)
        assertEquals(models.Fagsystem.TILLEGGSSTØNADER, actual.fagsystem)
        assertEquals(new_uid, actual.uid)
        assertEquals(models.Action.CREATE, actual.action)
        assertEquals(false, actual.førsteUtbetalingPåSak)
        assertEquals(models.SakId(sid), actual.sakId)
        assertEquals(models.BehandlingId(bid), actual.behandlingId)
        assertEquals(models.PeriodeId("$sid#$pid"), actual.lastPeriodeId)
        assertEquals(models.Personident(personident), actual.personident)
        assertEquals(1.mar.atStartOfDay(), actual.vedtakstidspunkt)
        assertEquals(models.StønadTypeTilleggsstønader.TILSYN_BARN_AAP, actual.stønad)
        assertEquals(TestData.DEFAULT_BESLUTTER, actual.beslutterId.ident)
        assertEquals(TestData.DEFAULT_SAKSBEHANDLER, actual.saksbehandlerId.ident)
        assertEquals(models.Periodetype.UKEDAG, actual.periodetype)
        assertEquals(null, actual.avvent)
        assertEquals(1, actual.perioder.size)
        assertEquals(500u, actual.perioder[0].beløp)
        assertEquals(null, actual.perioder[0].vedtakssats)
        assertEquals(4.feb, actual.perioder[0].fom)
        assertEquals(4.feb, actual.perioder[0].tom)
        assertEquals(null, actual.perioder[0].betalendeEnhet)
    }

    @Test
    fun `can migrate iverksetting for tiltakspenger`() = runTest(TestRuntime.context) {
        val sid = RandomOSURId.generate()
        val bid = RandomOSURId.generate()
        val iid = RandomOSURId.generate()
        val pid = 2L
        val meldeperiode = "432"

        val personident = transaction {
            val iv = TestData.dao.iverksetting(
                mottattTidspunkt = LocalDateTime.now().minusDays(2),
                iverksetting = TestData.domain.iverksetting(
                    fagsystem = models.kontrakter.felles.Fagsystem.TILTAKSPENGER,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(bid),
                    iverksettingId = IverksettingId(iid),
                    vedtakstidspunkt = 1.mar.atStartOfDay(),
                ),
            ).also { it.insert(utsjekk.utbetaling.UtbetalingId(UUID.randomUUID())) }


            TestData.dao.iverksettingResultat(
                fagsystem = models.kontrakter.felles.Fagsystem.TILTAKSPENGER, 
                sakId = SakId(sid),
                behandlingId = BehandlingId(bid),
                iverksettingId = IverksettingId(iid),
                tilkjentYtelse = TestData.domain.enTilkjentYtelse(
                    listOf(
                        TestData.domain.enAndelTilkjentYtelse(
                            beløp = 500,
                            fom = 4.feb,
                            tom = 4.feb,
                            periodeId = pid,
                            stønadsdata = StønadsdataTiltakspenger(
                                stønadstype = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, 
                                barnetillegg = false,
                                brukersNavKontor = BrukersNavKontor("1234"),
                                meldekortId = meldeperiode,
                            )
                        )
                    )
                ),
                resultat = OppdragResultat(OppdragStatus.KVITTERT_OK),
            ).insert(utsjekk.utbetaling.UtbetalingId(UUID.randomUUID()))

            iv.data.personident
        }

        val res = httpClient.post("/api/iverksetting/v2/migrate") {
            bearerAuth(TestRuntime.azure.generateToken(fakes.Azp.TILTAKSPENGER))
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(sid, bid, iid, meldeperiode))
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val utbetaling = TestRuntime.kafka.getProducer(Topics.utbetaling)
        assertEquals(0, utbetaling.uncommitted().size)
        val new_uid = tsUId(sid, meldeperiode, models.StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, models.Fagsystem.TILTAKSPENGER)
        val actual = utbetaling.history() .single { (key, _) -> key == new_uid.toString() }.second
        assertEquals(false, actual.dryrun)
        assertEquals(iid, actual.originalKey)
        assertEquals(models.Fagsystem.TILTAKSPENGER, actual.fagsystem)
        assertEquals(new_uid, actual.uid)
        assertEquals(models.Action.CREATE, actual.action)
        assertEquals(false, actual.førsteUtbetalingPåSak)
        assertEquals(models.SakId(sid), actual.sakId)
        assertEquals(models.BehandlingId(bid), actual.behandlingId)
        assertEquals(models.PeriodeId("$sid#$pid"), actual.lastPeriodeId)
        assertEquals(models.Personident(personident), actual.personident)
        assertEquals(1.mar.atStartOfDay(), actual.vedtakstidspunkt)
        assertEquals(models.StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, actual.stønad)
        assertEquals(TestData.DEFAULT_BESLUTTER, actual.beslutterId.ident)
        assertEquals(TestData.DEFAULT_SAKSBEHANDLER, actual.saksbehandlerId.ident)
        assertEquals(models.Periodetype.UKEDAG, actual.periodetype)
        assertEquals(null, actual.avvent)
        assertEquals(1, actual.perioder.size)
        assertEquals(500u, actual.perioder[0].beløp)
        assertEquals(null, actual.perioder[0].vedtakssats)
        assertEquals(4.feb, actual.perioder[0].fom)
        assertEquals(4.feb, actual.perioder[0].tom)
        assertEquals(null, actual.perioder[0].betalendeEnhet)
    }
}

