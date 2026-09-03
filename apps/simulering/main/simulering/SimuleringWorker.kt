package simulering

import kotlinx.coroutines.channels.Channel
import libs.kafka.KafkaProducer
import libs.utils.appLog
import libs.utils.secureLog
import models.ApiError
import models.Fagsystem
import models.Info
import models.Simulering
import models.v2
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse

class SimuleringWorker(
    private val channel: Channel<Pair<String, SimulerBeregningRequest>>,
    private val backpressureChannel: Channel<Pair<String, Fagsystem>>,
    private val service: SimuleringService,
    private val producers: Map<Fagsystem, KafkaProducer<String, Simulering>>,
) {
    suspend fun run() {
        for ((key, request) in channel) {
            try {
                val fagsystem = Fagsystem.from(request.request.oppdrag.kodeFagomraade.trimEnd())
                val simulering = try {
                    val response = service.simulerJaxb(request)
                    mapSuccess(response, fagsystem)
                } catch (e: Exception) {
                    mapAndLogError(e, key, fagsystem)
                }
                producerFor(fagsystem).send(key, simulering)
            } catch (e: Exception) {
                appLog.error("Feil i simulering-worker for key=$key")
                secureLog.error("Feil i simulering-worker for key=$key", e)
            }
        }
    }

    suspend fun drainBackpressure() {
        for ((key, fagsystem) in backpressureChannel) {
            try {
                appLog.warn("Simulering har for lang kø, prøv igjen senere (${fagsystem} key=${key})")
                secureLog.warn("Simulering har for lang kø, prøv igjen senere (${fagsystem} key=${key})")
                producerFor(fagsystem).send(key, Info.Utilgjengelig(fagsystem, "Simulering har for lang kø, prøv igjen senere"))
            } catch(e: Exception) {
                // TODO: vurder å bytte til warning + metrikker for å styre alerts ved forekomst-frekvens
                appLog.error("Feil ved sending av backpressure-svar for $fagsystem key=$key")
                secureLog.error("Feil ved sending av backpressure-svar for $fagsystem key=$key", e)
            }
        }
    }

    private fun mapAndLogError(error: Exception, key: String, fs: Fagsystem): Simulering {
        val msg = if (error is ApiError) error.msg else error.message ?: "ukjent feil"

        fun ugyldig(): Simulering {
            appLog.warn("Ugyldig simulering $fs $key $msg")
            secureLog.warn("Ugyldig simulering $fs $key $msg", error)
            return Info.UgyldigRequest(fs, msg)
        }

        fun utilgjengelig(): Simulering {
            appLog.error("Simulering utilgjengelig $fs $key $msg")
            secureLog.error("Simulering utilgjengelig $fs $key $msg", error)
            return Info.Utilgjengelig(fs, msg)
        }

        fun feilet(): Simulering {
            appLog.error("Simulering feilet $fs $key $msg")
            secureLog.error("Simulering feilet $fs $key $msg", error)
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
}
