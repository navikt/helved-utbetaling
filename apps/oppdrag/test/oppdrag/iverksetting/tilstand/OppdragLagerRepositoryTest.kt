package oppdrag.iverksetting.tilstand

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import libs.utils.Resource
import libs.xml.XMLMapper
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.TestRuntime
import oppdrag.etUtbetalingsoppdrag
import oppdrag.somOppdragLager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class OppdragLagerRepositoryTest {

    @AfterEach
    fun cleanup() = runBlocking {
        TestRuntime.cleanup()
    }

    @Test
    fun `skal ikke lagre duplikat`() = runTest(TestRuntime.context) {
        val oppdragLager = etUtbetalingsoppdrag().somOppdragLager

        transaction {
            OppdragLagerRepository.opprettOppdrag(oppdragLager)
        }

        val psqlException = assertThrows<PSQLException> {
            transaction {
                OppdragLagerRepository.opprettOppdrag(oppdragLager)
            }
        }
        assertEquals("23505", psqlException.sqlState)
    }

    @Test
    fun `skal lagre to ulike iverksettinger samme behandling`() = runTest(TestRuntime.context) {
        val oppdragLager = etUtbetalingsoppdrag().somOppdragLager.copy(iverksetting_id = "1")

        transaction {
            OppdragLagerRepository.opprettOppdrag(oppdragLager)
        }

        assertDoesNotThrow {
            transaction {
                OppdragLagerRepository.opprettOppdrag(oppdragLager.copy(iverksetting_id = "2"))
            }
        }
    }

    @Test
    fun `skal lagre status`() = runTest(TestRuntime.context) {
        val oppdragLager =
            etUtbetalingsoppdrag().somOppdragLager.copy(
                status = OppdragStatus.LAGT_PÅ_KØ
            )
        transaction {
            OppdragLagerRepository.opprettOppdrag(oppdragLager)
        }
        transaction {
            val hentetOppdrag = OppdragLagerRepository.hentOppdrag(oppdragLager.id)

            assertEquals(OppdragStatus.LAGT_PÅ_KØ, hentetOppdrag.status)
            OppdragLagerRepository.oppdaterStatus(hentetOppdrag.id, OppdragStatus.KVITTERT_OK)

            val hentetOppdatertOppdrag = OppdragLagerRepository.hentOppdrag(hentetOppdrag.id)
            assertEquals(OppdragStatus.KVITTERT_OK, hentetOppdatertOppdrag.status)
        }
    }

    @Test
    fun `skal lagre kvitteringsmelding`() = runTest(TestRuntime.context) {
        val oppdragLager =
            etUtbetalingsoppdrag().somOppdragLager.copy(
                status = OppdragStatus.LAGT_PÅ_KØ
            )

        transaction {
            OppdragLagerRepository.opprettOppdrag(oppdragLager)
        }

        transaction {
            OppdragLagerRepository.opprettOppdrag(oppdragLager.copy(iverksetting_id = "2"))
            val hentetOppdrag = OppdragLagerRepository.hentOppdrag(oppdragLager.id)
            val kvitteringsmelding = avvistKvitteringsmelding()

            OppdragLagerRepository.oppdaterKvitteringsmelding(hentetOppdrag.id, kvitteringsmelding)

            val hentetOppdatertOppdrag = OppdragLagerRepository.hentOppdrag(oppdragLager.id)

            assertTrue(kvitteringsmelding.erLik(hentetOppdatertOppdrag.kvitteringsmelding!!))
        }
    }

    @Test
    fun `skal kun hente ut ett dp oppdrag for grensesnittavstemming`() = runTest(TestRuntime.context) {
        val dag = LocalDateTime.now()
        val startenPåDagen = dag.withHour(0).withMinute(0)
        val sluttenAvDagen = dag.withHour(23).withMinute(59)

        val baOppdragLager = etUtbetalingsoppdrag(dag).somOppdragLager
        val baOppdragLager2 = etUtbetalingsoppdrag(dag.minusDays(1)).somOppdragLager

        transaction {
            OppdragLagerRepository.opprettOppdrag(baOppdragLager)
            OppdragLagerRepository.opprettOppdrag(baOppdragLager2)
        }

        transaction {
            val oppdrageneTilGrensesnittavstemming =
                OppdragLagerRepository.hentIverksettingerForGrensesnittavstemming(
                    startenPåDagen,
                    sluttenAvDagen,
                    Fagsystem.DAGPENGER,
                )

            assertEquals(1, oppdrageneTilGrensesnittavstemming.size)
            assertEquals("DP", oppdrageneTilGrensesnittavstemming.first().fagsystem)
            assertEquals(
                dag.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss")),
                oppdrageneTilGrensesnittavstemming
                    .first()
                    .avstemming_tidspunkt
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss")),
            )
        }
    }

    private val mapper = XMLMapper<Oppdrag>()

    private fun avvistKvitteringsmelding(): Mmel {
        return mapper.readValue(Resource.read("/xml/kvittering-funksjonell-feil.xml")).mmel
    }

    private fun Mmel.erLik(andre: Mmel) =
        systemId == andre.systemId &&
                kodeMelding == andre.kodeMelding &&
                alvorlighetsgrad == andre.alvorlighetsgrad &&
                beskrMelding == andre.beskrMelding &&
                sqlKode == andre.sqlKode &&
                sqlState == andre.sqlState &&
                sqlMelding == andre.sqlMelding &&
                mqCompletionKode == andre.mqCompletionKode &&
                mqReasonKode == andre.mqReasonKode &&
                programId == andre.programId &&
                sectionNavn == andre.sectionNavn
}