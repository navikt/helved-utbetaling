package libs.task

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import libs.postgres.coroutines.CoroutineDatasource
import libs.postgres.coroutines.connection
import libs.postgres.coroutines.transaction
import libs.postgres.map
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis

private object Repo {
    suspend fun count(status: Status): Int = coroutineContext.connection
        .prepareStatement("SELECT count(*) FROM task WHERE status = ?").use { stmt ->
            stmt.setString(1, status.name)
            stmt.executeQuery()
                .map { it.getInt(1) }
                .singleOrNull() ?: 0
        }
}

class TaskSchedulerTest {
    private val pg = PostgresContainer()
//    private val scope = CoroutineScope(Dispatchers.IO + CoroutineDatasource(pg.datasource))
    private val scope = CoroutineScope(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.produceTasks() =
        produce(capacity = 100) {
            while (true) {
                val id = UUID.randomUUID()
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
                runCatching {
                    task.insert()
                }
            }
        }
    }

    private suspend fun count(status: Status): Int = transaction { Repo.count(status) }

    //    @Test
    fun `schedule task UBEHANDLET`() = runBlocking {
        scope.async {
            val job = TaskTestScheduler(scope)

            val time = measureTimeMillis {
                while (count(Status.UBEHANDLET) != 0) {
                    delay(1000)
                    val ubehandlet = count(Status.UBEHANDLET)
                    val klartTilPlukk = count(Status.KLAR_TIL_PLUKK)
                    println("$klartTilPlukk of ${ubehandlet + klartTilPlukk} KLAR_TIL_PLUKK")
                }
            }

            println("Completed in ${time / 1000}s")

            job.close()
        }.await()
    }

    //    @Test
    fun `populate 10K UBEHANDLET tasks`() = runBlocking {
        scope.async {
            println("Size before: ${count(Status.UBEHANDLET)}")

            val coroutines = (1..20).map {
                scope.launch {
                    saveTasks(produceTasks())
                }
            }

            while (count(Status.UBEHANDLET) < 10_000) {
                println("saved ${count(Status.UBEHANDLET)} tasks")
                delay(1000)
            }

            coroutines.forEach {
                runCatching {
                    it.cancelAndJoin()
                }
            }

            println("Size after: ${count(Status.UBEHANDLET)}")
        }.await()
    }
}
