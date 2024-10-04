package oppdrag.iverksetting

import kotlinx.coroutines.test.runTest
import libs.mq.MQ
import oppdrag.TestRuntime
import oppdrag.etUtbetalingsoppdrag
import oppdrag.iverksetting.domene.OppdragMapper
import org.junit.jupiter.api.*
import org.postgresql.util.PSQLException
import org.postgresql.util.ServerErrorMessage
import java.io.PrintWriter
import java.sql.Connection
import javax.sql.DataSource
import kotlin.test.assertEquals

class OppdragServiceTest {

    @BeforeEach
    @AfterEach
    fun cleanup() = TestRuntime.clear()

    @Test
    @Disabled
    fun `sender ikke oppdrag om lagring av oppdrag i db feiler`() = runTest(TestRuntime.context) {
        val mq = MQ(TestRuntime.config.mq)
        val service = OppdragService(
            config = TestRuntime.config.oppdrag,
            mq = mq,
//            postgres = DataSourceFake()
        )

        val utbetalingsoppdrag = etUtbetalingsoppdrag()
        val oppdrag110 = OppdragMapper.tilOppdrag110(utbetalingsoppdrag)
        val oppdrag = OppdragMapper.tilOppdrag(oppdrag110)

        assertThrows<UkjentOppdragLagerException> {
            service.opprettOppdrag(utbetalingsoppdrag, oppdrag, 0)
        }

        assertEquals(0, TestRuntime.oppdrag.sendKø.getReceived().size)
    }

    private class DataSourceFake : DataSource {
        override fun getLogWriter() = TODO("Not yet implemented")
        override fun setLogWriter(out: PrintWriter?) = TODO("Not yet implemented")
        override fun setLoginTimeout(seconds: Int) = TODO("Not yet implemented")
        override fun getLoginTimeout() = TODO("Not yet implemented")
        override fun getParentLogger() = TODO("Not yet implemented")
        override fun <T : Any?> unwrap(iface: Class<T>?) = TODO("Not yet implemented")
        override fun isWrapperFor(iface: Class<*>?) = TODO("Not yet implemented")
        override fun getConnection(username: String?, password: String?) = TODO("Not yet implemented")

        override fun getConnection(): Connection {
            throw PSQLException(ServerErrorMessage("Ooops"))
        }
    }
}