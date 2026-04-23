package speiderhytta.slo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import libs.utils.appLog
import java.io.File

/**
 * One SLO definition (the YAML format is a subset of Sloth's so the same file
 * can be fed into `sloth generate` in CI and read here at runtime).
 *
 *   service:    "utsjekk"
 *   name:       "iverksetting-availability"
 *   objective:  99.9
 *   description: "Iverksetting endpoint succeeds"
 *   sli:
 *     events:
 *       error_query: "..."
 *       total_query: "..."
 *
 * Unknown YAML keys (e.g. Sloth's `alerting`, `labels`) are ignored by the
 * mapper configured below.
 */
data class SloDefinition(
    val service: String,
    val name: String,
    val objective: Double,
    val description: String? = null,
    val sli: Sli,
) {
    data class Sli(val events: Events) {
        data class Events(
            val errorQuery: String,
            val totalQuery: String,
        )
    }
}

/**
 * Sloth's top-level wrapper: { version, service, slos: [...] }.
 * We accept either format — bare list or wrapped — for flexibility.
 */
private data class SlothFile(
    val service: String? = null,
    val slos: List<SloDefinition> = emptyList(),
)

/**
 * Reads SLO YAML files from disk on startup. Path layout:
 *   /var/run/slos/utsjekk.yml
 *   /var/run/slos/abetal.yml
 *
 * Mounted into the container as a ConfigMap built in CI from each app's
 * `apps/<app>/slo.yml` (so the source of truth lives next to the app).
 */
class SloDefinitionLoader(private val dir: File) {

    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .findAndRegisterModules()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun load(): List<SloDefinition> {
        if (!dir.isDirectory) {
            appLog.warn("SLO definitions dir {} does not exist; no SLOs loaded", dir)
            return emptyList()
        }
        return dir.listFiles { f -> f.isFile && (f.extension == "yml" || f.extension == "yaml") }
            ?.flatMap(::parseFile)
            ?: emptyList()
    }

    private fun parseFile(file: File): List<SloDefinition> = try {
        val wrapper = mapper.readValue(file, SlothFile::class.java)
        if (wrapper.slos.isNotEmpty()) {
            wrapper.slos.map { def -> def.maybeFillService(wrapper.service) }
        } else {
            // bare list fallback
            mapper.readerForListOf(SloDefinition::class.java).readValue<List<SloDefinition>>(file)
        }
    } catch (t: Throwable) {
        appLog.warn("failed to parse SLO file {}", file, t)
        emptyList()
    }

    private fun SloDefinition.maybeFillService(parent: String?): SloDefinition =
        if (service.isBlank() && !parent.isNullOrBlank()) copy(service = parent) else this
}
