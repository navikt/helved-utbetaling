package oppdrag.grensesnittavstemming

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import oppdrag.TestEnvironment
import oppdrag.etUtbetalingsoppdrag
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import oppdrag.server
import oppdrag.somOppdragLager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.time.LocalDateTime
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBElement
import javax.xml.bind.Marshaller
import javax.xml.namespace.QName
import kotlin.test.assertEquals

class GrensesnittAvstemmingTest {

    @AfterEach
    fun cleanup() {
        TestEnvironment.clearMQ()
    }

    @Test
    fun `skal avstemme eksisterende oppdrag`() {
        val avstemming = GrensesnittavstemmingRequest(
            fagsystem = Fagsystem.DAGPENGER,
            fra = LocalDateTime.now().withDayOfMonth(1),
            til = LocalDateTime.now().plusMonths(1)
        )

        val oppdragLager = etUtbetalingsoppdrag().somOppdragLager

        testApplication {
            application { server(TestEnvironment.config) }

            TestEnvironment.transaction {
                OppdragLagerRepository.opprettOppdrag(oppdragLager, it)
            }

            val response = httpClient.post("/grensesnittavstemming") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestEnvironment.generateToken())
                setBody(avstemming)
            }

            assertEquals(HttpStatusCode.Created, response.status)

            val actual = TestEnvironment.oppdrag.avstemmingKøListener
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
                .map(::xml)
                .map(TestEnvironment::createSoapMessage)
                .map { it.text.replaceBetweenXmlTag("avleverendeAvstemmingId", "redacted") }

            assertEquals(expected, actual)
        }
    }
}

private fun String.replaceBetweenXmlTag(tag: String, replacement: String): String {
    return replace(
        regex = Regex("(?<=<$tag>).*(?=</$tag>)"),
        replacement = replacement
    )
}

private fun xml(avstemming: Avstemmingsdata): String {
    val context: JAXBContext = JAXBContext.newInstance(Avstemmingsdata::class.java)
    val marshaller = context.createMarshaller().apply {
        setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
    }
    val stringWriter = StringWriter()
    // see https://stackoverflow.com/a/5870064
    val jaxbWrapper = JAXBElement(QName("uri", "local"), Avstemmingsdata::class.java, avstemming)
    marshaller.marshal(jaxbWrapper, stringWriter)
    return stringWriter.toString()
}

private val ApplicationTestBuilder.httpClient: HttpClient
    get() =
        createClient {
            install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                }
            }
        }
