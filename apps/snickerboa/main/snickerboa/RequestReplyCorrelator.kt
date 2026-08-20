package snickerboa

import org.http4k.core.Status
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import models.ApiError
import models.DocumentedErrors
import models.Simulering
import models.StatusReply
import kotlin.collections.set
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface UtbetalingResponse {
    val statusCode: Status
    data class StatusResult(override val statusCode: Status, val body: StatusReply) : UtbetalingResponse
    data class Simulering(override val statusCode: Status, val body: models.Simulering) : UtbetalingResponse
}

class RequestReplyCorrelator(
    val producers: UtbetalingProducers,
    private val timeout: Duration = 30.seconds,
) {
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
            models.Status.OK, models.Status.MOTTATT, models.Status.HOS_OPPDRAG -> Status.OK
            models.Status.FEILET -> Status(reply.error?.statusCode ?: 500, "")
        }

        return UtbetalingResponse.StatusResult(statusCode, reply)
    }

    // TODO: Skal vi lese simulerings topicet direkte?
    private suspend fun handleSimulering(txId: UUID, produce: (UUID) -> Unit): UtbetalingResponse {
        val deferred = CompletableDeferred<Simulering>().also {
            simuleringsRequests[txId] = it
            it.invokeOnCompletion { simuleringsRequests.remove(txId) }
        }

        try {
            produce(txId)
            val simulering = withTimeout(timeout) { deferred.await() }
            return UtbetalingResponse.Simulering(Status.OK, simulering)
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