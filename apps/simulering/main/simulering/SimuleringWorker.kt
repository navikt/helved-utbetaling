package simulering

import java.time.LocalTime
import java.time.LocalDateTime
import kotlinx.coroutines.channels.Channel
import libs.kafka.KafkaProducer
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import libs.utils.Log
import libs.utils.erHelligdag
import models.ApiError
import models.Fagsystem
import models.Info
import models.Simulering

class SimuleringWorker(
    private val channel: Channel<SimuleringRequestWrapper>,
    private val backpressureChannel: Channel<SimuleringBackpressure>,
    private val service: SimuleringService,
    private val producers: Map<Fagsystem, KafkaProducer<String, Simulering>>,
    private val shadow: SimuleringShadow? = null,
) {
    suspend fun run() {
        for ((key, request, headers) in channel) {
            SimuleringTracing.kafka(headers) {
                try {
                    val fagsystem = Fagsystem.from(request.request.oppdrag.kodeFagomraade.trimEnd())
                    val simulering = try {
                        val response = service.simulerJaxb(request)
                        response.toSimulering(fagsystem)
                    } catch (e: Exception) {
                        Span.current().setStatus(StatusCode.ERROR)
                        mapAndLogError(e, key, fagsystem)
                    }
                    producerFor(fagsystem).send(key, simulering, headers)
                    shadow?.submit(key, request, fagsystem, simulering)
                } catch (e: Exception) {
                    Span.current().setStatus(StatusCode.ERROR)
                    Log.error("Feil i simulering-worker for key=$key", e)
                }
            }
        }
    }

    suspend fun drainBackpressure() {
        for ((key, fagsystem, headers) in backpressureChannel) {
            SimuleringTracing.kafka(headers) {
                try {
                    Log.warn("Simulering har for lang kø, prøv igjen senere (${fagsystem} key=${key})")
                    producerFor(fagsystem).send(key, Info.Utilgjengelig(fagsystem, "Simulering har for lang kø, prøv igjen senere"), headers)
                } catch(e: Exception) {
                    Span.current().setStatus(StatusCode.ERROR)
                    Log.error("Feil ved sending av backpressure-svar for $fagsystem key=$key", e)
                }
            }
        }
    }

    private fun mapAndLogError(error: Exception, key: String, fs: Fagsystem): Simulering {
        val msg = if (error is ApiError) error.msg else error.message ?: "ukjent feil"

        fun ugyldig(): Simulering {
            Log.warn("Ugyldig simulering ($fs $key)", error)
            return Info.UgyldigRequest(fs, msg)
        }

        fun utilgjengelig(): Simulering {
            val now = LocalDateTime.now()
            val utenforÅpningstid = now.toLocalDate().erHelligdag() || now.toLocalTime() !in LocalTime.of(6, 0)..<LocalTime.of(21, 0)
            val simuleringStengtErr = error is ApiError && error.statusCode == 502 && msg.contains("simulering stengt")
            when (utenforÅpningstid && simuleringStengtErr) {
                true -> Log.warn("Simulering utilgjengelig ($fs $key)", error)
                false -> Log.error("Simulering utilgjengelig ($fs $key)", error)
            }
            return Info.Utilgjengelig(fs, msg)
        }

        fun feilet(): Simulering {
            Log.error("Simulering feilet ($fs $key)", error)
            return Info.Feilet(fs, msg)
        }

        return when {
            isInvalidRequest(error, msg) -> ugyldig()
            isUnavailable(error, msg) -> utilgjengelig()
            else -> feilet()
        }
    }

    private fun isInvalidRequest(error: Exception, msg: String) =
        (error is ApiError && error.statusCode in 400..499)
            || msg.contains("finnes ikke")
            || msg.contains("ugyldig")
            || msg.contains("finnes fra før")
            || msg.contains("DFHPI1008")
            || msg.contains("Referert vedtak")

    private fun isUnavailable(error: Exception, msg: String) =
        (error is ApiError && error.statusCode in 500..599)
            || msg.contains("stengt")
            || msg.contains("utilgjengelig")
            || msg.contains("SOAP Body")
            || msg.contains("502")

    private fun producerFor(fagsystem: Fagsystem): KafkaProducer<String, Simulering> {
        val key = if (fagsystem.isTilleggsstønader()) Fagsystem.TILLEGGSSTØNADER else fagsystem
        return producers[key] ?: error("Ingen producer for fagsystem $fagsystem")
    }

}
