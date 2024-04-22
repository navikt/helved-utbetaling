package oppdrag.iverksetting.mq

import libs.mq.MQ
import libs.xml.XMLMapper
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.Resource
import oppdrag.TestEnvironment
import oppdrag.etUtbetalingsoppdrag
import oppdrag.iverksetting.domene.Kvitteringstatus
import oppdrag.iverksetting.domene.OppdragMapper
import oppdrag.iverksetting.domene.kvitteringstatus
import oppdrag.iverksetting.tilstand.OppdragLager
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import oppdrag.iverksetting.tilstand.id
import oppdrag.somOppdragLager
import org.junit.jupiter.api.*
import kotlin.test.assertEquals

class OppdragMQConsumerTest {
    private val mapper = XMLMapper<Oppdrag>()

    @BeforeEach
    fun clear() {
        TestEnvironment.clearTables()
    }

    @Test
    fun `skal tolke kvittering riktig ved ok`() {
        val xml = Resource.read("/kvittering-akseptert.xml")
        val oppdrag = mapper.readValue(xml)
        assertEquals(Kvitteringstatus.OK, oppdrag.kvitteringstatus)
    }

    @Test
    fun `skal deserialisere kvittering som feilet i testmiljø`() {
        val xml = Resource.read("/kvittering-test.xml")
        val kvittering = TestEnvironment.oppdrag.createMessage(xml)
        val oppdragXml = consumer.leggTilNamespacePrefiks(kvittering.text)

        assertDoesNotThrow {
            mapper.readValue(oppdragXml).id
        }
    }

    @Test
    fun `skal tolke kvittering riktig ved feil`() {
        val xml = Resource.read("/kvittering-avvist.xml")
        val oppdrag = mapper.readValue(xml)
        assertEquals(Kvitteringstatus.AVVIST_FUNKSJONELLE_FEIL, oppdrag.kvitteringstatus)
    }

    @Test
    fun `skal lagre status og mmel fra kvittering`() {
        val oppdragLager = etUtbetalingsoppdrag().somOppdragLager

        TestEnvironment.postgres.transaction { con ->
            OppdragLagerRepository.opprettOppdrag(oppdragLager, con)
        }

        val xml = Resource.read("/kvittering-test.xml")
        val kvittering = TestEnvironment.oppdrag.createMessage(xml)

        consumer.onMessage(kvittering)

        // todo: better assertions
        assertEquals(1, TestEnvironment.tableSize("oppdrag_lager"))
    }

    @Test
    fun `skal lagre kvittering på riktig versjon`() {
        val oppdragLager = etUtbetalingsoppdrag().somOppdragLager.apply {
            status = OppdragStatus.KVITTERT_OK
        }

        val oppdragLagerV1 = etUtbetalingsoppdrag().let { utbet ->
            val tilOppdrag110 = OppdragMapper.tilOppdrag110(utbet)
            val oppdrag = OppdragMapper.tilOppdrag(tilOppdrag110)
            OppdragLager.lagFraOppdrag(utbet, oppdrag, 0)
        }

        TestEnvironment.postgres.transaction { con ->
            OppdragLagerRepository.opprettOppdrag(oppdragLager, con)
            OppdragLagerRepository.opprettOppdrag(oppdragLagerV1, con)
        }

        val xml = Resource.read("/kvittering-akseptert.xml")
        val kvittering = TestEnvironment.oppdrag.createMessage(xml)
        consumer.onMessage(kvittering)

        // todo: better assertions
        assertEquals(2, TestEnvironment.tableSize("oppdrag_lager"))
    }

    @Test
    fun `oppretter ikke oppdrag hvis henting av oppdrag feiler`() {
        val xml = Resource.read("/kvittering-akseptert.xml")
        val kvittering = TestEnvironment.oppdrag.createMessage(xml)

//        assertThrows<Exception> {
        consumer.onMessage(kvittering)
//        }

        // todo: better assertions
        assertEquals(0, TestEnvironment.tableSize("oppdrag_lager"))
    }

    @Test
    fun `skal logge warn hvis oppdrag i databasen har uventet status`() {
        val oppdragLager = etUtbetalingsoppdrag().somOppdragLager.apply {
            status = OppdragStatus.KVITTERT_OK
        }

        TestEnvironment.postgres.transaction { con ->
            OppdragLagerRepository.opprettOppdrag(oppdragLager, con)
        }

        val xml = Resource.read("/kvittering-akseptert.xml")
        val kvittering = TestEnvironment.oppdrag.createMessage(xml)
        consumer.onMessage(kvittering)

        // todo: better assertions
        assertEquals(1, TestEnvironment.tableSize("oppdrag_lager"))
    }

    internal companion object {
        val consumer = TestEnvironment.postgres.withDatasource { datasource ->
            OppdragMQConsumer(TestEnvironment.config, MQ(TestEnvironment.mq.config), datasource)
        }

        @BeforeAll
        @JvmStatic
        fun setup() = consumer.start()

        @AfterAll
        @JvmStatic
        fun teardown() = consumer.close()
    }
}
