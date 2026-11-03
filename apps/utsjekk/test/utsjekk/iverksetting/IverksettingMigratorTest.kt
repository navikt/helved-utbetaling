package utsjekk.iverksetting

import TestData
import TestRuntime
import httpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import libs.jdbc.concurrency.transaction
import models.kontrakter.BrukersNavKontor
import models.kontrakter.StønadTypeTiltakspenger
import org.junit.jupiter.api.Test
import utsjekk.Topics
import utsjekk.utbetaling.feb
import utsjekk.utbetaling.mar
import java.time.LocalDateTime
import java.util.*
import models.NavEnhet
import models.kontrakter.Fagsystem
import kotlin.test.assertEquals

class IverksettingMigratorTest {

    @Test
    fun `iverksetting not found`() = runTest(TestRuntime.context) {
        val sid = "s1"
        val bid = "b1"

        val res = httpClient.post("/api/iverksetting/v2/migrate") {
            bearerAuth(TestRuntime.azure.generateToken("tiltakspenger-saksbehandling-api"))
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(sid, bid))
        }

        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `iverksettingresultat not found`() = runTest(TestRuntime.context) {
        val sid = RandomOSURId.generate()
        val bid = RandomOSURId.generate()
        val iid = RandomOSURId.generate()

        transaction {
            TestData.dao.iverksetting(
                mottattTidspunkt = LocalDateTime.now().minusDays(2),
                iverksetting = TestData.domain.iverksetting(
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(bid),
                    iverksettingId = IverksettingId(iid),
                ),
            ).also { it.insert(utsjekk.utbetaling.UtbetalingId(UUID.randomUUID())) }
        }

        val res = httpClient.post("/api/iverksetting/v2/migrate") {
            bearerAuth(TestRuntime.azure.generateToken(fakes.Azp.TILTAKSPENGER))
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(sid, bid))
        }

        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `pågående iverksetting er locked`() = runTest(TestRuntime.context) {
        val sid = RandomOSURId.generate()
        val bid = RandomOSURId.generate()

        transaction {
            TestData.dao.iverksetting(
                mottattTidspunkt = LocalDateTime.now().minusDays(2),
                iverksetting = TestData.domain.iverksetting(
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(bid),
                ),
            ).also { it.insert(utsjekk.utbetaling.UtbetalingId(UUID.randomUUID())) }

            TestData.dao.iverksettingResultat(
                fagsystem = Fagsystem.TILTAKSPENGER,
                sakId = SakId(sid),
                behandlingId = BehandlingId(bid),
            ).insert(utsjekk.utbetaling.UtbetalingId(UUID.randomUUID()))
        }

        val res = httpClient.post("/api/iverksetting/v2/migrate") {
            bearerAuth(TestRuntime.azure.generateToken(fakes.Azp.TILTAKSPENGER))
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(sid, bid))
        }

