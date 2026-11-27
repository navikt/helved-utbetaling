package simulering

import kotlinx.serialization.json.*
import libs.utils.Resource
import models.*
import no.nav.system.os.entiteter.oppdragskjema.Belopsgrense
import no.nav.system.os.entiteter.oppdragskjema.Grad as SoapGrad
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest.SimuleringsPeriode
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.*

class SokosMapperTest {
    @Test
    fun `request beholder datoer referanser desimaler grader og simuleringsperiode`() {
        val source = simulering(fagområde = "AAP").apply {
            request.simuleringsPeriode = SimuleringsPeriode().apply {
                datoSimulerFom = "2026-01-01"
                datoSimulerTom = "2026-01-31"
            }
            request.oppdrag.oppdragslinjes.single().apply {
                sats = BigDecimal("700.123456789")
                refOppdragsId = 9999999999L
                refLinjeId = BigInteger("123456789")
                grads.add(SoapGrad().apply { typeGrad = "UFOR"; grad = BigInteger("50") })
            }
        }

        val mapped = source.toRestRequest().operation.input.requestSimulering
        val line = mapped.oppdragslinje.single()

        assertEquals("2026-01-01", mapped.simuleringsPeriode?.datoSimulerFom)
        assertEquals("2026-01-31", mapped.simuleringsPeriode?.datoSimulerTom)
        assertEquals(BigDecimal("700.123456789"), line.sats)
        assertEquals(9999999999L, line.refOppdragsId)
        assertEquals(BigInteger("123456789"), line.refLinjeId)
        assertEquals(BigInteger("50"), line.gradOppdragslinje.single().grad)
        assertEquals("Z999999", line.attestant.single().attestantId)
        val wire = libs.kotlinx.KotlinxJson.encodeToJsonElement(source.toRestRequest()).toString()
        assertTrue(wire.contains("\"sats\":700.123456789"))
    }

    @Test
    fun `belopsgrenser avvises fremfor aa droppes i shadow request`() {
        val source = simulering().apply { request.oppdrag.belopsgrenses.add(Belopsgrense()) }
        assertFailsWith<IllegalArgumentException> { source.toRestRequest() }
    }

    @Test
    fun `REST perioder med samme dato samles foer domenemapping`() {
        val response = restResponse()
        val calculation = requireNotNull(response.beregning)
        val duplicate = calculation.copy(periodeStoppnivaa = calculation.periodeStoppnivaa + calculation.periodeStoppnivaa)

        val result = response.copy(beregning = duplicate).toSimulering(Fagsystem.AAP)

        assertIs<v2.Simulering>(result)
        assertEquals(1, result.perioder.size)
        assertEquals(2, result.perioder.single().utbetalinger.size)
        assertEquals(600, result.perioder.single().utbetalinger.first().nyttBeløp)
        assertEquals(100, result.perioder.single().utbetalinger.first().tidligereUtbetalt)
    }

    @Test
    fun `advarsel mapper beregning mens alvorlige statuser blir shadow feil`() {
        val response = restResponse()
        assertIs<v2.Simulering>(response.copy(statusKode = 4).toSimulering(Fagsystem.AAP))
        listOf(8, 12).forEach { status ->
            val result = response.copy(statusKode = status, statusMelding = "Test failure").toSimulering(Fagsystem.AAP)
            assertIs<Info>(result)
            assertEquals(Info.Status.FEILET, result.status)
        }
    }

    @Test
    fun `eksisterende oppdrag fra REST mapper til samme ugyldige request som SOAP`() {
        val response = libs.kotlinx.KotlinxJson.decodeFromString<SimuleringResponse>("""
            {"OsSimulerBeregningOperationResponse":{"ZS1OCont":{"responsSimulering":{"statusKode":8,"statusMelding":"B110008F - Oppdraget finnes fra før","beregning":{"gjelderId":"","gjelderNavn":"","datoBeregnet":"","kodeFaggruppe":"","belopTotalt":0.0}}}}}
        """).operation.output.responsSimulering

        assertEquals(
            Info.UgyldigRequest(Fagsystem.AAP, "Utbetaling med SakId/BehandlingId finnes fra før"),
            response.toSimulering(Fagsystem.AAP)
        )
    }

    @Test
    fun `observert REST feil normaliseres for baade v1 og v2`() {
        listOf(Fagsystem.AAP, Fagsystem.TILLEGGSSTØNADER).forEach { fagsystem ->
            val response = ResponsSimulering(8, "B110008F - Oppdraget finnes fra før")
            assertEquals(Info.UgyldigRequest(fagsystem, "Utbetaling med SakId/BehandlingId finnes fra før"), response.toSimulering(fagsystem))
        }
    }

    @Test
    fun `uobserverte forretningsfeil normaliseres ikke`() {
        val messages = listOf(
            "OPPDRAGET/FAGSYSTEM-ID finnes ikke fra før",
            "Oppdraget finnes fra før",
            "Referert vedtak/linje ikke funnet",
            "Navn på person ikke funnet i PDL",
            "Personen finnes ikke i PDL",
            "DELYTELSE-ID finnes i oppdragsbasen fra før"
        )
        messages.forEach { message ->
            assertEquals(Info.Feilet(Fagsystem.AAP, "Sokos status 8: $message"), ResponsSimulering(8, message).toSimulering(Fagsystem.AAP))
        }
        val response = ResponsSimulering(12, "B110008F - Oppdraget finnes fra før")
        assertEquals(Info.Feilet(Fagsystem.AAP, "Sokos status 12: ${response.statusMelding}"), response.toSimulering(Fagsystem.AAP))
    }

    @Test
    fun `ukjente REST feil beholder status og melding`() {
        listOf(null, "", "Annen feil", "Navn er ikke funnet ved oppslag").forEach { message ->
            val response = ResponsSimulering(8, message)
            assertEquals(Info.Feilet(Fagsystem.AAP, "Sokos status 8: ${message.orEmpty()}"), response.toSimulering(Fagsystem.AAP))
        }
    }

    @Test
    fun `kjente feilmeldinger overstyrer ikke vellykket beregning eller advarsel`() {
        listOf(0, 4).forEach { status ->
            val response = restResponse().copy(statusKode = status)
            listOf("B110008F - Oppdraget finnes fra før", "Navn er ikke funnet ved oppslag").forEach { message ->
                assertEquals(response.toSimulering(Fagsystem.AAP), response.copy(statusMelding = message).toSimulering(Fagsystem.AAP))
            }
        }
    }

    @Test
    fun `tom beregning bevarer eksisterende v1 og v2 semantikk`() {
        val response = ResponsSimulering(0)
        val v1 = response.toSimulering(Fagsystem.TILLEGGSSTØNADER)
        assertIs<Info>(v1)
        assertEquals(Info.Status.OK_UTEN_ENDRING, v1.status)
        val v2 = response.toSimulering(Fagsystem.AAP)
        assertIs<v2.Simulering>(v2)
        assertTrue(v2.perioder.isEmpty())
    }

    private fun restResponse(): ResponsSimulering = libs.kotlinx.KotlinxJson.decodeFromString<SimuleringResponse>(
        Resource.read("/sokos-response.json").replace("FAGOMRADE", "AAP")
    ).operation.output.responsSimulering
}
