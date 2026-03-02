package abetal

import models.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * Parameterized validation tests for Utbetaling.
 * Tests common validation rules that apply across all fagsystem types.
 */
internal class ValidationParameterizedTest {

    @TestFactory
    fun `validation error cases`() = listOf(
        DynamicTest.dynamicTest("error ved årsskifte") {
            val utbet = createUtbetaling(
                perioder = listOf(
                    Utbetalingsperiode(31.des, 31.des, 100u),
                    Utbetalingsperiode(1.jan, 1.jan, 100u),
                )
            )

            val err = assertThrows<ApiError> {
                utbet.validate()
            }
            assertEquals("Engangsutbetalinger kan ikke strekke seg over årsskifte", err.msg)
        },
        
        DynamicTest.dynamicTest("error ved for lang sakId") {
            val utbet = createUtbetaling(
                sakId = SakId("012345678901234567890123456789123"),
                perioder = listOf(Utbetalingsperiode(31.des, 31.des, 100u))
            )

            val err = assertThrows<ApiError> {
                utbet.validate()
            }
            assertEquals("Sak-ID må være mellom 1 og 25 tegn", err.msg)
        },
        
        DynamicTest.dynamicTest("error ved for lang behandlingId") {
            val utbet = createUtbetaling(
                behandlingId = BehandlingId("012345678901234567890123456789123"),
                perioder = listOf(Utbetalingsperiode(1.jan, 1.jan, 100u))
            )

            val err = assertThrows<ApiError> {
                utbet.validate()
            }
            assertEquals("Behandling-ID må være mellom 1 og 30 tegn", err.msg)
        },
        
        DynamicTest.dynamicTest("error ved to perioder med samme fom") {
            val utbet = createUtbetaling(
                periodetype = Periodetype.DAG,
                perioder = listOf(
                    Utbetalingsperiode(1.jan, 2.jan, 100u),
                    Utbetalingsperiode(1.jan, 3.jan, 100u),
                )
            )

            val err = assertThrows<ApiError> {
                utbet.validate()
            }
            assertEquals("Kan ikke sende inn duplikate perioder", err.msg)
        },
        
        DynamicTest.dynamicTest("error ved to perioder med samme tom") {
            val utbet = createUtbetaling(
                periodetype = Periodetype.DAG,
                perioder = listOf(
                    Utbetalingsperiode(2.jan, 3.jan, 100u),
                    Utbetalingsperiode(1.jan, 3.jan, 100u),
                )
            )

            val err = assertThrows<ApiError> {
                utbet.validate()
            }
            assertEquals("Kan ikke sende inn duplikate perioder", err.msg)
        },
        
        DynamicTest.dynamicTest("error ved tom før fom") {
            val utbet = createUtbetaling(
                periodetype = Periodetype.DAG,
                perioder = listOf(Utbetalingsperiode(5.jan, 3.jan, 100u))
            )

            val err = assertThrows<ApiError> {
                utbet.validate()
            }
            assertEquals("Tom må være >= fom", err.msg)
        },
        
        DynamicTest.dynamicTest("error ved for mange perioder") {
            val perioder = buildList<Utbetalingsperiode> {
                for (i in 1L..1001L) {
                    add(Utbetalingsperiode(
                        fom = LocalDate.now().minusDays(i),
                        tom = LocalDate.now().minusDays(i),
                        beløp = 100u,
                    ))
                }
            }
            val utbet = createUtbetaling(
                periodetype = Periodetype.DAG,
                perioder = perioder
            )

            val err = assertThrows<ApiError> {
                utbet.validate()
            }
            assertEquals("Utbetalinger kan ikke strekke seg over 1000 dager", err.msg)
        },
        
        DynamicTest.dynamicTest("error ved manglende perioder") {
            val utbet = createUtbetaling(
                periodetype = Periodetype.DAG,
                perioder = emptyList()
            )

            val err = assertThrows<ApiError> {
                utbet.validate()
            }
            assertEquals("Mangler perioder", err.msg)
        }
    )

    private fun createUtbetaling(
        sakId: SakId = SakId("$nextInt"),
        behandlingId: BehandlingId = BehandlingId("$nextInt"),
        periodetype: Periodetype = Periodetype.EN_GANG,
        perioder: List<Utbetalingsperiode>
    ) = Utbetaling(
        dryrun = false,
        originalKey = "123",
        fagsystem = Fagsystem.AAP,
        uid = randomUtbetalingId(),
        action = Action.CREATE,
        førsteUtbetalingPåSak = true,
        sakId = sakId,
        behandlingId = behandlingId,
        lastPeriodeId = PeriodeId(),
        personident = Personident("12345678910"),
        vedtakstidspunkt = LocalDateTime.now(),
        stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
        beslutterId = Navident("123"),
        saksbehandlerId = Navident("123"),
        periodetype = periodetype,
        avvent = null,
        perioder = perioder,
    )
}
