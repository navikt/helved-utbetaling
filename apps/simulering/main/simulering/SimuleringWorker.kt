package simulering

import kotlinx.coroutines.channels.Channel
import libs.kafka.KafkaProducer
import libs.utils.appLog
import libs.utils.secureLog
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
                    val message = e.message ?: "ukjent feil"
                    if (isInvalidRequest(message)) {
                        appLog.warn("Simulering feilet for fagsystem=$fagsystem")
                        secureLog.warn("Simulering feilet for key=$key fagsystem=$fagsystem", e)
                    } else {
                        appLog.error("Simulering feilet for fagsystem=$fagsystem")
                        secureLog.error("Simulering feilet for key=$key fagsystem=$fagsystem", e)
                    }
                    mapError(e, fagsystem)
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
                producerFor(fagsystem).send(key, Info.Utilgjengelig(fagsystem, "simulering har for lang kø, prøv igjen senere"))
            } catch(e: Exception) {
                // TODO: vurder å bytte til warning + metrikker for å styre alerts ved forekomst-frekvens
                appLog.error("Feil ved sending av backpressure-svar for key=$key")
                secureLog.error("Feil ved sending av backpressure-svar for key=$key", e)
            }
        }
    }

    private fun mapSuccess(response: SimulerBeregningResponse, fagsystem: Fagsystem): Simulering =
        when {
            fagsystem.isTilleggsstønader() -> simulering.v1.from(response) ?: Info.OkUtenEndring(fagsystem)
            fagsystem == Fagsystem.TILTAKSPENGER -> simulering.v1.from(response) ?: Info.OkUtenEndring(fagsystem)
            else -> v2.Simulering.from(response)
        }

    private fun mapError(error: Exception, fagsystem: Fagsystem): Simulering {
        val message = error.message ?: "ukjent feil"
        return when {
            isUnavailable(message) -> Info.Utilgjengelig(fagsystem, message)
            isInvalidRequest(message) -> Info.UgyldigRequest(fagsystem, message)
            else -> Info.Feilet(fagsystem, message)
        }
    }

    private fun isUnavailable(msg: String) = msg.contains("stengt")
        || msg.contains("utilgjengelig")
        || msg.contains("SOAP Body")
        || msg.contains("502")

    private fun isInvalidRequest(msg: String) = msg.contains("finnes ikke")
        || msg.contains("ugyldig")
        || msg.contains("finnes fra før")
        || msg.contains("DFHPI1008")
        || msg.contains("Referert vedtak")

    private fun producerFor(fagsystem: Fagsystem): KafkaProducer<String, Simulering> {
        val key = if (fagsystem.isTilleggsstønader()) Fagsystem.TILLEGGSSTØNADER else fagsystem
        return producers[key] ?: error("Ingen producer for fagsystem $fagsystem")
    }
}
