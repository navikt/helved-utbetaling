package smokesignal

import kotlinx.coroutines.runBlocking
import libs.utils.appLog
import libs.utils.secureLog
import models.erHelligdag
import java.time.LocalDate

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    runBlocking {
        smokesignal()
    }
}

suspend fun smokesignal(
    config: Config = Config(),
    client: VedskivaClient = VedskivaClient(config),
) {
    if (LocalDate.now().erHelligdag()) return
    val next = client.next()
    client.signal(next)
}

