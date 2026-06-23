package libs.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YamlTest {

    @Test
    fun `simple key-value mapping`() {
        val node = parseYaml("name: hello\nversion: 1.0") as YamlNode.Mapping
        assertEquals("hello", node.string("name"))
        assertEquals("1.0", node.string("version"))
    }

    @Test
    fun `nested mappings`() {
        val yaml = """
            server:
              host: localhost
              port: 8080
        """.trimIndent()
        val root = parseYaml(yaml) as YamlNode.Mapping
        val server = root.mapping("server")!!
        assertEquals("localhost", server.string("host"))
        assertEquals("8080", server.string("port"))
    }

    @Test
    fun `deeply nested mappings`() {
        val yaml = """
            a:
              b:
                c: deep
        """.trimIndent()
        val root = parseYaml(yaml) as YamlNode.Mapping
        assertEquals("deep", root.mapping("a")!!.mapping("b")!!.string("c"))
    }

    @Test
    fun `sequence of scalars`() {
        val yaml = """
            items:
              - alpha
              - beta
              - gamma
        """.trimIndent()
        val root = parseYaml(yaml) as YamlNode.Mapping
        val items = root.sequence("items").map { (it as YamlNode.Scalar).value }
        assertEquals(listOf("alpha", "beta", "gamma"), items)
    }

    @Test
    fun `sequence of mappings`() {
        val yaml = """
            people:
              - name: Alice
                age: 30
              - name: Bob
                age: 25
        """.trimIndent()
        val root = parseYaml(yaml) as YamlNode.Mapping
        val people = root.sequence("people")
        assertEquals(2, people.size)
        val alice = people[0] as YamlNode.Mapping
        assertEquals("Alice", alice.string("name"))
        assertEquals("30", alice.string("age"))
        val bob = people[1] as YamlNode.Mapping
        assertEquals("Bob", bob.string("name"))
        assertEquals("25", bob.string("age"))
    }

    @Test
    fun `block scalar preserves internal newlines`() {
        val yaml = """
            query: |
              sum(rate(
                requests_total{app="foo"}
              [5m]))
            next: value
        """.trimIndent()
        val root = parseYaml(yaml) as YamlNode.Mapping
        val query = root.string("query")!!
        assertTrue(query.contains("sum(rate("))
        assertTrue(query.contains("[5m]))"))
        assertTrue(query.contains("\n"))
        assertEquals("value", root.string("next"))
    }

    @Test
    fun `block scalar trims trailing empty lines`() {
        val yaml = "description: |\n  line one\n  line two\n\n\nnext: ok"
        val root = parseYaml(yaml) as YamlNode.Mapping
        val desc = root.string("description")!!
        assertEquals("line one\nline two", desc)
        assertEquals("ok", root.string("next"))
    }

    @Test
    fun `double quoted strings`() {
        val yaml = """name: "hello world"""".trimIndent()
        val root = parseYaml(yaml) as YamlNode.Mapping
        assertEquals("hello world", root.string("name"))
    }

    @Test
    fun `single quoted strings`() {
        val yaml = "name: 'hello world'"
        val root = parseYaml(yaml) as YamlNode.Mapping
        assertEquals("hello world", root.string("name"))
    }

    @Test
    fun `comments are stripped`() {
        val yaml = """
            # top comment
            name: value # inline comment
            other: ok
        """.trimIndent()
        val root = parseYaml(yaml) as YamlNode.Mapping
        assertEquals("value", root.string("name"))
        assertEquals("ok", root.string("other"))
    }

    @Test
    fun `comments inside quotes are preserved`() {
        val yaml = "query: 'rate(foo{x=#bar}[5m])'"
        val root = parseYaml(yaml) as YamlNode.Mapping
        assertEquals("rate(foo{x=#bar}[5m])", root.string("query"))
    }

    @Test
    fun `top-level sequence`() {
        val yaml = """
            - name: first
              value: 1
            - name: second
              value: 2
        """.trimIndent()
        val root = parseYaml(yaml) as YamlNode.Sequence
        assertEquals(2, root.items.size)
        assertEquals("first", (root.items[0] as YamlNode.Mapping).string("name"))
    }

    @Test
    fun `double helper`() {
        val yaml = "objective: 99.9"
        val root = parseYaml(yaml) as YamlNode.Mapping
        assertEquals(99.9, root.double("objective"))
    }

    @Test
    fun `missing keys return null`() {
        val yaml = "name: value"
        val root = parseYaml(yaml) as YamlNode.Mapping
        assertNull(root.string("missing"))
        assertNull(root.mapping("missing"))
        assertEquals(emptyList(), root.sequence("missing"))
    }

    @Test
    fun `empty input`() {
        val root = parseYaml("") as YamlNode.Mapping
        assertTrue(root.entries.isEmpty())
    }

    @Test
    fun `values with colons are not split`() {
        val yaml = "url: http://localhost:8080/path"
        val root = parseYaml(yaml) as YamlNode.Mapping
        assertEquals("http://localhost:8080/path", root.string("url"))
    }

    @Test
    fun `full slo yml integration test`() {
        val yaml = """
            version: prometheus/v1
            service: utsjekk
            labels:
              team: helved
              app: utsjekk
            slos:
              - name: iverksetting-availability
                objective: 99.9
                description: |
                  Iverksetting endpoint should respond successfully (non-5xx) for at
                  least 99.9% of requests over a 30-day window.
                sli:
                  events:
                    # Adjust label names to whatever the ktor micrometer binder emits.
                    error_query: |
                      sum(rate(ktor_http_server_requests_seconds_count{
                        app="utsjekk",
                        k8s_cluster_name="prod",
                        route=~"/api/iverksetting.*",
                        status=~"5.."
                      }[5m]))
                    total_query: |
                      sum(rate(ktor_http_server_requests_seconds_count{
                        app="utsjekk",
                        k8s_cluster_name="prod",
                        route=~"/api/iverksetting.*"
                      }[5m]))
                alerting:
                  name: UtsjekkIverksettingErrorBudgetBurn
                  page_alert:
                    labels:
                      severity: critical
                  ticket_alert:
                    labels:
                      severity: warning
        """.trimIndent()

        val root = parseYaml(yaml) as YamlNode.Mapping
        assertEquals("prometheus/v1", root.string("version"))
        assertEquals("utsjekk", root.string("service"))

        val labels = root.mapping("labels")!!
        assertEquals("helved", labels.string("team"))

        val slos = root.sequence("slos")
        assertEquals(1, slos.size)

        val slo = slos[0] as YamlNode.Mapping
        assertEquals("iverksetting-availability", slo.string("name"))
        assertEquals(99.9, slo.double("objective"))
        assertTrue(slo.string("description")!!.contains("non-5xx"))

        val events = slo.mapping("sli")!!.mapping("events")!!
        assertTrue(events.string("error_query")!!.contains("status=~\"5..\""))
        assertTrue(events.string("total_query")!!.contains("ktor_http_server_requests_seconds_count"))

        // Unknown keys parsed but not needed
        val alerting = slo.mapping("alerting")!!
        assertEquals("UtsjekkIverksettingErrorBudgetBurn", alerting.string("name"))
    }
}
