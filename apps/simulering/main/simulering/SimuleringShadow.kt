package simulering

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import libs.utils.Log
import libs.utils.secureLog
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import models.Fagsystem
import models.Info
import models.Simulering
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class SimuleringShadow(private val client: SokosClient, private val config: SokosOppdragConfig) : AutoCloseable {
    // Never queue unbounded work or run a shadow request on a production worker.
    private val executor = ThreadPoolExecutor(
        2, 2, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16),
        { task -> Thread(task, "simulering-rest-shadow").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    fun submit(key: String, request: SimulerBeregningRequest, fagsystem: Fagsystem, soapMapped: Simulering) {
        if (config.cluster == "prod-gcp") return
        val comparison = ShadowComparison(key, fagsystem)
        val parent = Context.current()
        try {
            executor.execute {
                SimuleringTracing.span("simulering.rest.shadow", parent) {
                    compare(request, fagsystem, soapMapped, comparison)
                }
            }
        } catch (_: RejectedExecutionException) {
            comparison.log("skipped") { put("reason", "Shadow queue full or shutting down") }
        }
    }

    private fun compare(request: SimulerBeregningRequest, fagsystem: Fagsystem, soapMapped: Simulering, comparison: ShadowComparison) {
        try {
            comparison.log("soap_mapped") { put("soapMapped", libs.kotlinx.KotlinxJson.encodeToJsonElement(soapMapped)) }
            val response = client.simulerDto(request.toRestRequest(), comparison)
            val restMapped = response.toSimulering(fagsystem)
            comparison.log("comparison") {
                put("restBusinessStatus", response.statusKode)
                put("restMapped", libs.kotlinx.KotlinxJson.encodeToJsonElement(restMapped))
                put("soapMapped", libs.kotlinx.KotlinxJson.encodeToJsonElement(soapMapped))
                put("equal", soapMapped == restMapped)
            }
            if (soapMapped != restMapped && !isMissingTestPersonMismatch(config.cluster, soapMapped, response)) {
                Log.error("Simulation mismatch between SOAP and REST results. Check secureLog for details.")
            }
        } catch (e: Exception) {
            Span.current().setStatus(StatusCode.ERROR)
            // Exception messages may contain downstream payloads: never use the application logger.
            comparison.log("failed") { put("error", e.toString()) }
        }
    }

    override fun close() {
        executor.shutdownNow()
    }
}

internal fun isMissingTestPersonMismatch(cluster: String, soapMapped: Simulering, response: ResponsSimulering): Boolean {
    if (cluster != "dev-gcp") return false
    if (soapMapped !is Info || soapMapped.status != Info.Status.UGYLDIG_REQUEST || soapMapped.message != "Personen finnes ikke i PDL") return false
    if (response.statusKode != 4 || response.statusMelding != "Navn er ikke funnet ved oppslag") return false
    val calculation = response.beregning ?: return true
    return calculation.gjelderId.isBlank() && calculation.datoBeregnet.isBlank()
        && calculation.belopTotalt.signum() == 0 && calculation.periodeStoppnivaa.isEmpty()
}

class ShadowComparison(private val key: String, private val fagsystem: Fagsystem) {
    private val comparisonId = UUID.randomUUID().toString()

    fun log(event: String, fields: JsonObjectBuilder.() -> Unit) {
        SimuleringTracing.withLogContext {
            secureLog.info(buildJsonObject {
                put("event", "simulering_shadow_$event")
                put("comparisonId", comparisonId)
                put("key", key)
                put("fagsystem", fagsystem.name)
                put("modelVersion", if (fagsystem.isTilleggsstønader() || fagsystem == Fagsystem.TILTAKSPENGER) "v1" else "v2")
                fields()
            }.toString())
        }
    }
}
