package utsjekk.utbetaling.simulering

import com.fasterxml.jackson.module.kotlin.readValue
import libs.utils.Resource
import models.kontrakter.objectMapper
import org.junit.jupiter.api.Test
import utsjekk.simulering.SimuleringApi
import utsjekk.simulering.client
import kotlin.test.assertEquals

class SimuleringMapperTest {

    @Test
    fun `mapper til simulering`() {
        val json = Resource.read("/json/simulering_forskjellige_klassekoder.json")
        val response = objectMapper.readValue<client.SimuleringResponse>(json)
        val mapped = SimuleringApi.from(response)

        assertEquals(8, mapped.perioder.size)
    }

    @Test
    fun `beregner nytt beløp`() {
        val json = Resource.read("/json/simulering_forskjellige_klassekoder.json")
        val response = objectMapper.readValue<client.SimuleringResponse>(json)
        val mapped = SimuleringApi.from(response)

        assertEquals(2190, mapped.perioder[0].utbetalinger[0].nyttBeløp)
        assertEquals(-201, mapped.perioder[1].utbetalinger[0].nyttBeløp)
        assertEquals(2706, mapped.perioder[1].utbetalinger[1].nyttBeløp)
        assertEquals( 603, mapped.perioder[2].utbetalinger[0].nyttBeløp)
        assertEquals(1205, mapped.perioder[3].utbetalinger[0].nyttBeløp)
        assertEquals(1305, mapped.perioder[4].utbetalinger[0].nyttBeløp)
        assertEquals(1105, mapped.perioder[5].utbetalinger[0].nyttBeløp)
        assertEquals( 502, mapped.perioder[6].utbetalinger[0].nyttBeløp)
        assertEquals( 502, mapped.perioder[7].utbetalinger[0].nyttBeløp)
    }

    @Test
    fun `beregner tidligere utbetalt`() {
        val json = Resource.read("/json/simulering_forskjellige_klassekoder.json")
        val response = objectMapper.readValue<client.SimuleringResponse>(json)
        val mapped = SimuleringApi.from(response)

        assertEquals(  0, mapped.perioder[0].utbetalinger[0].tidligereUtbetalt)
        assertEquals(502, mapped.perioder[1].utbetalinger[0].tidligereUtbetalt)
        assertEquals(  0, mapped.perioder[1].utbetalinger[1].tidligereUtbetalt)
        assertEquals(  0, mapped.perioder[2].utbetalinger[0].tidligereUtbetalt)
        assertEquals(  0, mapped.perioder[3].utbetalinger[0].tidligereUtbetalt)
        assertEquals(  0, mapped.perioder[4].utbetalinger[0].tidligereUtbetalt)
        assertEquals(  0, mapped.perioder[5].utbetalinger[0].tidligereUtbetalt)
        assertEquals(  0, mapped.perioder[6].utbetalinger[0].tidligereUtbetalt)
        assertEquals(  0, mapped.perioder[7].utbetalinger[0].tidligereUtbetalt)
    }

}
