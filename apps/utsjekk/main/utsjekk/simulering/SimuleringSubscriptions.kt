package utsjekk.simulering

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import models.Simulering
import models.StatusReply
import models.locked
import java.util.concurrent.ConcurrentHashMap

object SimuleringSubscriptions {

    private val subscriptions = ConcurrentHashMap<String, CompletableDeferred<Simulering>>()
    private val subscriptionsV1 = ConcurrentHashMap<String, CompletableDeferred<models.v1.Simulering>>()
    private val statuses = ConcurrentHashMap<String, CompletableDeferred<StatusReply>>()
    val subscriptionEvents = Channel<String>(Channel.UNLIMITED)

    fun subscribeV1(key: String): Pair<CompletableDeferred<models.v1.Simulering>, CompletableDeferred<StatusReply>> {
        libs.utils.appLog.info("subscribe to $key")
        if (subscriptionsV1.containsKey(key)) locked("Simulering for $key pågår allerede")

        val simuleringDeferred = CompletableDeferred<models.v1.Simulering>()
        subscriptionsV1[key] = simuleringDeferred 

        val statusDeferred = CompletableDeferred<StatusReply>()
        statuses[key] = statusDeferred

        subscriptionEvents.trySend(key)
        return simuleringDeferred to statusDeferred
    }

    fun subscribe(key: String): Pair<CompletableDeferred<Simulering>, CompletableDeferred<StatusReply>> {
        libs.utils.appLog.info("subscribe to $key")
        if (subscriptions.containsKey(key)) locked("Simulering for $key pågår allerede")

        val simuleringDeferred = CompletableDeferred<Simulering>()
        subscriptions[key] = simuleringDeferred 

        val statusDeferred = CompletableDeferred<StatusReply>()
        statuses[key] = statusDeferred

        subscriptionEvents.trySend(key)
        return simuleringDeferred to statusDeferred
    }

    fun unsubscribe(key: String) {
        libs.utils.appLog.info("unsubscribe to $key")
        subscriptions[key]?.cancel()
        subscriptions.remove(key)

        subscriptionsV1[key]?.cancel()
        subscriptionsV1.remove(key)

        statuses[key]?.cancel()
        statuses.remove(key)
    }
}

