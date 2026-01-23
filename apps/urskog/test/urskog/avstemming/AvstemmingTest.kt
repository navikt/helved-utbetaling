package urskog.avstemming

import models.*
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import urskog.*
import java.util.*
import kotlin.test.assertEquals

class AvstemmingTest {

    @AfterEach 
    fun cleanup() {
        TestRuntime.mq.reset()
        TestRuntime.topics.avstemming.assertThat().isEmpty()
    }

    @Test
    fun `can send avstemming`() {
        val t1 = UUID.randomUUID().toString()
        val t2 = UUID.randomUUID().toString()
        val t3 = UUID.randomUUID().toString()

        val (start, data, slutt) = avstemming()

        TestRuntime.topics.avstemming.produce(t1) { start }
        TestRuntime.topics.avstemming.produce(t2) { data }
        TestRuntime.topics.avstemming.produce(t3) { slutt }

        assertEquals(3, TestRuntime.mq.sentAvstemming().size)

        XmlAssert.assertEquals(start, TestRuntime.mq.sentAvstemming()[0])
        XmlAssert.assertEquals(data, TestRuntime.mq.sentAvstemming()[1])
        XmlAssert.assertEquals(slutt, TestRuntime.mq.sentAvstemming()[2])
    }
}

