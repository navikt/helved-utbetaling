package models

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.parser.OpenAPIV3Parser
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Contract tests that verify the OpenAPI spec (openapi-dryrun.yml) matches
 * the actual Kotlin models used by the dryrun endpoints.
 *
 * The Kotlin models are the source of truth. If a test fails, the OpenAPI spec
 * needs updating (not the models).
 */
class DryrunContractTest {

    private val jackson = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val spec by lazy {
        val specPath = java.lang.System.getProperty("user.dir") + "/../dokumentasjon/openapi-dryrun.yml"
        val result = OpenAPIV3Parser().readLocation(specPath, null, null)
        assertNotNull(result.openAPI, "Failed to parse OpenAPI spec: ${result.messages}")
        result.openAPI
    }

    private val schemas get() = spec.components.schemas

    private fun schema(name: String): Schema<*> =
        schemas[name] ?: fail("Schema '$name' not found in OpenAPI spec")

    private fun schemaEnumValues(name: String): List<String> {
        val s = schema(name)
        return s.enum?.map { it.toString() } ?: fail("Schema '$name' has no enum values")
    }

    private fun schemaProperties(name: String): Map<String, Schema<*>> =
        schema(name).properties ?: fail("Schema '$name' has no properties")

    private fun schemaRequired(name: String): List<String> =
        schema(name).required ?: emptyList()

    // ──────────────────────────────────────────────
    //  Enum completeness
    // ──────────────────────────────────────────────

    @Test
    fun `Fagsystem enum matches OpenAPI spec`() {
        assertEnumMatches<Fagsystem>("Fagsystem")
    }

    @Test
    fun `Fagområde enum matches OpenAPI spec`() {
        assertEnumMatches<v1.Fagområde>("Fagområde")
    }

    @Test
    fun `PosteringType enum matches OpenAPI spec`() {
        assertEnumMatches<v1.PosteringType>("PosteringType")
    }

    @Test
    fun `SimuleringV2Type enum matches OpenAPI spec`() {
        val specValues = schemaEnumValues("SimuleringV2Type").toSet()
        val kotlinValues = v2.Type.entries.map { it.name }.toSet()
        assertEquals(kotlinValues, specValues, "SimuleringV2Type enum mismatch")
    }

    @Test
    fun `Periodetype enum matches OpenAPI spec`() {
        assertEnumMatches<Periodetype>("Periodetype")
    }

    @Test
    fun `Utbetalingstype enum matches OpenAPI spec`() {
        assertEnumMatches<Utbetalingstype>("Utbetalingstype")
    }

    @Test
    fun `StønadTypeAAP enum matches OpenAPI spec`() {
        assertEnumMatches<StønadTypeAAP>("StønadTypeAAP")
    }

    @Test
    fun `StønadTypeDagpenger enum matches OpenAPI spec`() {
        assertEnumMatches<StønadTypeDagpenger>("StønadTypeDagpenger")
    }

    @Test
    fun `StønadTypeTilleggsstønader enum matches OpenAPI spec`() {
        assertEnumMatches<StønadTypeTilleggsstønader>("StønadTypeTilleggsstønader")
    }

    @Test
    fun `StønadTypeTiltakspenger enum matches OpenAPI spec`() {
        assertEnumMatches<StønadTypeTiltakspenger>("StønadTypeTiltakspenger")
    }

    @Test
    fun `InfoStatus enum matches OpenAPI spec`() {
        val specValues = schemaEnumValues("InfoStatus").toSet()
        val kotlinValues = Info.Status.entries.map { it.name }.toSet()
        assertEquals(kotlinValues, specValues, "InfoStatus enum mismatch")
    }

    @Test
    fun `Årsak enum matches OpenAPI spec`() {
        assertEnumMatches<Årsak>("Årsak")
    }

    // ──────────────────────────────────────────────
    //  Request schema field validation
    // ──────────────────────────────────────────────

    @Test
    fun `AapUtbetaling schema fields match Kotlin model`() {
        val specProps = schemaProperties("AapUtbetaling")
        val kotlinFields = setOf("dryrun", "sakId", "behandlingId", "ident", "utbetalinger", "vedtakstidspunktet", "saksbehandler", "beslutter", "avvent")
        assertFieldsMatch(kotlinFields, specProps.keys, "AapUtbetaling")
    }

