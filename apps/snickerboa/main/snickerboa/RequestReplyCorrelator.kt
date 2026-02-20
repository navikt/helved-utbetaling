package snickerboa

import io.ktor.http.HttpStatusCode
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import models.ApiError
import models.DocumentedErrors
import models.Simulering
import models.Status
import models.StatusReply
import kotlin.collections.set
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class UtbetalingResponse(val statusCode: HttpStatusCode, val body: Any)

class RequestReplyCorrelator(
    val producers: UtbetalingProducers,
    private val timeout: Duration = 30.seconds,
) {
    // TODO: Det kan hende vi ikke får responsen fordi den kan havne på en annen tråd en requesten
    private val statusRequests = ConcurrentHashMap<UUID, CompletableDeferred<StatusReply>>()
    private val simuleringsRequests = ConcurrentHashMap<UUID, CompletableDeferred<Simulering>>()

    suspend fun handleUtbetaling(
        dryrun: Boolean,
        txId: UUID,
        produce: (UUID) -> Unit,
    ): UtbetalingResponse {
        return if (dryrun) handleSimulering(txId, produce)
        else handleStatus(txId, produce)
    }

    private suspend fun handleStatus(txId: UUID, produce: (UUID) -> Unit): UtbetalingResponse {
        val deferred = CompletableDeferred<StatusReply>().also {
            statusRequests[txId] = it
            it.invokeOnCompletion { statusRequests.remove(txId) }
        }

        val reply = try {
            produce(txId)
            withTimeout(timeout) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            StatusReply.err(ApiError(408, "Fikk ingen endelig status innen ${timeout.inWholeSeconds} sekunder"))
        } catch (e: Exception) {
            StatusReply.err(ApiError(500, "Intern feil under venting på svar: ${e.message}"))
        } finally {
            statusRequests.remove(txId)
        }

        val statusCode = when (reply.status) {
            Status.OK, Status.MOTTATT, Status.HOS_OPPDRAG -> HttpStatusCode.OK
            Status.FEILET -> HttpStatusCode.fromValue(reply.error?.statusCode ?: 500)
        }

        return UtbetalingResponse(statusCode, reply)
    }

    private suspend fun handleSimulering(txId: UUID, produce: (UUID) -> Unit): UtbetalingResponse {
        val deferred = CompletableDeferred<Simulering>().also {
            simuleringsRequests[txId] = it
            it.invokeOnCompletion { simuleringsRequests.remove(txId) }
        }

        try {
            produce(txId)
            val simulering = withTimeout(timeout) { deferred.await() }
            return UtbetalingResponse(HttpStatusCode.OK, simulering)
        } catch (_: TimeoutCancellationException) {
            throw ApiError(408, "Fikk ingen respons på simulering innen ${timeout.inWholeSeconds} sekunder", DocumentedErrors.BASE)
        } finally {
            simuleringsRequests.remove(txId)
        }
    }

    fun completeStatus(id: UUID, reply: StatusReply) {
        statusRequests[id]?.complete(reply)
    }

    fun completeSimulering(id: UUID, reply: Simulering) {
        simuleringsRequests[id]?.complete(reply)
    }
}