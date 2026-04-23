package speiderhytta.slo

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SloDefinitionLoaderTest {

    private val tmp: File = Files.createTempDirectory("slo-loader-test").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    @Test
    fun `parses Sloth-style YAML with snake_case error_query and total_query`() {
        File(tmp, "utsjekk.yml").writeText(
            """
            version: prometheus/v1
            service: utsjekk
            labels:
              team: helved
            slos:
              - name: iverksetting-availability
                objective: 99.9
                description: |
                  Iverksetting endpoint should respond successfully.
                sli:
                  events:
                    error_query: |
                      sum(rate(ktor_http_server_requests_seconds_count{app="utsjekk",k8s_cluster_name="prod",status=~"5.."}[5m]))
                    total_query: |
                      sum(rate(ktor_http_server_requests_seconds_count{app="utsjekk",k8s_cluster_name="prod"}[5m]))
                alerting:
                  name: UtsjekkBurn
                  page_alert:
                    labels:
                      severity: critical
            """.trimIndent()
        )

        val defs = SloDefinitionLoader(tmp).load()

        assertEquals(1, defs.size)
        val def = defs.single()
        assertEquals("utsjekk", def.service)
        assertEquals("iverksetting-availability", def.name)
        assertEquals(99.9, def.objective)
        assertTrue(def.sli.events.errorQuery.contains("status=~\"5..\""))
        assertTrue(def.sli.events.totalQuery.contains("ktor_http_server_requests_seconds_count"))
    }

    @Test
    fun `returns empty when directory does not exist`() {
        val missing = File(tmp, "nope")
        assertEquals(emptyList(), SloDefinitionLoader(missing).load())
    }

    @Test
    fun `skips files that fail to parse and returns successfully parsed ones`() {
        File(tmp, "broken.yml").writeText("this: is: not: valid: yaml: [")
        File(tmp, "ok.yml").writeText(
            """
            service: abetal
            slos:
              - name: dummy
                objective: 99.0
                sli:
                  events:
                    error_query: "sum(rate(foo[5m]))"
                    total_query: "sum(rate(bar[5m]))"
            """.trimIndent()
        )

        val defs = SloDefinitionLoader(tmp).load()
        assertEquals(1, defs.size)
        assertEquals("abetal", defs.single().service)
    }
}
