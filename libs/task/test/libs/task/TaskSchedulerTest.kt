package libs.task

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.produce
import libs.postgres.concurrency.CoroutineDatasource
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import libs.postgres.map
import libs.utils.appLog
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis


class TaskSchedulerTest {
    private val pg = PostgresContainer()
    private val databaseScope = CoroutineScope(Dispatchers.IO + CoroutineDatasource(pg.datasource))


    @Test
    fun `populated UBEHANDLET tasks is set to KLAR_TIL_PLUKK by scheduler`() {
        runBlocking {
            databaseScope.async {
                appLog.info("initially ${count(Status.UBEHANDLET)} UBEHANDLET tasks")

                produceWhile {
                    count(Status.UBEHANDLET) < 10
                }

                consumeWhile {
                    count(Status.UBEHANDLET) > 1
                }
            }.await()
        }
    }

    private suspend fun consumeWhile(predicate: suspend () -> Boolean) {
        TaskTestScheduler(databaseScope).use {
            val timed = measureTimeMillis {
                while (predicate()) continue
            }

            val ubehCount = count(Status.UBEHANDLET)
            val klarCount = count(Status.KLAR_TIL_PLUKK)

            appLog.info("processed $ubehCount UBEHANDLET -> $klarCount KLAR_TIL_PLUKK in $timed ms")
        }
    }

    private suspend fun produceWhile(predicate: suspend () -> Boolean) {
        val producer = databaseScope.launch {
            for (task in infiniteTasks) {
                transaction {
                    task.insert()
                }
            }
        }

        val timed = measureTimeMillis {
            while (predicate()) continue
        }

        producer.cancelAndJoin()

        appLog.info("saved ${count(Status.UBEHANDLET)} UBEHANDLET tasks in $timed ms")
    }
}

private suspend fun count(status: Status): Int =
    transaction {
        Repo.count(status)
    }

private object Repo {
    suspend fun count(status: Status): Int = coroutineContext.connection
        .prepareStatement("SELECT count(*) FROM task WHERE status = ?").use { stmt ->
            stmt.setString(1, status.name)
            stmt.executeQuery()
                .map { it.getInt(1) }
                .singleOrNull() ?: 0
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
private val CoroutineScope.infiniteTasks
    get() = produce {
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
