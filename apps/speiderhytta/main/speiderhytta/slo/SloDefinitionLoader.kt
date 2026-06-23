package speiderhytta.slo

import libs.utils.YamlNode
import libs.utils.appLog
import libs.utils.mapping
import libs.utils.parseYaml
import libs.utils.sequence
import libs.utils.string
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
 * Unknown YAML keys (e.g. Sloth's `alerting`, `labels`) are ignored.
 */
data class SloDefinition(
    val service: String = "",
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
 * Reads SLO YAML files from disk on startup. Path layout:
 *   /var/run/slos/utsjekk.yml
 *   /var/run/slos/abetal.yml
 *
 * Mounted into the container as a ConfigMap built in CI from each app's
 * `apps/<app>/slo.yml` (so the source of truth lives next to the app).
 */
class SloDefinitionLoader(private val dir: File) {

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
        val root = parseYaml(file.readText()) as? YamlNode.Mapping ?: return emptyList()
        val service = root.string("service")
        root.sequence("slos").map { node ->
            val m = node as YamlNode.Mapping
            val events = m.mapping("sli")!!.mapping("events")!!
            SloDefinition(
                service = m.string("service")?.takeIf { it.isNotBlank() } ?: service ?: "",
                name = m.string("name")!!,
                objective = m.string("objective")!!.toDouble(),
                description = m.string("description")?.trim(),
                sli = SloDefinition.Sli(
                    SloDefinition.Sli.Events(
                        errorQuery = events.string("error_query")!!,
                        totalQuery = events.string("total_query")!!,
                    )
                ),
            )
        }
    } catch (t: Throwable) {
        appLog.warn("failed to parse SLO file {}", file, t)
        emptyList()
    }
}
