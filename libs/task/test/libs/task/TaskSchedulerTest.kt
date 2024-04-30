package libs.task

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import libs.postgres.coroutines.CoroutineDatasource
import libs.postgres.coroutines.connection
import libs.postgres.coroutines.transaction
import libs.postgres.map
import org.junit.jupiter.api.Test
import kotlin.coroutines.coroutineContext

private object Repo {
    suspend fun count(): Int = coroutineContext.connection
        .prepareStatement("select count(*) from task")
        .executeQuery()
        .map { it.getInt(1) }
        .singleOrNull() ?: 0
}

class TaskSchedulerTest {
    private val pg = PostgresContainer()
    private val scope = CoroutineScope(Dispatchers.IO + CoroutineDatasource(pg.datasource))

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.produceTasks(gen: IdGen) =
        produce(capacity = 100) {
            while (true) {
                val id = gen.next()
                val task = TaskDao(
                    payload = "$id",
                    type = "awesome $id",
                    metadata = "splendid",
                    avvikstype = "avvik",
                )
                send(task)
            }
        }

    private suspend fun saveTasks(tasks: ReceiveChannel<TaskDao>) {
        for (task in tasks) {
            transaction {
                task.insert()
            }
        }
    }

    private suspend fun count(): Int {
        return transaction {
            Repo.count()
        }
    }

    class IdGen(private var id: Int) {
        fun next(): Int {
            return id++
        }
    }

//    @Test
    fun populate() {
        runBlocking {

            scope.async {
                println("Size before: ${count()}")

                val jobs = (0..99).map {
                    scope.launch {
                        println("launching: ${currentCoroutineContext()}")
                        val generator = IdGen(it * 11000)
                        val producer = produceTasks(generator)
                        saveTasks(producer)
                    }
                }
                var prev = count()
                while (count() < 10_000) {
                    delay(1000)
                    val new = count()
                    val diff = new - prev
                    prev = new
                    println("saved $diff tasks/s")
                }

                runCatching {
                    jobs.forEach { it.cancelAndJoin() }
                }

                println("Size after: ${count()}")
            }.await()
        }
    }
}