    @Test
    fun `AapUtbetaling required fields match non-nullable non-default Kotlin fields`() {
        val specRequired = schemaRequired("AapUtbetaling").toSet()
        // Kotlin: dryrun has default, saksbehandler/beslutter/avvent are nullable
        val expectedRequired = setOf("sakId", "behandlingId", "ident", "utbetalinger", "vedtakstidspunktet")
        assertEquals(expectedRequired, specRequired, "AapUtbetaling required fields mismatch")
    }

    @Test
    fun `AapUtbetalingsdag schema fields match Kotlin model`() {
        val specProps = schemaProperties("AapUtbetalingsdag")
        val kotlinFields = setOf("id", "fom", "tom", "sats", "utbetaltBeløp")
        assertFieldsMatch(kotlinFields, specProps.keys, "AapUtbetalingsdag")
    }

    @Test
    fun `Avvent schema fields match Kotlin model`() {
        val specProps = schemaProperties("Avvent")
        val kotlinFields = setOf("fom", "tom", "overføres", "årsak", "feilregistrering")
        assertFieldsMatch(kotlinFields, specProps.keys, "Avvent")
    }

    @Test
    fun `DpUtbetaling schema fields match Kotlin model`() {
        val specProps = schemaProperties("DpUtbetaling")
        val kotlinFields = setOf("dryrun", "sakId", "behandlingId", "ident", "utbetalinger", "vedtakstidspunktet", "saksbehandler", "beslutter")
        assertFieldsMatch(kotlinFields, specProps.keys, "DpUtbetaling")
    }

    @Test
    fun `DpUtbetaling required fields match non-nullable non-default Kotlin fields`() {
        val specRequired = schemaRequired("DpUtbetaling").toSet()
        val expectedRequired = setOf("sakId", "behandlingId", "ident", "utbetalinger", "vedtakstidspunktet")
        assertEquals(expectedRequired, specRequired, "DpUtbetaling required fields mismatch")
    }

    @Test
    fun `DpUtbetalingsdag schema fields match Kotlin model`() {
        val specProps = schemaProperties("DpUtbetalingsdag")
        val kotlinFields = setOf("meldeperiode", "dato", "sats", "utbetaltBeløp", "utbetalingstype")
        assertFieldsMatch(kotlinFields, specProps.keys, "DpUtbetalingsdag")
    }

    @Test
    fun `TsDto schema fields match Kotlin model`() {
        val specProps = schemaProperties("TsDto")
        val kotlinFields = setOf("dryrun", "sakId", "behandlingId", "personident", "vedtakstidspunkt", "periodetype", "saksbehandler", "beslutter", "utbetalinger")
        assertFieldsMatch(kotlinFields, specProps.keys, "TsDto")
    }

    @Test
    fun `TsDto required fields match non-nullable non-default Kotlin fields`() {
        val specRequired = schemaRequired("TsDto").toSet()
        val expectedRequired = setOf("sakId", "behandlingId", "personident", "vedtakstidspunkt", "periodetype", "utbetalinger")
        assertEquals(expectedRequired, specRequired, "TsDto required fields mismatch")
    }

    @Test
    fun `TsUtbetaling schema fields match Kotlin model`() {
        val specProps = schemaProperties("TsUtbetaling")
        val kotlinFields = setOf("id", "stønad", "perioder", "brukFagområdeTillst")
        assertFieldsMatch(kotlinFields, specProps.keys, "TsUtbetaling")
    }

    @Test
    fun `TsPeriode schema fields match Kotlin model`() {
        val specProps = schemaProperties("TsPeriode")
        val kotlinFields = setOf("fom", "tom", "beløp", "betalendeEnhet")
        assertFieldsMatch(kotlinFields, specProps.keys, "TsPeriode")
    }

    @Test
    fun `TpUtbetaling schema fields match Kotlin model`() {
        val specProps = schemaProperties("TpUtbetaling")
        val kotlinFields = setOf("sakId", "behandlingId", "dryrun", "personident", "vedtakstidspunkt", "perioder", "saksbehandler", "beslutter")
        assertFieldsMatch(kotlinFields, specProps.keys, "TpUtbetaling")
    }

    @Test
    fun `TpUtbetaling required fields match non-nullable non-default Kotlin fields`() {
        val specRequired = schemaRequired("TpUtbetaling").toSet()
        val expectedRequired = setOf("sakId", "behandlingId", "personident", "vedtakstidspunkt", "perioder")
        assertEquals(expectedRequired, specRequired, "TpUtbetaling required fields mismatch")
    }

    @Test
    fun `TpPeriode schema fields match Kotlin model`() {
        val specProps = schemaProperties("TpPeriode")
        val kotlinFields = setOf("meldeperiode", "fom", "tom", "betalendeEnhet", "barnetillegg", "beløp", "stønad")
        assertFieldsMatch(kotlinFields, specProps.keys, "TpPeriode")
    }

