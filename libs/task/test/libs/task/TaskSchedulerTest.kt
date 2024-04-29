package libs.task

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import libs.postgres.coroutines.CoroutineDatasource
import libs.postgres.coroutines.transaction
import libs.postgres.map
import org.junit.jupiter.api.Test
import utsjekk.task.TaskDao
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals

class TaskSchedulerTest {
    private val pg = PostgresContainer()
    private val scope = CoroutineScope(Dispatchers.IO + CoroutineDatasource(pg.datasource))

    private val dataflow: Flow<TaskDao> = flow {
        repeat(10_000) {
            emit(
                TaskDao(
                    payload = "$it",
                    type = "awesome $it",
                    metadata = "splendid",
                    avvikstype = "avvik",
                )
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> Flow<T>.buffer(size: Int = 0): Flow<T> = flow {
        coroutineScope {
            val channel = produce(capacity = size) {
                println("Produces batch size of $size")
                collect { send(it) }
            }
            channel.consumeEach { emit(it) }
        }
    }

    @Test
    fun verify() {
        assertEquals(10_000, count())
    }

    private fun count(): Int {
        return pg.transaction {
            it.prepareStatement("SELECT count(*) from task")
                .executeQuery()
                .map { rs -> rs.getInt(1) }
                .singleOrNull() ?: 0
        }
    }

    private suspend fun populateDatabase() {
        val time = measureTimeMillis {
            dataflow.buffer(100).collect {
                transaction {
                    it.insert()
                }
            }
        }
        println("Populated db in $time ms")
    }

    @Test
    fun populate() = runBlocking {
        println("Size before: ${count()}")
        scope.launch {
            populateDatabase()
        }
        delay(5_000)
        coroutineContext.cancelChildren()
        println("Size after: ${count()}")
    }
}
