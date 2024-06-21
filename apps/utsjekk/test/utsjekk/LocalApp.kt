package utsjekk

import fakes.AzureFake
import fakes.OppdragFake
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import libs.jdbc.PostgresContainer
import libs.postgres.Postgres
import libs.postgres.Postgres.migrate
import libs.postgres.concurrency.CoroutineDatasource
import libs.postgres.concurrency.transaction
import libs.task.Status
import libs.task.TaskDao
import java.time.LocalDateTime
import java.util.UUID
import kotlin.coroutines.CoroutineContext

fun main() {
    val postgres = PostgresContainer("utsjekk")
    val azure = AzureFake()
    val oppdrag = OppdragFake()

    val datasource = Postgres.initialize(postgres.config).apply { migrate() }
    val context: CoroutineContext = Dispatchers.IO + CoroutineDatasource(datasource)

    val config by lazy {
        Config(
            oppdrag = oppdrag.config,
            azure = azure.config,
            postgres = postgres.config,
        )
    }

    embeddedServer(Netty, port = 8080) {
        utsjekk(config, context)
        populate(context)
    }.start(wait = true)
}

fun populate(context: CoroutineContext) {
    CoroutineScope(context).launch {
        transaction {
            TaskDao(
                id = UUID.randomUUID(),
                payload = "",
                status = Status.UNPROCESSED,
                attempt = 0,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                scheduledFor = LocalDateTime.now(),
                message = "",
            ).insert()
        }
    }
}