    // ──────────────────────────────────────────────
    //  Response schema field validation
    // ──────────────────────────────────────────────

    @Test
    fun `SimuleringV1 schema fields match Kotlin model`() {
        val specProps = schemaProperties("SimuleringV1")
        // @type is the discriminator field added by Jackson, not in Kotlin data class
        val kotlinFields = setOf("@type", "oppsummeringer", "detaljer")
        assertFieldsMatch(kotlinFields, specProps.keys, "SimuleringV1")
    }

    @Test
    fun `OppsummeringForPeriode schema fields match Kotlin model`() {
        val specProps = schemaProperties("OppsummeringForPeriode")
        val kotlinFields = setOf("fom", "tom", "tidligereUtbetalt", "nyUtbetaling", "totalEtterbetaling", "totalFeilutbetaling")
        assertFieldsMatch(kotlinFields, specProps.keys, "OppsummeringForPeriode")
    }

    @Test
    fun `SimuleringDetaljer schema fields match Kotlin model`() {
        val specProps = schemaProperties("SimuleringDetaljer")
        val kotlinFields = setOf("gjelderId", "datoBeregnet", "totalBeløp", "perioder")
        assertFieldsMatch(kotlinFields, specProps.keys, "SimuleringDetaljer")
    }

    @Test
    fun `SimuleringV1Periode schema fields match Kotlin model`() {
        val specProps = schemaProperties("SimuleringV1Periode")
        val kotlinFields = setOf("fom", "tom", "posteringer")
        assertFieldsMatch(kotlinFields, specProps.keys, "SimuleringV1Periode")
    }

    @Test
    fun `SimuleringV1Postering schema fields match Kotlin model`() {
        val specProps = schemaProperties("SimuleringV1Postering")
        val kotlinFields = setOf("fagområde", "sakId", "fom", "tom", "beløp", "type", "klassekode")
        assertFieldsMatch(kotlinFields, specProps.keys, "SimuleringV1Postering")
    }

    @Test
    fun `SimuleringV2 schema fields match Kotlin model`() {
        val specProps = schemaProperties("SimuleringV2")
        val kotlinFields = setOf("@type", "perioder")
        assertFieldsMatch(kotlinFields, specProps.keys, "SimuleringV2")
    }

    @Test
    fun `Simuleringsperiode schema fields match Kotlin model`() {
        val specProps = schemaProperties("Simuleringsperiode")
        val kotlinFields = setOf("fom", "tom", "utbetalinger")
        assertFieldsMatch(kotlinFields, specProps.keys, "Simuleringsperiode")
    }

    @Test
    fun `SimulertUtbetaling schema fields match Kotlin model`() {
        val specProps = schemaProperties("SimulertUtbetaling")
        val kotlinFields = setOf("fagsystem", "sakId", "utbetalesTil", "stønadstype", "tidligereUtbetalt", "nyttBeløp", "posteringer")
        assertFieldsMatch(kotlinFields, specProps.keys, "SimulertUtbetaling")
    }

    @Test
    fun `SimuleringV2Postering schema fields match Kotlin model`() {
        val specProps = schemaProperties("SimuleringV2Postering")
        val kotlinFields = setOf("fom", "tom", "beløp", "type", "klassekode")
        assertFieldsMatch(kotlinFields, specProps.keys, "SimuleringV2Postering")
    }

    @Test
    fun `Info schema fields match Kotlin model`() {
        val specProps = schemaProperties("Info")
        val kotlinFields = setOf("@type", "status", "fagsystem", "message")
        assertFieldsMatch(kotlinFields, specProps.keys, "Info")
    }

    // ──────────────────────────────────────────────
    //  Discriminator validation
    // ──────────────────────────────────────────────

