package simulering

import java.time.LocalTime
import java.time.LocalDateTime
import kotlinx.coroutines.channels.Channel
import libs.kafka.KafkaProducer
import libs.tracing.Tracing
import libs.utils.Log
import libs.utils.erHelligdag
import models.ApiError
import models.Fagsystem
import models.Info
import models.Simulering
import models.v2
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse

class SimuleringWorker(
    private val channel: Channel<SimuleringRequest>,
    private val backpressureChannel: Channel<SimuleringBackpressure>,
    private val service: SimuleringService,
    private val producers: Map<Fagsystem, KafkaProducer<String, Simulering>>,
) {
    suspend fun run() {
        for ((key, request, headers) in channel) {
            try {
                val fagsystem = Fagsystem.from(request.request.oppdrag.kodeFagomraade.trimEnd())
                val simulering = try {
                    val response = service.simulerJaxb(request)
                    mapSuccess(response, fagsystem)
                } catch (e: Exception) {
                    mapAndLogError(e, key, fagsystem)
                }
                withTraceparent(headers) {
                    producerFor(fagsystem).send(key, simulering, headers)
                }
            } catch (e: Exception) {
                Log.error("Feil i simulering-worker for key=$key", e)
            }
        }
    }

    suspend fun drainBackpressure() {
        for ((key, fagsystem, headers) in backpressureChannel) {
            try {
                Log.warn("Simulering har for lang kø, prøv igjen senere (${fagsystem} key=${key})")
                withTraceparent(headers) {
                    producerFor(fagsystem).send(key, Info.Utilgjengelig(fagsystem, "Simulering har for lang kø, prøv igjen senere"), headers)
                }
            } catch(e: Exception) {
                Log.error("Feil ved sending av backpressure-svar for $fagsystem key=$key", e)
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

    private fun mapSuccess(response: SimulerBeregningResponse, fagsystem: Fagsystem): Simulering =
        when {
            fagsystem.isTilleggsstønader() -> simulering.v1.from(response) ?: Info.OkUtenEndring(fagsystem)
            fagsystem == Fagsystem.TILTAKSPENGER -> simulering.v1.from(response) ?: Info.OkUtenEndring(fagsystem)
            else -> v2.Simulering.from(response)
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

    private fun <T> withTraceparent(
        headers: Map<String, String>,
        block: () -> T,
    ): T {
        val traceparent = headers["traceparent"] ?: return block()
        return Tracing.contextFromTraceparent(traceparent)
            .makeCurrent()
            .use { block() }
    }
}
