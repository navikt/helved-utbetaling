package branntaarn

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import libs.kotlinx.KotlinxJson
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrannTest {

    @Test
    fun `ManglendeKvittering serialiseres med riktig type-diskriminator`() {
        val brann: Brann = ManglendeKvittering(
            key = "abc",
            timeout = LocalDateTime.of(2026, 1, 1, 10, 0),
            sakId = "sak-1",
            fagsystem = "AAP",
        )

        val json = KotlinxJson.encodeToJsonElement(Brann.serializer(), brann).jsonObject

        assertEquals("manglende_kvittering", json["@type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `Dobbeltutbetaling serialiseres med riktig type-diskriminator`() {
        val brann: Brann = Dobbeltutbetaling(
            behandlingId = "beh-1",
            klassekode = "DAGP",
            fom = LocalDate.of(2026, 1, 1),
            tom = LocalDate.of(2026, 1, 31),
            beløp = 1000u,
            kilder = emptyMap(),
        )

        val json = KotlinxJson.encodeToJsonElement(Brann.serializer(), brann).jsonObject

        assertEquals("dobbelt_utbetaling", json["@type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `JSON med manglende_kvittering deserialiseres til ManglendeKvittering`() {
        val json = """{"@type":"manglende_kvittering","key":"abc","timeout":"2026-01-01T10:00:00","sakId":"sak-1","fagsystem":"AAP"}"""

        val result = KotlinxJson.decodeFromString(Brann.serializer(), json)

        assertTrue(result is ManglendeKvittering, "Expected ManglendeKvittering, got ${result::class}")
        assertEquals("abc", result.key)
    }

    @Test
    fun `JSON med dobbelt_utbetaling deserialiseres til Dobbeltutbetaling`() {
        val json = """{"@type":"dobbelt_utbetaling","behandlingId":"beh-1","klassekode":"DAGP","fom":"2026-01-01","tom":"2026-01-31","beløp":1000,"kilder":{}}"""

        val result = KotlinxJson.decodeFromString(Brann.serializer(), json)

        assertTrue(result is Dobbeltutbetaling, "Expected Dobbeltutbetaling, got ${result::class}")
        assertEquals("beh-1", result.behandlingId)
    }
}
