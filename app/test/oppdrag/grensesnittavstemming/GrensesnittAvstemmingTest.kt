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

class GrensesnittAvstemmingTest {
    private val mapper = XMLMapper<Avstemmingsdata>()

    @AfterEach
    fun cleanup() {
        TestEnvironment.clearTables()
        TestEnvironment.clearMQ()
        assertEquals(0, TestEnvironment.oppdrag.sendKø.queueDepth())
        assertEquals(0, TestEnvironment.oppdrag.avstemmingKø.queueDepth())
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
                server(TestEnvironment.config)
            }

            TestEnvironment.postgres.transaction {
                OppdragLagerRepository.opprettOppdrag(oppdragLager, it)
            }

            TestEnvironment.clearMQ() // hack for å fjerne meldinger som henger igjen i testcontaineren
            val response = httpClient.post("/grensesnittavstemming") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestEnvironment.azure.generateToken())
                setBody(avstemming)
            }

            assertEquals(HttpStatusCode.Created, response.status)

            val actual = TestEnvironment.oppdrag.avstemmingKø
                .getReceived()
                .map { it.text.replaceBetweenXmlTag("avleverendeAvstemmingId", "redacted") }

            val avstemmingMapper = GrensesnittavstemmingMapper(
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
                .map(TestEnvironment.oppdrag::createMessage)
                .map { it.text.replaceBetweenXmlTag("avleverendeAvstemmingId", "redacted") }

            assertEquals(expected, actual)
        }
    }
}