        assertEquals(HttpStatusCode.Locked, res.status)

    }

    @Test
    fun `can migrate iverksetting for tilleggsstønader`() = runTest(TestRuntime.context) {
        val sid = RandomOSURId.generate()
        val bid = RandomOSURId.generate()
        val pid = 4L
        val uid = uid(sakId = sid, "meldeperiode", models.StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, models.Fagsystem.TILTAKSPENGER)


        val personident = transaction {
            val iv = TestData.dao.iverksetting(
                mottattTidspunkt = LocalDateTime.now().minusDays(2),
                iverksetting = TestData.domain.iverksetting(
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(bid),
                    vedtakstidspunkt = 1.mar.atStartOfDay(),
                ),
            ).also { it.insert(utsjekk.utbetaling.UtbetalingId(uid.id)) }

            TestData.dao.iverksettingResultat(
                fagsystem = Fagsystem.TILTAKSPENGER,
                sakId = SakId(sid),
                behandlingId = BehandlingId(bid),
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
                                meldekortId = "meldeperiode",
                            )
                        ),
                    )
                ),
                resultat = OppdragResultat(OppdragStatus.KVITTERT_OK),
            ).insert(utsjekk.utbetaling.UtbetalingId(uid.id))

            iv.data.personident
        }

        val res = httpClient.post("/api/iverksetting/v2/migrate") {
            bearerAuth(TestRuntime.azure.generateToken(fakes.Azp.TILTAKSPENGER))
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(sid, bid))
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val utbetaling = TestRuntime.kafka.getProducer(Topics.utbetaling)
        assertEquals(0, utbetaling.uncommitted().size)
        val actual = utbetaling.history().single { (key, _) -> key == uid.toString() }.second
        assertEquals(false, actual.dryrun)
        assertEquals(models.Fagsystem.TILTAKSPENGER, actual.fagsystem)
        assertEquals(uid, actual.uid)
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
        assertEquals(NavEnhet("1234"), actual.perioder[0].betalendeEnhet)
    }

    @Test
    fun `can migrate iverksetting with multiple klassekoder for tiltakspenger`() = runTest(TestRuntime.context) {
        val sid = RandomOSURId.generate()
        val bid = RandomOSURId.generate()
        val pid = 4L
        val uid1 = uid(sakId = sid, "meldeperiode", models.StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, models.Fagsystem.TILTAKSPENGER)
        val uid2 = uid(sakId = sid, "meldeperiode-2", models.StønadTypeTiltakspenger.ARBEIDSMARKEDSOPPLÆRING_AMO, models.Fagsystem.TILTAKSPENGER)


        val personident = transaction {
            val iv = TestData.dao.iverksetting(
                mottattTidspunkt = LocalDateTime.now().minusDays(2),
                iverksetting = TestData.domain.iverksetting(
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(bid),
                    vedtakstidspunkt = 1.mar.atStartOfDay(),
                ),
            ).also { it.insert(utsjekk.utbetaling.UtbetalingId(uid1.id)) }


            TestData.dao.iverksettingResultat(
                fagsystem = Fagsystem.TILTAKSPENGER,
                sakId = SakId(sid),
                behandlingId = BehandlingId(bid),
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
                                meldekortId = "meldeperiode",
                            )
                        ),
                        TestData.domain.enAndelTilkjentYtelse(
                            beløp = 800,
                            fom = 1.mar,
                            tom = 1.mar,
                            periodeId = pid + 1,
                            stønadsdata = StønadsdataTiltakspenger(
                                stønadstype = StønadTypeTiltakspenger.ARBEIDSMARKEDSOPPLÆRING_AMO,
                                barnetillegg = false,
                                brukersNavKontor = BrukersNavKontor("1234"),
                                meldekortId = "meldeperiode-2",
                            )
                        ),
                    )
                ),
                resultat = OppdragResultat(OppdragStatus.KVITTERT_OK),
            ).insert(utsjekk.utbetaling.UtbetalingId(uid2.id))

            iv.data.personident
        }

        val res = httpClient.post("/api/iverksetting/v2/migrate") {
            bearerAuth(TestRuntime.azure.generateToken(fakes.Azp.TILTAKSPENGER))
            contentType(ContentType.Application.Json)
            setBody(MigrationRequest(sid, bid))
        }

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(personident, personident) // unused
        val utbetaling = TestRuntime.kafka.getProducer(Topics.utbetaling)
        assertEquals(0, utbetaling.uncommitted().size)
        val actuals = utbetaling.history().filter { (key, _) -> key == uid1.toString() }.map { it.second }// .second
        assertEquals(1, actuals.size)
        val actual = actuals.first()
        assertEquals(false, actual.dryrun)
        assertEquals(models.Fagsystem.TILTAKSPENGER, actual.fagsystem)
        assertEquals(uid1, actual.uid)
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
        assertEquals(NavEnhet("1234"), actual.perioder[0].betalendeEnhet)
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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(bid),
                    iverksettingId = IverksettingId(iid),
                    vedtakstidspunkt = 1.mar.atStartOfDay(),
                ),
            ).also { it.insert(utsjekk.utbetaling.UtbetalingId(UUID.randomUUID())) }


            TestData.dao.iverksettingResultat(
                fagsystem = Fagsystem.TILTAKSPENGER,
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
            setBody(MigrationRequest(sid, bid))
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val utbetaling = TestRuntime.kafka.getProducer(Topics.utbetaling)
        assertEquals(0, utbetaling.uncommitted().size)
        val new_uid = uid(sid, meldeperiode, models.StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, models.Fagsystem.TILTAKSPENGER)
        val actual = utbetaling.history().single { (key, _) -> key == new_uid.toString() }.second
        assertEquals(false, actual.dryrun)
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
        assertEquals(NavEnhet("1234"), actual.perioder[0].betalendeEnhet)
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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(bid),
                    iverksettingId = IverksettingId(iid),
                    vedtakstidspunkt = 1.mar.atStartOfDay(),
                ),
            ).also { it.insert(utsjekk.utbetaling.UtbetalingId(UUID.randomUUID())) }


            TestData.dao.iverksettingResultat(
                fagsystem = Fagsystem.TILTAKSPENGER,
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
            setBody(MigrationRequest(sid, bid))
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val utbetaling = TestRuntime.kafka.getProducer(Topics.utbetaling)
        assertEquals(0, utbetaling.uncommitted().size)
        val new_uid = uid(sid, meldeperiode, models.StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, models.Fagsystem.TILTAKSPENGER)
        val actual = utbetaling.history().single { (key, _) -> key == new_uid.toString() }.second
        assertEquals(false, actual.dryrun)
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
        assertEquals(NavEnhet("1234"), actual.perioder[0].betalendeEnhet)
        assertEquals(500u, actual.perioder[1].beløp)
        assertEquals(null, actual.perioder[1].vedtakssats)
        assertEquals(15.feb, actual.perioder[1].fom)
        assertEquals(28.feb, actual.perioder[1].tom)
        assertEquals(NavEnhet("1234"), actual.perioder[1].betalendeEnhet)
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
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(bid),
                    iverksettingId = IverksettingId(iid),
                    vedtakstidspunkt = 1.mar.atStartOfDay(),
                ),
            ).also { it.insert(utsjekk.utbetaling.UtbetalingId(UUID.randomUUID())) }


            TestData.dao.iverksettingResultat(
                fagsystem = Fagsystem.TILTAKSPENGER,
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
            setBody(MigrationRequest(sid, bid))
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val utbetaling = TestRuntime.kafka.getProducer(Topics.utbetaling)
        assertEquals(0, utbetaling.uncommitted().size)
        val uid1 = uid(sid, meldeperiode, models.StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, models.Fagsystem.TILTAKSPENGER)
        val actual1 = utbetaling.history().single { (key, _) -> key == uid1.toString() }.second
        assertEquals(false, actual1.dryrun)
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
        assertEquals(NavEnhet("1234"), actual1.perioder[0].betalendeEnhet)

        val uid2 = uid(sid, meldeperiode, models.StønadTypeTiltakspenger.DIGITAL_JOBBKLUBB, models.Fagsystem.TILTAKSPENGER)
        val actual2 = utbetaling.history().single { (key, _) -> key == uid2.toString() }.second
        assertEquals(false, actual2.dryrun)
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
        assertEquals(NavEnhet("1234"), actual2.perioder[0].betalendeEnhet)
    }

    @Test
    fun `migrating same sak twice only sends once`() = runTest(TestRuntime.context) {
        val sid = RandomOSURId.generate()
        val bid = RandomOSURId.generate()
        val uid = uid(sakId = sid, "meldeperiode", models.StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, models.Fagsystem.TILTAKSPENGER)

        transaction {
            TestData.dao.iverksetting(
                mottattTidspunkt = LocalDateTime.now().minusDays(2),
                iverksetting = TestData.domain.iverksetting(
                    fagsystem = Fagsystem.TILTAKSPENGER,
                    sakId = SakId(sid),
                    behandlingId = BehandlingId(bid),
                ),
            ).also { it.insert(utsjekk.utbetaling.UtbetalingId(uid.id)) }

            TestData.dao.iverksettingResultat(
                fagsystem = Fagsystem.TILTAKSPENGER,
                sakId = SakId(sid),
                behandlingId = BehandlingId(bid),
                tilkjentYtelse = TestData.domain.enTilkjentYtelse(
                    listOf(
                        TestData.domain.enAndelTilkjentYtelse(
                            beløp = 500,
                            fom = 4.feb,
                            tom = 4.feb,
                            periodeId = 4L,
                            stønadsdata = StønadsdataTiltakspenger(
                                stønadstype = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                                barnetillegg = false,
                                brukersNavKontor = BrukersNavKontor("1234"),
                                meldekortId = "meldeperiode",
                            )
                        ),
                    )
                ),
                resultat = OppdragResultat(OppdragStatus.KVITTERT_OK),
            ).insert(utsjekk.utbetaling.UtbetalingId(uid.id))
        }

        repeat(2) {
            val res = httpClient.post("/api/iverksetting/v2/migrate") {
                bearerAuth(TestRuntime.azure.generateToken(fakes.Azp.TILTAKSPENGER))
                contentType(ContentType.Application.Json)
                setBody(MigrationRequest(sid, bid))
            }
            assertEquals(HttpStatusCode.OK, res.status)
        }

        val utbetaling = TestRuntime.kafka.getProducer(Topics.utbetaling)
        assertEquals(1, utbetaling.history().count { (key, _) -> key == uid.toString() })
    }

    @Test
    fun `iverksetting on migrated sak is locked`() = runTest(TestRuntime.context) {
        val sid = RandomOSURId.generate()
        transaction {
            TestData.dao.iverksetting(
                iverksetting = TestData.domain.iverksetting(
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    sakId = SakId(sid),
                ),
            ).insert(utsjekk.utbetaling.UtbetalingId(UUID.randomUUID()))
            IverksettingDao.markMigrated(SakId(sid), Fagsystem.TILLEGGSSTØNADER)
        }

        val dto = TestData.dto.iverksetting(
            sakId = sid,
            vedtak = TestData.dto.vedtaksdetaljer(
                utbetalinger = listOf(TestData.dto.utbetaling(stønadsdata = TestData.dto.tilleggstønad())),
            ),
        )
        val res = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }

        assertEquals(HttpStatusCode.Locked, res.status)
    }
}
