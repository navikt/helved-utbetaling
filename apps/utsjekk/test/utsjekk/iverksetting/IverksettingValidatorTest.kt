package utsjekk.iverksetting

import TestData
import TestRuntime
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import utsjekk.ApiError
import java.time.LocalDateTime

class IverksettingValidatorTest {

    @Test
    fun `skal få BAD_REQUEST når forrige iverksetting er knyttet til en annen sak`() = runTest(TestRuntime.context) {
        val forrigeIverksetting = TestData.domain.iverksetting()
        val now = LocalDateTime.now()

        transaction {
            IverksettingDao(
                forrigeIverksetting.behandlingId,
                forrigeIverksetting,
                now,
            ).insert()
        }


        val iverksetting = TestData.domain.iverksetting(
            forrigeBehandlingId = forrigeIverksetting.behandlingId,
        )

        val err = assertThrows<ApiError.BadRequest> {
            transaction {
                IverksettingValidator.validerAtIverksettingGjelderSammeSakSomForrigeIverksetting(iverksetting)
            }
        }

        assertTrue(err.message.contains("Fant ikke iverksetting med sakId ${iverksetting.sakId}"))

    }

    @Test
    fun `skal få BAD_REQUEST når forrige iverksetting har annen behandlingId enn siste mottatte iverksetting`() =
        runTest(TestRuntime.context) {

            val sistMottattDao = transaction {
                TestData.dao.iverksetting(
                    mottattTidspunkt = LocalDateTime.now().minusDays(2),
                    iverksetting = TestData.domain.iverksetting(sakId = SakId(RandomOSURId.generate())),
                ).also { it.insert() }
            }

            val forrigeIverksetting = TestData.domain.iverksetting(
                sakId = sistMottattDao.data.sakId,
            )

            val iverksetting = TestData.domain.iverksetting(
                sakId = sistMottattDao.data.sakId,
                forrigeBehandlingId = forrigeIverksetting.behandlingId,
            )

            val err = assertThrows<ApiError.BadRequest> {
                transaction {
                    IverksettingValidator.validerAtForrigeIverksettingErLikSisteMottatteIverksetting(iverksetting)
                }
            }

            assertTrue(err.message.contains("Forrige iverksetting stemmer ikke med siste mottatte iverksetting på saken."))
        }

    @Test
    fun `skal få BAD_REQUEST når forrige iverksetting har annen iverksettingId enn siste mottatte iverksetting`() =
        runTest(TestRuntime.context) {
            val sistMottattDao = transaction {
                TestData.dao.iverksetting(
                    mottattTidspunkt = LocalDateTime.now().minusDays(2),
                    iverksetting = TestData.domain.iverksetting(
                        sakId = SakId(RandomOSURId.generate()),
                        behandlingId = BehandlingId(RandomOSURId.generate()),
                        iverksettingId = IverksettingId(RandomOSURId.generate()),
                    ),
                ).also { it.insert() }
            }

            val forrigeIverksetting = TestData.domain.iverksetting(
                sakId = sistMottattDao.data.sakId,
                behandlingId = sistMottattDao.data.behandlingId,
                iverksettingId = IverksettingId(RandomOSURId.generate()),
            )

            val iverksetting = TestData.domain.iverksetting(
                sakId = sistMottattDao.data.sakId,
                behandlingId = sistMottattDao.data.behandlingId,
                forrigeBehandlingId = forrigeIverksetting.behandlingId,
                forrigeIverksettingId = forrigeIverksetting.iverksettingId
            )

            val err = assertThrows<ApiError.BadRequest> {
                transaction {
                    IverksettingValidator.validerAtForrigeIverksettingErLikSisteMottatteIverksetting(iverksetting)
                }
            }

            assertTrue(err.message.contains("Forrige iverksetting stemmer ikke med siste mottatte iverksetting på saken."))
        }

    @Test
    fun `skal få BAD_REQUEST når forrige iverksetting ikke er satt og vi har mottatt iverksetting på saken før`() =
        runTest(TestRuntime.context) {
            val sisteMottattDao = transaction {
                TestData.dao.iverksetting().also { it.insert() }
            }

            val iverksetting = TestData.domain.iverksetting(
                sakId = sisteMottattDao.data.sakId
            )

            val err = assertThrows<ApiError.BadRequest> {
                transaction {
                    IverksettingValidator.validerAtForrigeIverksettingErLikSisteMottatteIverksetting(iverksetting)
                }
            }

            assertTrue(err.message.contains("Forrige iverksetting stemmer ikke med siste mottatte iverksetting på saken."))
        }

    @Test
    fun `skal få BAD_REQUEST når forrige iverksetting er satt og vi ikke har mottatt iverksetting på saken før`() =
        runTest(TestRuntime.context) {
            val iverksetting = TestData.domain.iverksetting(
                forrigeBehandlingId = BehandlingId(RandomOSURId.generate())
            )

            val err = assertThrows<ApiError.BadRequest> {
                transaction {
                    IverksettingValidator.validerAtForrigeIverksettingErLikSisteMottatteIverksetting(iverksetting)
                }
            }

            assertTrue(err.message.contains("Det er ikke registrert noen tidligere iverksettinger på saken, men forrigeIverksetting er satt"))
        }

    @Test
    fun `skal få CONFLICT når iverksetting allerede er mottatt`() =
        runTest(TestRuntime.context) {
            val dao = transaction {
                TestData.dao.iverksetting().also {
                    it.insert()
                }
            }

            val err = assertThrows<ApiError.Conflict> {
                transaction {
                    IverksettingValidator.validerAtIverksettingIkkeAlleredeErMottatt(dao.data)
                }
            }

            assertTrue(err.message.contains("Iverksettingen er allerede mottatt"))
        }

    @Test
    fun `skal få CONFLICT når forrige iverksetting ikke er ferdig og ok mot oppdrag`() = runTest(TestRuntime.context) {
        val dao = transaction {
            TestData.dao.iverksettingResultat(
                resultat = OppdragResultat(
                    oppdragStatus = OppdragStatus.KVITTERT_MED_MANGLER,
                )
            ).also {
                it.insert()
            }
        }

        val iverksetting = TestData.domain.iverksetting(
            sakId = dao.sakId,
            fagsystem = dao.fagsystem,
            forrigeBehandlingId = dao.behandlingId
        )

        val err = assertThrows<ApiError.Conflict> {
            transaction {
                IverksettingValidator.validerAtForrigeIverksettingErFerdigIverksattMotOppdrag(iverksetting)
            }
        }

        assertTrue(err.message.contains("Forrige iverksetting er ikke ferdig iverksatt mot Oppdragssystemet"))
    }
}