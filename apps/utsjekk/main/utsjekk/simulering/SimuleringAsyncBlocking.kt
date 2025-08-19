package utsjekk.simulering

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import models.Simulering
import models.UtbetalingId
import models.locked

class SimuleringAsyncBlocking {

    private val map = ConcurrentHashMap<UtbetalingId, CompletableDeferred<Simulering>>()

    fun register(utbetalingId: UtbetalingId): CompletableDeferred<Simulering> {
        if (map.containsKey(utbetalingId)) locked("Simulering for $utbetalingId pågår allerede")
        val completableDeferred = CompletableDeferred<Simulering>()
        map[utbetalingId] = completableDeferred
        return completableDeferred
    }

    fun remove(utbetalingId: UtbetalingId) {
        map[utbetalingId]?.cancel()
        map.remove(utbetalingId)
    }

    fun complete(utbetalingId: UtbetalingId, simulering: Simulering) {
        map[utbetalingId]?.complete(simulering)
    }


}