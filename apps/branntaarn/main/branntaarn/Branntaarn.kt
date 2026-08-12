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

    val config = Config()
    val peisschtappern = PeisschtappernClient(config)
    val slack = SlackClient(config)

    val branner = hentBranner(peisschtappern)
    brannalarmer(slack, branner)
    slukk(branner, peisschtappern)
}

fun hentBranner(peisschtappern: PeisschtappernClient) =
    manglendeKvittering(peisschtappern) +
    pendingMismatch(peisschtappern) +
    peisschtappern.dobbeltutbetalinger()

fun brannalarmer(slack: SlackClient, branner: List<Brann>) {
    slack.postPendingMismatches(branner.filterIsInstance<PendingMismatch>())
    slack.postAggregated(branner.filterIsInstance<ManglendeKvittering>().groupBy { it.fagsystem })
    slack.postDobbeltutbetalinger(branner.filterIsInstance<Dobbeltutbetaling>())
}

fun slukk(branner: List<Brann>, peisschtappern: PeisschtappernClient) {
    branner.forEach(peisschtappern::slukk)
}

fun manglendeKvittering(
    peisschtappern: PeisschtappernClient,
    now: LocalDateTime = LocalDateTime.now(),
): List<Brann> {
    if (!now.erVarseltid()) return emptyList()
    return peisschtappern.manglendeKvitteringer().filter { it.timeout.isBefore(now) }
}

fun pendingMismatch(
    peisschtappern: PeisschtappernClient,
    now: LocalDateTime = LocalDateTime.now(),
): List<Brann> {
    if (!now.erVarseltid()) return emptyList()
    return peisschtappern.pendingMismatches()
}

internal fun LocalDateTime.erVarseltid() = !toLocalDate().erHelligdag() && hour in 6..21
