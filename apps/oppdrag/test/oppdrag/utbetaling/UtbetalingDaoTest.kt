package oppdrag.utbetaling

import java.io.PrintWriter
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import libs.mq.MQ
import libs.postgres.concurrency.*
import libs.utils.Resource
import libs.xml.XMLMapper
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.oppdrag.*
import no.trygdeetaten.skjema.oppdrag.Mmel
import oppdrag.TestRuntime
import oppdrag.etUtbetalingsoppdrag
import oppdrag.iverksetting.domene.OppdragMapper
import oppdrag.iverksetting.tilstand.OppdragId
import org.junit.jupiter.api.*
import org.postgresql.util.PSQLException
import org.postgresql.util.ServerErrorMessage
import no.trygdeetaten.skjema.oppdrag.Oppdrag

class UtbetalingDaoTest {

    @Test
    fun `can insert`() = runTest(TestRuntime.context) {
        val uo = etUtbetalingsoppdrag()
        val oppdrag110 = OppdragMapper.tilOppdrag110(uo)
        val oppdrag = OppdragMapper.tilOppdrag(oppdrag110)
        transaction {
            UtbetalingDao(
                uid = UtbetalingId(UUID.randomUUID()),
                data = oppdrag,
                sakId = "123",
                behandlingId = "1",
                personident = "12345678910",
                klassekode = "LOL",
                kvittering = null,
                fagsystem = "DAGPENGER",
                status = OppdragStatus.LAGT_PÅ_KØ,
            ).insert()
        }
    }

    @Test
    fun `can update`() = runTest(TestRuntime.context) {
        val uo = etUtbetalingsoppdrag()
        val oppdrag110 = OppdragMapper.tilOppdrag110(uo)
        val oppdrag = OppdragMapper.tilOppdrag(oppdrag110)
        transaction {
            val dao = UtbetalingDao(
                uid = UtbetalingId(UUID.randomUUID()),
                data = oppdrag,
                sakId = "123",
                behandlingId = "1",
                personident = "12345678910",
                klassekode = "LOL",
                kvittering = null,
                fagsystem = "DAGPENGER",
                status = OppdragStatus.LAGT_PÅ_KØ,
            )
            dao.insert()
            dao.copy(
                status = OppdragStatus.KVITTERT_OK,
                kvittering = avvistKvitteringsmelding()
            ).update()
        }
    }

    @Test
    fun `can find by utbetalingId`() = runTest(TestRuntime.context) {
        val uo = etUtbetalingsoppdrag()
        val oppdrag110 = OppdragMapper.tilOppdrag110(uo)
        val oppdrag = OppdragMapper.tilOppdrag(oppdrag110)
        val uid = UtbetalingId(UUID.randomUUID()) 
        transaction {
            UtbetalingDao(
                uid = uid,
                data = oppdrag,
                sakId = "123",
                behandlingId = "1",
                personident = "12345678910",
                klassekode = "LOL",
                kvittering = null,
                fagsystem = "DAGPENGER",
                status = OppdragStatus.LAGT_PÅ_KØ,
            ).insert()

            assertNotNull(UtbetalingDao.findOrNull(uid))
        }
    }

    @Test
    fun `can find by oppdragId`() = runTest(TestRuntime.context) {
        val uo = etUtbetalingsoppdrag()
        val oppdrag110 = OppdragMapper.tilOppdrag110(uo)
        val oppdrag = OppdragMapper.tilOppdrag(oppdrag110)
        val uid = UtbetalingId(UUID.randomUUID()) 
        transaction {
            UtbetalingDao(
                uid = uid,
                data = oppdrag,
                sakId = "123",
                behandlingId = "1",
                personident = "12345678910",
                klassekode = "LOL",
                kvittering = null,
                fagsystem = "DAGPENGER",
                status = OppdragStatus.LAGT_PÅ_KØ,
            ).insert()

            val oid =OppdragId(Fagsystem.DAGPENGER, "123", "1", null)
            assertNotNull(UtbetalingDao.findOrNull(oid))
        }
    }
}

private val mapper = XMLMapper<Oppdrag>()

private fun avvistKvitteringsmelding(): Mmel {
    return mapper.readValue(Resource.read("/xml/kvittering-funksjonell-feil.xml")).mmel
}
