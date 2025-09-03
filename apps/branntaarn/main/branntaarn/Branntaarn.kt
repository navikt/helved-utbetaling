package branntaarn

import libs.utils.appLog
import libs.utils.secureLog
import models.*
import java.time.LocalDateTime

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    branntaarn()
}

fun branntaarn(
    config: Config = Config(),
    now: LocalDateTime = LocalDateTime.now(),
) {
    if (now.toLocalDate().erHelligdag() || now.hour < 6 || now.hour > 21) return

    val peisschtappern = PeisschtappernClient(config)
    val slack = SlackClient(config)
    peisschtappern.branner()
        .filter { brann -> brann.timeout.isBefore(now) }
        .onEach(peisschtappern::slukk)
        .forEach(slack::post)
}
