package libs.jackson

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import models.ApiError
import models.Fagsystem
import models.Info
import models.Simulering
import models.StatusReply
import models.StønadTypeDagpenger
import models.v2
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelvedObjectMapperTest {
    private val mapper = jacksonObjectMapper().registerHelvedModules()

    @Test
    fun `v2 simulering round-trips with discriminator and stønadstype`() {
        val simulering: Simulering = v2.Simulering(
            perioder = listOf(
                v2.Simuleringsperiode(
                    fom = LocalDate.of(2025, 1, 1),
                    tom = LocalDate.of(2025, 1, 31),
                    utbetalinger = listOf(
                        v2.SimulertUtbetaling(
                            fagsystem = Fagsystem.DAGPENGER,
                            sakId = "sak-1",
                            utbetalesTil = "12345678910",
                            stønadstype = StønadTypeDagpenger.DAGPENGER,
                            tidligereUtbetalt = 1,
                            nyttBeløp = 2,
                            posteringer = emptyList(),
                        )
                    ),
                )
            )
        )

        val json = mapper.writeValueAsString(simulering)

        assertTrue(json.contains("\"@type\":\"v2\""))
        assertTrue(json.contains("\"stønadstype\":\"DAGPENGER\""))
        assertEquals(simulering, mapper.readValue(json, Simulering::class.java))
    }

    @Test
    fun `info round-trips with discriminator`() {
        val info: Simulering = Info.OkUtenEndring(Fagsystem.AAP)
        val json = mapper.writeValueAsString(info)

        assertTrue(json.contains("\"@type\":\"info\""))
        assertEquals(info, mapper.readValue(json, Simulering::class.java))
    }

    @Test
    fun `api error omits throwable internals`() {
        val json = mapper.readTree(mapper.writeValueAsString(StatusReply.err(ApiError(422, "bad request"))))
        val error = json.get("error")

        assertEquals("bad request", error.get("msg").asText())
        assertFalse(error.has("cause"))
        assertFalse(error.has("localizedMessage"))
        assertFalse(error.has("message"))
        assertFalse(error.has("stackTrace"))
        assertFalse(error.has("suppressed"))
    }
}
