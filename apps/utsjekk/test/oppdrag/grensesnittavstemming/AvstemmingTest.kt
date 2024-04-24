package oppdrag.grensesnittavstemming

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import libs.xml.XMLMapper
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import oppdrag.*
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import javax.xml.namespace.QName
import kotlin.test.assertEquals

class AvstemmingTest {
    private val mapper = XMLMapper<Avstemmingsdata>()

    @AfterEach
    fun cleanup() {
        TestRuntime.clearTables()
        TestRuntime.clearMQ()
    }

    @Test
    fun `skal avstemme eksisterende oppdrag`() {
        val oppdragLager = etUtbetalingsoppdrag().somOppdragLager
        val avstemming = GrensesnittavstemmingRequest(
            fagsystem = Fagsystem.DAGPENGER,
            fra = LocalDateTime.now().withDayOfMonth(1),
            til = LocalDateTime.now().plusMonths(1)
        )

        testApplication {
            application {
                server(TestRuntime.config)
            }

            TestRuntime.postgres.transaction {
                OppdragLagerRepository.opprettOppdrag(oppdragLager, it)
            }

            val response = httpClient.post("/grensesnittavstemming") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken())
                setBody(avstemming)
            }

            assertEquals(HttpStatusCode.Created, response.status)

            val actual = TestRuntime.oppdrag.avstemmingKø
                .getReceived()
                .map { it.text.replaceBetweenXmlTag("avleverendeAvstemmingId", "redacted") }
                .map { it.replaceBetweenXmlTag("tidspunkt", "redacted") } // fixme: på GHA blir denne 1ms off

            val avstemmingMapper = AvstemmingMapper(
                oppdragsliste = listOf(oppdragLager),
                fagsystem = avstemming.fagsystem,
                fom = avstemming.fra,
                tom = avstemming.til
            )

            val expected = avstemmingMapper
                .lagAvstemmingsmeldinger()
                .map {
                    val avstem = mapper.wrapInTag(it, QName("uri", "local"))
                    mapper.writeValueAsString(avstem)
                }
                .map(TestRuntime.oppdrag::createMessage)
                .map { it.text.replaceBetweenXmlTag("avleverendeAvstemmingId", "redacted") }
                .map { it.replaceBetweenXmlTag("tidspunkt", "redacted") } // fixme: på GHA blir denne 1ms off

            assertEquals(expected, actual)
        }
    }
}
