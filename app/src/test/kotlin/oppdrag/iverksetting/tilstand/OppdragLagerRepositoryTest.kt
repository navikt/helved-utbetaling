package oppdrag.iverksetting.tilstand

import no.nav.dagpenger.kontrakter.felles.Fagsystem
import no.nav.dagpenger.kontrakter.oppdrag.OppdragStatus
import no.trygdeetaten.skjema.oppdrag.Mmel
import oppdrag.TestEnvironment
import oppdrag.etUtbetalingsoppdrag
import oppdrag.iverksetting.mq.OppdragXmlMapper
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
    fun cleanup() {
        TestEnvironment.clearTables()
    }

    @Test
    fun `skal ikke lagre duplikat`() {
        val oppdragLager = etUtbetalingsoppdrag().somOppdragLager

        TestEnvironment.transaction { con ->
            OppdragLagerRepository.opprettOppdrag(oppdragLager, con)
        }

        val psqlException = assertThrows<PSQLException> {
            TestEnvironment.transaction { con ->
                OppdragLagerRepository.opprettOppdrag(oppdragLager, con)
            }
        }
        assertEquals("23505", psqlException.sqlState)
    }

    @Test
    fun `skal lagre to ulike iverksettinger samme behandling`() {
        val oppdragLager = etUtbetalingsoppdrag().somOppdragLager.copy(iverksetting_id = "1")

        TestEnvironment.transaction { con ->
            OppdragLagerRepository.opprettOppdrag(oppdragLager, con)
        }

        assertDoesNotThrow {
            TestEnvironment.transaction { con ->
                OppdragLagerRepository.opprettOppdrag(oppdragLager.copy(iverksetting_id = "2"), con)
            }
        }
    }

    @Test
    fun `skal lagre status`() {
        val oppdragLager =
            etUtbetalingsoppdrag().somOppdragLager.copy(
                status = OppdragStatus.LAGT_PÅ_KØ
            )

        TestEnvironment.transaction { con ->
            OppdragLagerRepository.opprettOppdrag(oppdragLager, con)
        }

        TestEnvironment.transaction { con ->
            val hentetOppdrag = OppdragLagerRepository.hentOppdrag(oppdragLager.id, con)

            assertEquals(OppdragStatus.LAGT_PÅ_KØ, hentetOppdrag.status)
            OppdragLagerRepository.oppdaterStatus(hentetOppdrag.id, OppdragStatus.KVITTERT_OK, con)

            val hentetOppdatertOppdrag = OppdragLagerRepository.hentOppdrag(hentetOppdrag.id, con)
            assertEquals(OppdragStatus.KVITTERT_OK, hentetOppdatertOppdrag.status)
        }
    }

    @Test
    fun `skal lagre kvitteringsmelding`() {
        val oppdragLager =
            etUtbetalingsoppdrag().somOppdragLager.copy(
                status = OppdragStatus.LAGT_PÅ_KØ
            )

        TestEnvironment.transaction { con ->
            OppdragLagerRepository.opprettOppdrag(oppdragLager, con)
        }

        TestEnvironment.transaction { con ->
            val hentetOppdrag = OppdragLagerRepository.hentOppdrag(oppdragLager.id, con)
            val kvitteringsmelding = avvistKvitteringsmelding()

            OppdragLagerRepository.oppdaterKvitteringsmelding(hentetOppdrag.id, kvitteringsmelding, con)

            val hentetOppdatertOppdrag = OppdragLagerRepository.hentOppdrag(oppdragLager.id, con)

            assertTrue(kvitteringsmelding.erLik(hentetOppdatertOppdrag.kvitteringsmelding!!))
        }
    }

    @Test
    fun `skal kun hente ut ett dp oppdrag for grensesnittavstemming`() {
        val dag = LocalDateTime.now()
        val startenPåDagen = dag.withHour(0).withMinute(0)
        val sluttenAvDagen = dag.withHour(23).withMinute(59)

        val baOppdragLager = etUtbetalingsoppdrag(dag).somOppdragLager
        val baOppdragLager2 = etUtbetalingsoppdrag(dag.minusDays(1)).somOppdragLager

        TestEnvironment.transaction { con ->
            OppdragLagerRepository.opprettOppdrag(baOppdragLager, con)
            OppdragLagerRepository.opprettOppdrag(baOppdragLager2, con)
        }

        TestEnvironment.transaction { con ->
            val oppdrageneTilGrensesnittavstemming =
                OppdragLagerRepository.hentIverksettingerForGrensesnittavstemming(
                    startenPåDagen,
                    sluttenAvDagen,
                    Fagsystem.DAGPENGER,
                    con,
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

    private fun avvistKvitteringsmelding() =
        OppdragXmlMapper.tilOppdrag(
            this::class.java.getResourceAsStream("/kvittering-avvist.xml")
                ?.bufferedReader().use { it?.readText() ?: "" },
        ).mmel

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