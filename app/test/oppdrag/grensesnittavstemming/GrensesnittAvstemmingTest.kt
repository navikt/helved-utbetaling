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
        TestEnvironment.clearMQ()
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

//private fun xml(avstemming: Avstemmingsdata): String {
//    val context: JAXBContext = JAXBContext.newInstance(Avstemmingsdata::class.java)
//    val marshaller = context.createMarshaller().apply {
//        setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
//    }
//    val stringWriter = StringWriter()
//    // see https://stackoverflow.com/a/5870064
//    val jaxbWrapper = JAXBElement(QName("uri", "local"), Avstemmingsdata::class.java, avstemming)
//    marshaller.marshal(jaxbWrapper, stringWriter)
//    return stringWriter.toString()
//}
