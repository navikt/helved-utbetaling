package vedskiva

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.postgres.Jdbc
import libs.postgres.Migrator
import libs.utils.logger
import libs.utils.secureLog
import models.erHelligdag

val appLog = logger("app")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    val config: Config = Config()
    val kafka: Kafka = Kafka()
    Jdbc.initialize(config.jdbc)
    runBlocking {
        withContext(Jdbc.context) {
            Migrator(config.jdbc.migrations).migrate()

            if (!LocalDate.now().erHelligdag()) {
                OppdragsdataConsumer(config.kafka, kafka).use {
                    it.consumeFromBeginning()
                }
            }
        }
    }
}