    @Test
    fun `Simulering discriminator values match JsonSubTypes annotations`() {
        // From @JsonSubTypes on Simulering sealed interface:
        // v1 -> models.v1.Simulering
        // v2 -> models.v2.Simulering
        // info -> Info
        val expectedDiscriminatorValues = setOf("v1", "v2", "info")

        // Verify SimuleringV1 schema has @type = "v1"
        val v1TypeEnum = schemaProperties("SimuleringV1")["@type"]?.enum?.map { it.toString() }?.toSet()
        assertEquals(setOf("v1"), v1TypeEnum, "SimuleringV1 @type discriminator mismatch")

        // Verify SimuleringV2 schema has @type = "v2"
        val v2TypeEnum = schemaProperties("SimuleringV2")["@type"]?.enum?.map { it.toString() }?.toSet()
        assertEquals(setOf("v2"), v2TypeEnum, "SimuleringV2 @type discriminator mismatch")

        // Verify Info schema has @type = "info"
        val infoTypeEnum = schemaProperties("Info")["@type"]?.enum?.map { it.toString() }?.toSet()
        assertEquals(setOf("info"), infoTypeEnum, "Info @type discriminator mismatch")

        // Verify all discriminator values are covered
        val allDiscriminators = (v1TypeEnum.orEmpty() + v2TypeEnum.orEmpty() + infoTypeEnum.orEmpty())
        assertEquals(expectedDiscriminatorValues, allDiscriminators, "Not all Simulering discriminator values documented in OpenAPI")
    }

    // ──────────────────────────────────────────────
    //  Serialization round-trip spot-checks
    // ──────────────────────────────────────────────

    @Test
    fun `AapUtbetaling serializes to JSON with field names matching OpenAPI schema`() {
        val model = AapUtbetaling(
            sakId = "SAK-1",
            behandlingId = "BEH-1",
            ident = "12345678901",
            utbetalinger = listOf(
                AapUtbetalingsdag(
                    id = UUID.randomUUID(),
                    fom = LocalDate.of(2025, 1, 6),
                    tom = LocalDate.of(2025, 1, 6),
                    sats = 1000u,
                    utbetaltBeløp = 800u,
                )
            ),
            vedtakstidspunktet = LocalDateTime.of(2025, 1, 1, 12, 0),
        )
        val json = jackson.readTree(jackson.writeValueAsString(model))

        assertJsonFieldsMatchSchema(json, "AapUtbetaling")
    }

    @Test
    fun `DpUtbetaling serializes to JSON with field names matching OpenAPI schema`() {
        val model = DpUtbetaling(
            sakId = "SAK-1",
            behandlingId = "BEH-1",
            ident = "12345678901",
            utbetalinger = listOf(
                DpUtbetalingsdag(
                    meldeperiode = "2025-01",
                    dato = LocalDate.of(2025, 1, 6),
                    sats = 1000u,
                    utbetaltBeløp = 800u,
                    utbetalingstype = Utbetalingstype.Dagpenger,
                )
            ),
            vedtakstidspunktet = LocalDateTime.of(2025, 1, 1, 12, 0),
        )
        val json = jackson.readTree(jackson.writeValueAsString(model))

        assertJsonFieldsMatchSchema(json, "DpUtbetaling")
    }

    @Test
    fun `TsDto serializes to JSON with field names matching OpenAPI schema`() {
        val model = TsDto(
            sakId = "SAK-1",
            behandlingId = "BEH-1",
            personident = "12345678901",
            vedtakstidspunkt = LocalDateTime.of(2025, 1, 1, 12, 0),
            periodetype = Periodetype.MND,
            utbetalinger = listOf(
                TsUtbetaling(
                    id = UUID.randomUUID(),
                    stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
                    perioder = listOf(
                        TsPeriode(
                            fom = LocalDate.of(2025, 1, 1),
                            tom = LocalDate.of(2025, 1, 31),
                            beløp = 5000u,
                        )
                    ),
                )
            ),
        )
        val json = jackson.readTree(jackson.writeValueAsString(model))

        assertJsonFieldsMatchSchema(json, "TsDto")
    }

    @Test
    fun `TpUtbetaling serializes to JSON with field names matching OpenAPI schema`() {
        val model = TpUtbetaling(
            sakId = "SAK-1",
            behandlingId = "BEH-1",
            personident = "12345678901",
            vedtakstidspunkt = LocalDateTime.of(2025, 1, 1, 12, 0),
            perioder = listOf(
                TpPeriode(
                    meldeperiode = "2025-01",
                    fom = LocalDate.of(2025, 1, 6),
                    tom = LocalDate.of(2025, 1, 17),
                    beløp = 500u,
                    stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
                )
            ),
        )
        val json = jackson.readTree(jackson.writeValueAsString(model))

        assertJsonFieldsMatchSchema(json, "TpUtbetaling")
    }

