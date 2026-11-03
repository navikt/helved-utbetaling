package urskog.oppdrag

import com.ibm.mq.jms.MQQueue
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import models.Fagsystem
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import urskog.*
import javax.jms.TextMessage
import kotlin.test.assertEquals

class OppdragskopierTest {

    @AfterEach
    fun cleanup() {
        TestRuntime.mq.reset()
    }

    @Test
    fun `sender DARE POC-kopi i dev`() {
        produserOppdrag("dev-gcp", Fagsystem.AAP.oppdrag())
        assertAntallProduserteKopier(TestRuntime.config.oppdrag.darePocAapKø, 1)
    }

    @Test
    fun `sender ikke DARE POC-kopi i prod`() {
        produserOppdrag("prod-gcp", Fagsystem.AAP.oppdrag())
        assertAntallProduserteKopier(TestRuntime.config.oppdrag.darePocAapKø, 0)
    }

    @Test
    fun `sender ikke DARE POC-kopi for andre fagsystemer enn AAP`() {
        Fagsystem.entries.filter { it.name != "AAP" }.forEach {
            produserOppdrag("dev-gcp", it.oppdrag())
        }
        assertAntallProduserteKopier(TestRuntime.config.oppdrag.darePocAapKø, 0)
    }

    @Test
    fun `sender oppdragskopi til beregningsplattform i dev`() {
        produserOppdrag("dev-gcp", Fagsystem.AAP.oppdrag())
        produserOppdrag("dev-gcp", Fagsystem.DAGPENGER.oppdrag())
        assertAntallProduserteKopier(TestRuntime.config.oppdrag.beregningsplattformKø, 2)
    }

    @Test
    fun `sender bare oppdragskopi til beregningsplattform i dev`() {
        produserOppdrag("prod-gcp", Fagsystem.AAP.oppdrag())
        produserOppdrag("prod-gcp", Fagsystem.DAGPENGER.oppdrag())
        assertAntallProduserteKopier(TestRuntime.config.oppdrag.beregningsplattformKø, 0)
    }

    private fun Fagsystem.oppdrag(): Oppdrag {
        return TestData.oppdrag(fagområde = fagområde, oppdragslinjer = emptyList())
    }

    private fun produserOppdrag(cluster: String, oppdrag: Oppdrag) {
        val config = TestRuntime.config.copy(cluster = cluster)
        val meters = SimpleMeterRegistry()

        try {
            OppdragMQProducer(config, TestRuntime.mq, meters).send(oppdrag)
        } finally {
            meters.close()
        }
    }

    private fun assertAntallProduserteKopier(kø: MQQueue, antallKopier: Int) {
        val originaler = TestRuntime.mq.context.sent[TestRuntime.config.oppdrag.sendKø]
            .orEmpty().map { (it as TextMessage).text }
        val kopier = TestRuntime.mq.context.sent[kø]
            .orEmpty().map { (it as TextMessage).text }

        assertEquals(antallKopier, kopier.size)
        if (antallKopier > 0) {
            assertEquals(originaler, kopier)
        }
    }
}
