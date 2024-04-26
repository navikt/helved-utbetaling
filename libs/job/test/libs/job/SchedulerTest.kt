package libs.job

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.test.assertEquals

class SchedulerTest {

    @Test
    fun `is spinning`() {
        val datasource = Datasource(mutableMapOf(1 to false, 2 to false))
        val scheduler = TaskScheduler(datasource)

        producer(datasource)

        runBlocking {
            withTimeout(1_000) {
                while (datasource.selectUncommitted().isNotEmpty()) {
                    delay(1)
                }
            }
        }

        assertEquals(10, datasource.countCommitted())
        scheduler.close()
    }
}

class Datasource(private val data: MutableMap<Int, Boolean>) {
    fun add(num: Int) = data.set(num, false)
    fun commit(num: Int) = data.set(num, true)
    fun selectUncommitted() = data.filterValues { !it }.keys.toList()
    fun countCommitted() = data.count { it.value }
}

class TaskScheduler(private val datasource: Datasource) : Scheduler<Int>(120, 1) {
    override fun feed(): List<Int> = datasource.selectUncommitted()
    override fun onError(err: Throwable) = fail("Task failed", err)

    override fun task(feeded: Int) {
        println("task for $feeded")
        datasource.commit(feeded)
    }
}

private fun producer(datasource: Datasource) =
    runBlocking {
        launch {
            for (data in 3..10) {
                datasource.add(data)
                delay(1)
            }
        }
    }