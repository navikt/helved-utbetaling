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
            setBody(MigrationRequest(sid, bid, iid, meldeperiode, null))
        }

        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `kan ikke migrere når både meldeperiode og uid mangler`() = runTest(TestRuntime.context) {
        val sid = "s1"
        val bid = "b1"
        val iid = "i1"

        val res = httpClient.post("/api/iverksetting/v2/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(sid, bid, iid, null, null))
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun `iverksettingresultat not found`() = runTest(TestRuntime.context) {
        val sid = RandomOSURId.generate()
        val bid = RandomOSURId.generate()
        val iid = RandomOSURId.generate()
        val uid = UUID.randomUUID()

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
            setBody(MigrationRequest(sid, bid, iid, null, uid))
        }

        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `pågående iverksetting er locked`() = runTest(TestRuntime.context) {
        val sid = RandomOSURId.generate()
        val bid = RandomOSURId.generate()
        val iid = RandomOSURId.generate()
        val uid = UUID.randomUUID()

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
            setBody(MigrationRequest(sid, bid, iid, null, uid))
        }

        assertEquals(HttpStatusCode.Locked, res.status)

    }

    @Test
    fun `can migrate iverksetting for tilleggsstønader`() = runTest(TestRuntime.context) {
        val sid = RandomOSURId.generate()
        val bid = RandomOSURId.generate()
        val iid = RandomOSURId.generate()
        val pid = 4L
        val uid = UUID.randomUUID()

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
                fagsystem = models.kontrakter.felles.Fagsystem.TILLEGGSSTØNADER, 
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
            setBody(MigrationRequest(sid, bid, iid, null, uid))
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val utbetaling = TestRuntime.kafka.getProducer(Topics.utbetaling)
        assertEquals(0, utbetaling.uncommitted().size)
        val new_uid = models.UtbetalingId(uid)
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
                            fom = 1.feb,
                            tom = 14.feb,
                            periodeId = pid,
                            stønadsdata = StønadsdataTiltakspenger(
                                stønadstype = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, 
                                barnetillegg = false,
                                brukersNavKontor = BrukersNavKontor("1234"),
                                meldekortId = meldeperiode,
                            )
                        ),
                        TestData.domain.enAndelTilkjentYtelse(
                            beløp = 500,
                            fom = 15.feb,
                            tom = 28.feb,
                            periodeId = pid,
                            stønadsdata = StønadsdataTiltakspenger(
                                stønadstype = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, 
                                barnetillegg = false,
                                brukersNavKontor = BrukersNavKontor("1234"),
                                meldekortId = "en-annen-meldekortId"
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
            setBody(MigrationRequest(sid, bid, iid, meldeperiode, null))
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val utbetaling = TestRuntime.kafka.getProducer(Topics.utbetaling)
        assertEquals(0, utbetaling.uncommitted().size)
        val new_uid = uid(sid, meldeperiode, models.StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, models.Fagsystem.TILTAKSPENGER)
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
        assertEquals(1.feb, actual.perioder[0].fom)
        assertEquals(14.feb, actual.perioder[0].tom)
        assertEquals(null, actual.perioder[0].betalendeEnhet)
    }

    @Test
    fun `can migrate iverksetting for tiltakspenger med to perioder`() = runTest(TestRuntime.context) {
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
                            fom = 1.feb,
                            tom = 14.feb,
                            periodeId = pid,
                            stønadsdata = StønadsdataTiltakspenger(
                                stønadstype = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, 
                                barnetillegg = false,
                                brukersNavKontor = BrukersNavKontor("1234"),
                                meldekortId = meldeperiode,
                            )
                        ),
                        TestData.domain.enAndelTilkjentYtelse(
                            beløp = 500,
                            fom = 15.feb,
                            tom = 28.feb,
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
            setBody(MigrationRequest(sid, bid, iid, meldeperiode, null))
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val utbetaling = TestRuntime.kafka.getProducer(Topics.utbetaling)
        assertEquals(0, utbetaling.uncommitted().size)
        val new_uid = uid(sid, meldeperiode, models.StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, models.Fagsystem.TILTAKSPENGER)
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
        assertEquals(2, actual.perioder.size)
        assertEquals(500u, actual.perioder[0].beløp)
        assertEquals(null, actual.perioder[0].vedtakssats)
        assertEquals(1.feb, actual.perioder[0].fom)
        assertEquals(14.feb, actual.perioder[0].tom)
        assertEquals(null, actual.perioder[0].betalendeEnhet)
        assertEquals(500u, actual.perioder[1].beløp)
        assertEquals(null, actual.perioder[1].vedtakssats)
        assertEquals(15.feb, actual.perioder[1].fom)
        assertEquals(28.feb, actual.perioder[1].tom)
        assertEquals(null, actual.perioder[1].betalendeEnhet)
    }

    @Test
    fun `can migrate iverksetting for tiltakspenger med to klassekoder`() = runTest(TestRuntime.context) {
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
                            fom = 1.feb,
                            tom = 14.feb,
                            periodeId = pid,
                            stønadsdata = StønadsdataTiltakspenger(
                                stønadstype = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, 
                                barnetillegg = false,
                                brukersNavKontor = BrukersNavKontor("1234"),
                                meldekortId = meldeperiode,
                            )
                        ),
                        TestData.domain.enAndelTilkjentYtelse(
                            beløp = 700,
                            fom = 1.feb,
                            tom = 14.feb,
                            periodeId = pid,
                            stønadsdata = StønadsdataTiltakspenger(
                                stønadstype = StønadTypeTiltakspenger.DIGITAL_JOBBKLUBB, 
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
            setBody(MigrationRequest(sid, bid, iid, meldeperiode, null))
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val utbetaling = TestRuntime.kafka.getProducer(Topics.utbetaling)
        assertEquals(0, utbetaling.uncommitted().size)
        val uid1 = uid(sid, meldeperiode, models.StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, models.Fagsystem.TILTAKSPENGER)
        val actual1 = utbetaling.history() .single { (key, _) -> key == uid1.toString() }.second
        assertEquals(false, actual1.dryrun)
        assertEquals(iid, actual1.originalKey)
        assertEquals(models.Fagsystem.TILTAKSPENGER, actual1.fagsystem)
        assertEquals(uid1, actual1.uid)
        assertEquals(models.Action.CREATE, actual1.action)
        assertEquals(false, actual1.førsteUtbetalingPåSak)
        assertEquals(models.SakId(sid), actual1.sakId)
        assertEquals(models.BehandlingId(bid), actual1.behandlingId)
        assertEquals(models.PeriodeId("$sid#$pid"), actual1.lastPeriodeId)
        assertEquals(models.Personident(personident), actual1.personident)
        assertEquals(1.mar.atStartOfDay(), actual1.vedtakstidspunkt)
        assertEquals(models.StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, actual1.stønad)
        assertEquals(TestData.DEFAULT_BESLUTTER, actual1.beslutterId.ident)
        assertEquals(TestData.DEFAULT_SAKSBEHANDLER, actual1.saksbehandlerId.ident)
        assertEquals(models.Periodetype.UKEDAG, actual1.periodetype)
        assertEquals(null, actual1.avvent)
        assertEquals(1, actual1.perioder.size)
        assertEquals(500u, actual1.perioder[0].beløp)
        assertEquals(null, actual1.perioder[0].vedtakssats)
        assertEquals(1.feb, actual1.perioder[0].fom)
        assertEquals(14.feb, actual1.perioder[0].tom)
        assertEquals(null, actual1.perioder[0].betalendeEnhet)

        val uid2 = uid(sid, meldeperiode, models.StønadTypeTiltakspenger.DIGITAL_JOBBKLUBB, models.Fagsystem.TILTAKSPENGER)
        val actual2 = utbetaling.history() .single { (key, _) -> key == uid2.toString() }.second
        assertEquals(false, actual2.dryrun)
        assertEquals(iid, actual2.originalKey)
        assertEquals(models.Fagsystem.TILTAKSPENGER, actual2.fagsystem)
        assertEquals(uid2, actual2.uid)
        assertEquals(models.Action.CREATE, actual2.action)
        assertEquals(false, actual2.førsteUtbetalingPåSak)
        assertEquals(models.SakId(sid), actual2.sakId)
        assertEquals(models.BehandlingId(bid), actual2.behandlingId)
        assertEquals(models.PeriodeId("$sid#$pid"), actual2.lastPeriodeId)
        assertEquals(models.Personident(personident), actual2.personident)
        assertEquals(1.mar.atStartOfDay(), actual2.vedtakstidspunkt)
        assertEquals(models.StønadTypeTiltakspenger.DIGITAL_JOBBKLUBB, actual2.stønad)
        assertEquals(TestData.DEFAULT_BESLUTTER, actual2.beslutterId.ident)
        assertEquals(TestData.DEFAULT_SAKSBEHANDLER, actual2.saksbehandlerId.ident)
        assertEquals(models.Periodetype.UKEDAG, actual2.periodetype)
        assertEquals(null, actual2.avvent)
        assertEquals(1, actual2.perioder.size)
        assertEquals(700u, actual2.perioder[0].beløp)
        assertEquals(null, actual2.perioder[0].vedtakssats)
        assertEquals(1.feb, actual2.perioder[0].fom)
        assertEquals(14.feb, actual2.perioder[0].tom)
        assertEquals(null, actual2.perioder[0].betalendeEnhet)
    }
}

