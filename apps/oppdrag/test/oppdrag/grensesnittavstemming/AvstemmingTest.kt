package oppdrag.grensesnittavstemming

import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import libs.postgres.concurrency.transaction
import libs.xml.XMLMapper
import models.kontrakter.felles.Fagsystem
import models.kontrakter.oppdrag.GrensesnittavstemmingRequest
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import oppdrag.*
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class AvstemmingTest {
    private val mapper = XMLMapper<Avstemmingsdata>()

    @AfterEach
    fun cleanup() = TestRuntime.clear()

    // @Test
    // fun `skal avstemme eksisterende oppdrag`(): Unit = runTest(TestRuntime.context) {
    //     val oppdragLager = etUtbetalingsoppdrag().somOppdragLager
    //     val avstemming = GrensesnittavstemmingRequest(
    //         fagsystem = Fagsystem.DAGPENGER,
    //         fra = LocalDateTime.now().withDayOfMonth(1),
    //         til = LocalDateTime.now().plusMonths(1)
    //     )
    //
    //     withContext(TestRuntime.context) {
    //         transaction {
    //             OppdragLagerRepository.opprettOppdrag(oppdragLager)
    //         }
    //     }
    //
    //     httpClient.post("/grensesnittavstemming") {
    //         contentType(ContentType.Application.Json)
    //         bearerAuth(TestRuntime.azure.generateToken())
    //         setBody(avstemming)
    //     }.also {
    //         assertEquals(HttpStatusCode.Created, it.status)
    //     }
    //
    //     val actual = TestRuntime.oppdrag.avstemmingKø
    //         .getReceived()
    //         .map { it.text.replaceBetweenXmlTag("avleverendeAvstemmingId", "redacted") }
    //         .map { it.replaceBetweenXmlTag("tidspunkt", "redacted") } // fixme: på GHA blir denne 1ms off
    //
    //     val avstemmingMapper = AvstemmingMapper(
    //         oppdragsliste = listOf(oppdragLager),
    //         fagsystem = avstemming.fagsystem,
    //         fom = avstemming.fra,
    //         tom = avstemming.til
    //     )
    //
    //     val expected = avstemmingMapper
    //         .lagAvstemmingsmeldinger()
    //         .map(mapper::writeValueAsString)
    //         .map(TestRuntime.oppdrag::createMessage)
    //         .map { it.text.replaceBetweenXmlTag("avleverendeAvstemmingId", "redacted") }
    //         .map { it.replaceBetweenXmlTag("tidspunkt", "redacted") } // fixme: på GHA blir denne 1ms off
    //
    //     assertEquals(expected, actual)
    // }
}
