package peisschtappern

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import kotlinx.coroutines.test.runTest
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import libs.kafka.*

class KafkaTest {

    data class TestCase(
        val table: Tables,
        val topic: Topic<String, ByteArray>,
        val testTopic: TestTopic<String, ByteArray>,
    )

    private val testCases = listOf(
        TestCase(Tables.avstemming, Topics.avstemming, TestTopics.avstemming),
        TestCase(Tables.oppdrag , Topics.oppdrag, TestTopics.oppdrag),
        TestCase(Tables.oppdragsdata , Topics.oppdragsdata, TestTopics.oppdragsdata),
        TestCase(Tables.dryrun_aap , Topics.dryrunAap, TestTopics.dryrunAap),
        TestCase(Tables.dryrun_tp , Topics.dryrunTp, TestTopics.dryrunTp),
        TestCase(Tables.dryrun_ts , Topics.dryrunTs, TestTopics.dryrunTs),
        TestCase(Tables.dryrun_dp , Topics.dryrunDp, TestTopics.dryrunDp),
        TestCase(Tables.aap , Topics.aap, TestTopics.aap),
        TestCase(Tables.saker , Topics.saker, TestTopics.saker),
        TestCase(Tables.utbetalinger , Topics.utbetalinger, TestTopics.utbetalinger),
        TestCase(Tables.kvittering , Topics.kvittering, TestTopics.kvittering),
        TestCase(Tables.simuleringer , Topics.simuleringer, TestTopics.simuleringer),
    )

    @Test
    fun `all tables are tested`() {
        assertTrue(Tables.values().all { table -> 
            testCases.any { case -> case.table == table }
        })
    }


    @Test
    fun `consume and save daos`() = runTest(TestRuntime.context) {
        testCases.forEach { case -> consumeSaveAndAssert(case) }
    }

    fun consumeSaveAndAssert(case: TestCase) {
        case.testTopic.produce("123") {
            "content for ${case.table.name}".toByteArray()
        }
        val dao = awaitDatabase(100) {
            Dao.find("123", case.table).singleOrNull()
        }

        assertNotNull(dao)
        assertEquals("v1", dao.version)
        assertEquals(case.topic.name, dao.topic_name)
        assertEquals("123", dao.key)
        assertEquals("content for ${case.table.name}", dao.value)
        assertEquals(0, dao.partition)
        assertEquals(0, dao.offset)
        assertNotNull(dao.timestamp_ms)
        assertNotNull(dao.stream_time_ms)
        assertNotNull(dao.system_time_ms)
    }


}