    @Test
    fun `v1 Simulering serializes with correct discriminator`() {
        val model: Simulering = v1.Simulering(
            oppsummeringer = listOf(
                v1.OppsummeringForPeriode(
                    fom = LocalDate.of(2025, 1, 1),
                    tom = LocalDate.of(2025, 1, 31),
                    tidligereUtbetalt = 0,
                    nyUtbetaling = 1000,
                    totalEtterbetaling = 1000,
                    totalFeilutbetaling = 0,
                )
            ),
            detaljer = v1.SimuleringDetaljer(
                gjelderId = "12345678901",
                datoBeregnet = LocalDate.of(2025, 1, 15),
                totalBeløp = 1000,
                perioder = emptyList(),
            ),
        )
        val json = jackson.readTree(jackson.writeValueAsString(model))

        assertEquals("v1", json["@type"].asText(), "v1.Simulering should serialize with @type=v1")
        assertTrue(json.has("oppsummeringer"), "Missing 'oppsummeringer' field")
        assertTrue(json.has("detaljer"), "Missing 'detaljer' field")
    }

    @Test
    fun `v2 Simulering serializes with correct discriminator`() {
        val model: Simulering = v2.Simulering(perioder = emptyList())
        val json = jackson.readTree(jackson.writeValueAsString(model))

        assertEquals("v2", json["@type"].asText(), "v2.Simulering should serialize with @type=v2")
        assertTrue(json.has("perioder"), "Missing 'perioder' field")
    }

    @Test
    fun `Info serializes with correct discriminator`() {
        val model: Simulering = Info.OkUtenEndring(Fagsystem.AAP)
        val json = jackson.readTree(jackson.writeValueAsString(model))

        assertEquals("info", json["@type"].asText(), "Info should serialize with @type=info")
        assertTrue(json.has("status"), "Missing 'status' field")
        assertTrue(json.has("fagsystem"), "Missing 'fagsystem' field")
        assertTrue(json.has("message"), "Missing 'message' field")
        assertEquals("OK_UTEN_ENDRING", json["status"].asText())
    }

    @Test
    fun `v1 Simulering deserializes from JSON with discriminator`() {
        val json = """
            {
              "@type": "v1",
              "oppsummeringer": [],
              "detaljer": {
                "gjelderId": "12345678901",
                "datoBeregnet": "2025-01-15",
                "totalBeløp": 1000,
                "perioder": []
              }
            }
        """.trimIndent()
        val result = jackson.readValue(json, Simulering::class.java)
        assertTrue(result is v1.Simulering, "Expected v1.Simulering, got ${result::class}")
    }

    @Test
    fun `v2 Simulering deserializes from JSON with discriminator`() {
        val json = """{"@type": "v2", "perioder": []}"""
        val result = jackson.readValue(json, Simulering::class.java)
        assertTrue(result is v2.Simulering, "Expected v2.Simulering, got ${result::class}")
    }

    @Test
    fun `Info deserializes from JSON with discriminator`() {
        val json = """
            {
              "@type": "info",
              "status": "OK_UTEN_ENDRING",
              "fagsystem": "AAP",
              "message": "test"
            }
        """.trimIndent()
        val result = jackson.readValue(json, Simulering::class.java)
        assertTrue(result is Info, "Expected Info, got ${result::class}")
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private inline fun <reified E : Enum<E>> assertEnumMatches(schemaName: String) {
        val specValues = schemaEnumValues(schemaName).toSet()
        val kotlinValues = enumValues<E>().map { it.name }.toSet()
        assertEquals(kotlinValues, specValues, "$schemaName enum mismatch")
    }

    private fun assertFieldsMatch(kotlinFields: Set<String>, specFields: Set<String>, schemaName: String) {
        val missingInSpec = kotlinFields - specFields
        val extraInSpec = specFields - kotlinFields
        if (missingInSpec.isNotEmpty() || extraInSpec.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("$schemaName field mismatch:")
                    if (missingInSpec.isNotEmpty()) appendLine("  Kotlin fields missing from spec: $missingInSpec")
                    if (extraInSpec.isNotEmpty()) appendLine("  Spec fields missing from Kotlin: $extraInSpec")
                }
            )
        }
    }

    private fun assertJsonFieldsMatchSchema(json: JsonNode, schemaName: String) {
        val specProps = schemaProperties(schemaName)
        val jsonFields = json.fieldNames().asSequence().toSet()

        // Every JSON field should exist in the spec
        val extraInJson = jsonFields - specProps.keys
        assertTrue(extraInJson.isEmpty(), "$schemaName: JSON has fields not in spec: $extraInJson")

        // Every required spec field should exist in the JSON
        val required = schemaRequired(schemaName).toSet()
        val missingRequired = required - jsonFields
        assertTrue(missingRequired.isEmpty(), "$schemaName: JSON missing required fields: $missingRequired")
    }
}
