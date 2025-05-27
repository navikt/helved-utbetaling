package libs.kafka.processor

import libs.kafka.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.Duration

internal class StateScheduleProcessorTest {

    private class FilterScheduler(ktable: KTable<String, String>, interval: Duration): StateScheduleProcessor<String, String>(
        named = "filter-${ktable.table.stateStoreName}-scheduler",
        table = ktable,
        interval = interval,
    ) {
        companion object {
            val result = mutableListOf<String>()
        }
        override fun schedule(wallClockTime: Long, store: StateStore<String, String>) {
            val notLols = store.filter(2) {
                it.value != "lol"
            }.map {
                it.second
            }
            result.addAll(notLols)
        }
    }

    @Test
    fun filter() {
        FilterScheduler.result.clear()
        val kafka = Mock.withTopology {
            val ktable = consume(Tables.B)
            val scheduler = FilterScheduler(ktable, 2.toDuration(DurationUnit.MILLISECONDS))
            ktable.schedule(scheduler)
        }

        kafka.inputTopic(Topics.B).produce("1", "hello")
        kafka.inputTopic(Topics.B).produce("1", "hello2")
        kafka.inputTopic(Topics.B).produce("2", "there")
        kafka.inputTopic(Topics.B).produce("3", "lol")
        kafka.inputTopic(Topics.B).produce("4", "hehe")

        kafka.advanceWallClockTime(2.toDuration(DurationUnit.MILLISECONDS))
        assertTrue(FilterScheduler.result.containsAll(listOf("hello2", "there")))
    }


    private class IteratorScheduler(ktable: KTable<String, String>, interval: Duration): StateScheduleProcessor<String, String>(
        named = "filter-${ktable.table.stateStoreName}-scheduler",
        table = ktable,
        interval = interval,
    ) {
        companion object  {
            var iteratorCount: Int = 0
        }
        override fun schedule(wallClockTime: Long, store: StateStore<String, String>) {
            val iter = store.iterator()
            while(iter.hasNext()) {
                iter.next()
                iteratorCount++
            }
        }
    }
    @Test
    fun iterator() {
        IteratorScheduler.iteratorCount = 0
        val kafka = Mock.withTopology {
            val ktable = consume(Tables.B)
            val scheduler = IteratorScheduler(ktable, 2.toDuration(DurationUnit.MILLISECONDS))
            ktable.schedule(scheduler)
        }

        kafka.inputTopic(Topics.B).produce("1", "hello")
        kafka.inputTopic(Topics.B).produce("1", "hello2")
        kafka.inputTopic(Topics.B).produce("2", "there")
        kafka.inputTopic(Topics.B).produce("3", "lol")
        kafka.inputTopic(Topics.B).produce("4", "hehe")

        kafka.advanceWallClockTime(2.toDuration(DurationUnit.MILLISECONDS))
        assertEquals(4, IteratorScheduler.iteratorCount)
    }
}

