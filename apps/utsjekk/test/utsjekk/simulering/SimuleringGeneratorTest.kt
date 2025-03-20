package utsjekk.simulering

import TestData.domain.simuleringDetaljer
import TestData.dto.api.oppsummeringForPeriode
import TestData.dto.api.simuleringResponse
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import utsjekk.simulering.TestCaser.etterbetaling
import utsjekk.simulering.TestCaser.feilutbetaling
import utsjekk.simulering.TestCaser.justeringInnenforSammeMåned
import utsjekk.simulering.TestCaser.justeringPåUlikeMåneder
import utsjekk.simulering.TestCaser.nyUtbetaling
import utsjekk.simulering.oppsummering.OppsummeringGenerator
import java.time.LocalDate
import java.time.Month

class SimuleringGeneratorTest {
    private val Int.mar: LocalDate get() = LocalDate.of(2024, 3, this)
    private val Int.apr: LocalDate get() = LocalDate.of(2024, 4, this)
    private val Int.may: LocalDate get() = LocalDate.of(2024, 5, this)
    private val Int.des: LocalDate get() = LocalDate.of(2024, 12, this)

    @Disabled
    @Test
    fun `oppsummering som ikke tar høyde for feiljustering påfølgende måned - 20-03-2025`() {
        val json = """
            {"gjelderId":"13069432055","datoBeregnet":"2025-03-20","totalBelop":2335,"perioder":[{"fom":"2025-03-03","tom":"2025-03-03","utbetalinger":[{"fagområde":"TILLST","fagSystemId":"717","utbetalesTilId":"13069432055","forfall":"2025-03-20","feilkonto":false,"detaljer":[{"type":"YTEL","faktiskFom":"2025-03-03","faktiskTom":"2025-03-03","belop":1085,"sats":1085.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null},{"type":"FEIL","faktiskFom":"2025-03-03","faktiskTom":"2025-03-03","belop":1024,"sats":0.0,"satstype":null,"klassekode":"KL_KODE_JUST_ARBYT","trekkVedtakId":null,"refunderesOrgNr":null},{"type":"YTEL","faktiskFom":"2025-03-03","faktiskTom":"2025-03-03","belop":-2109,"sats":2109.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null}]}]},{"fom":"2025-04-01","tom":"2025-04-01","utbetalinger":[{"fagområde":"TILLST","fagSystemId":"717","utbetalesTilId":"13069432055","forfall":"2025-03-20","feilkonto":false,"detaljer":[{"type":"FEIL","faktiskFom":"2025-04-01","faktiskTom":"2025-04-01","belop":-1024,"sats":0.0,"satstype":null,"klassekode":"KL_KODE_JUST_ARBYT","trekkVedtakId":null,"refunderesOrgNr":null},{"type":"YTEL","faktiskFom":"2025-04-01","faktiskTom":"2025-04-01","belop":1137,"sats":1137.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null}]}]},{"fom":"2025-05-01","tom":"2025-05-01","utbetalinger":[{"fagområde":"TILLST","fagSystemId":"717","utbetalesTilId":"13069432055","forfall":"2025-03-20","feilkonto":false,"detaljer":[{"type":"YTEL","faktiskFom":"2025-05-01","faktiskTom":"2025-05-01","belop":1137,"sats":1137.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null}]}]},{"fom":"2025-06-02","tom":"2025-06-02","utbetalinger":[{"fagområde":"TILLST","fagSystemId":"717","utbetalesTilId":"13069432055","forfall":"2025-03-20","feilkonto":false,"detaljer":[{"type":"YTEL","faktiskFom":"2025-06-02","faktiskTom":"2025-06-02","belop":1085,"sats":1085.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null}]}]}]}
        """.trimIndent()
        val deserialisert =
            objectMapper.readValue(json, client.SimuleringResponse::class.java)
        val detaljer = SimuleringDetaljer.from(deserialisert, Fagsystem.TILLEGGSSTØNADER)
        val oppsummering = OppsummeringGenerator.lagOppsummering(detaljer)
        /* TODO(): Fiks denne
        *  Her forventer vi at bruker skal få en justering for april hvor ny utbetaling blir 1137 - 1024 = 113
        */
    }

    @Test
    fun `bugfiks 11-09-2024 - oppsummering av ny utbetaling`() {
        val json = """
            {"gjelderId":"x","datoBeregnet":"2024-09-11","totalBelop":15728,"perioder":[{"fom":"2024-09-02","tom":"2024-09-02","utbetalinger":[{"fagområde":"TILLST","fagSystemId":"200000363","utbetalesTilId":"x","forfall":"2024-09-11","feilkonto":false,"detaljer":[{"type":"YTEL","faktiskFom":"2024-09-02","faktiskTom":"2024-09-02","belop":1861,"sats":1861.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null},{"type":"FEIL","faktiskFom":"2024-09-02","faktiskTom":"2024-09-02","belop":1550,"sats":0.0,"satstype":null,"klassekode":"KL_KODE_JUST_ARBYT","trekkVedtakId":null,"refunderesOrgNr":null},{"type":"YTEL","faktiskFom":"2024-09-02","faktiskTom":"2024-09-02","belop":-3411,"sats":3411.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null}]}]},{"fom":"2024-10-01","tom":"2024-10-01","utbetalinger":[{"fagområde":"TILLST","fagSystemId":"200000363","utbetalesTilId":"x","forfall":"2024-09-11","feilkonto":false,"detaljer":[{"type":"FEIL","faktiskFom":"2024-10-01","faktiskTom":"2024-10-01","belop":-1550,"sats":0.0,"satstype":null,"klassekode":"KL_KODE_JUST_ARBYT","trekkVedtakId":null,"refunderesOrgNr":null},{"type":"YTEL","faktiskFom":"2024-10-01","faktiskTom":"2024-10-01","belop":2038,"sats":2038.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null}]}]},{"fom":"2024-11-01","tom":"2024-11-01","utbetalinger":[{"fagområde":"TILLST","fagSystemId":"200000363","utbetalesTilId":"x","forfall":"2024-09-11","feilkonto":false,"detaljer":[{"type":"YTEL","faktiskFom":"2024-11-01","faktiskTom":"2024-11-01","belop":1861,"sats":1861.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null}]}]},{"fom":"2024-12-02","tom":"2024-12-02","utbetalinger":[{"fagområde":"TILLST","fagSystemId":"200000363","utbetalesTilId":"x","forfall":"2024-09-11","feilkonto":false,"detaljer":[{"type":"YTEL","faktiskFom":"2024-12-02","faktiskTom":"2024-12-02","belop":1949,"sats":1949.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null}]}]},{"fom":"2025-01-01","tom":"2025-01-01","utbetalinger":[{"fagområde":"TILLST","fagSystemId":"200000363","utbetalesTilId":"x","forfall":"2024-09-11","feilkonto":false,"detaljer":[{"type":"YTEL","faktiskFom":"2025-01-01","faktiskTom":"2025-01-01","belop":2038,"sats":2038.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null}]}]},{"fom":"2025-02-03","tom":"2025-02-03","utbetalinger":[{"fagområde":"TILLST","fagSystemId":"200000363","utbetalesTilId":"x","forfall":"2024-09-11","feilkonto":false,"detaljer":[{"type":"YTEL","faktiskFom":"2025-02-03","faktiskTom":"2025-02-03","belop":1772,"sats":1772.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null}]}]},{"fom":"2025-03-03","tom":"2025-03-03","utbetalinger":[{"fagområde":"TILLST","fagSystemId":"200000363","utbetalesTilId":"x","forfall":"2024-09-11","feilkonto":false,"detaljer":[{"type":"YTEL","faktiskFom":"2025-03-03","faktiskTom":"2025-03-03","belop":1861,"sats":1861.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null}]}]},{"fom":"2025-04-01","tom":"2025-04-01","utbetalinger":[{"fagområde":"TILLST","fagSystemId":"200000363","utbetalesTilId":"x","forfall":"2024-09-11","feilkonto":false,"detaljer":[{"type":"YTEL","faktiskFom":"2025-04-01","faktiskTom":"2025-04-01","belop":1949,"sats":1949.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null}]}]},{"fom":"2025-05-01","tom":"2025-05-01","utbetalinger":[{"fagområde":"TILLST","fagSystemId":"200000363","utbetalesTilId":"x","forfall":"2024-09-11","feilkonto":false,"detaljer":[{"type":"YTEL","faktiskFom":"2025-05-01","faktiskTom":"2025-05-01","belop":1949,"sats":1949.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null}]}]},{"fom":"2025-06-02","tom":"2025-06-02","utbetalinger":[{"fagområde":"TILLST","fagSystemId":"200000363","utbetalesTilId":"x","forfall":"2024-09-11","feilkonto":false,"detaljer":[{"type":"YTEL","faktiskFom":"2025-06-02","faktiskTom":"2025-06-02","belop":1861,"sats":1861.0,"satstype":"DAG","klassekode":"TSTBASISP4-OP","trekkVedtakId":null,"refunderesOrgNr":null}]}]}]}
        """.trimIndent()
        val deserialisert =
            objectMapper.readValue(json, client.SimuleringResponse::class.java)
        val detaljer = SimuleringDetaljer.from(deserialisert, Fagsystem.TILLEGGSSTØNADER)
        val oppsummering = OppsummeringGenerator.lagOppsummering(detaljer)

        val september = oppsummering.oppsummeringer.first { it.fom.month == Month.SEPTEMBER }
        val oktober = oppsummering.oppsummeringer.first { it.fom.month == Month.OCTOBER }
        assertEquals(1861, september.nyUtbetaling)
        assertEquals(3411, september.tidligereUtbetalt)
        assertEquals(0, september.totalEtterbetaling)
        assertEquals(0, september.totalFeilutbetaling)
        assertEquals(2038, oktober.nyUtbetaling)
        assertEquals(0, oktober.tidligereUtbetalt)
        assertEquals(488, oktober.totalEtterbetaling)
        assertEquals(0, oktober.totalFeilutbetaling)
    }

    @Test
    fun `skal lage oppsummering for ny utbetaling`() {
        val fremtiden = LocalDate.now().plusMonths(1)
        val simuleringDetaljer = simuleringDetaljer(
            datoBeregnet = 28.may,
            totalBeløp = 800,
            perioder = listOf(Periode(fremtiden, fremtiden, nyUtbetaling(fremtiden, fremtiden, 800)))
        )

        val expected = simuleringResponse(
            detaljer = simuleringDetaljer,
            oppsummeringer = listOf(
                oppsummeringForPeriode(
                    fom = fremtiden,
                    tom = fremtiden,
                    tidligereUtbetalt = 0,
                    nyUtbetaling = 800,
                    totalEtterbetaling = 0,
                    totalFeilutbetaling = 0
                )
            ),
        )

        val actual = OppsummeringGenerator.lagOppsummering(simuleringDetaljer)
        assertEquals(expected, actual)
    }

    @Test
    fun `skal lage oppsummering for etterbetaling`() {
        val detaljer = simuleringDetaljer(
            datoBeregnet = 28.may,
            totalBeløp = 100,
            perioder = listOf(Periode(1.may, 1.may, etterbetaling(1.may, 1.may, 700, 800)))
        )

        val expected = simuleringResponse(
            detaljer = detaljer,
            oppsummeringer = listOf(
                oppsummeringForPeriode(
                    1.may,
                    1.may,
                    tidligereUtbetalt = 700,
                    nyUtbetaling = 800,
                    totalEtterbetaling = 100,
                    totalFeilutbetaling = 0,
                )
            )
        )

        val actual = OppsummeringGenerator.lagOppsummering(detaljer)
        assertEquals(expected, actual)
    }

    @Test
    fun `skal lage oppsummering for feilutbetaling`() {
        val detaljer = simuleringDetaljer(
            datoBeregnet = 28.may,
            totalBeløp = 0,
            perioder = listOf(Periode(1.may, 1.may, feilutbetaling(1.may, 1.may, 700, 600)))
        )
        val expected = simuleringResponse(
            detaljer = detaljer,
            oppsummeringer = listOf(
                oppsummeringForPeriode(
                    fom = 1.may,
                    tom = 1.may,
                    tidligereUtbetalt = 700,
                    nyUtbetaling = 600,
                    totalEtterbetaling = 0,
                    totalFeilutbetaling = 100,
                )
            )
        )
        val actual = OppsummeringGenerator.lagOppsummering(detaljer)
        assertEquals(expected, actual)
    }

    @Test
    fun `skal lage oppsummering for flere perioder med justering i samme måned`() {
        val detaljer = simuleringDetaljer(
            datoBeregnet = 10.des,
            totalBeløp = 100,
            perioder = justeringInnenforSammeMåned(10.des, 600, 700)
        )
        val forventetOppsummering = simuleringResponse(
            detaljer = detaljer,
            oppsummeringer = listOf(
                oppsummeringForPeriode(
                    fom = 10.des,
                    tom = 11.des,
                    tidligereUtbetalt = 600,
                    nyUtbetaling = 700,
                    totalEtterbetaling = 100,
                    totalFeilutbetaling = 0,
                ),
            )
        )

        val actual = OppsummeringGenerator.lagOppsummering(detaljer)
        assertEquals(forventetOppsummering, actual)
    }

    @Test
    fun `skal lage oppsummering justering på ulike måneder`() {
        val detaljer = simuleringDetaljer(
            datoBeregnet = 10.des,
            totalBeløp = 100,
            perioder = justeringPåUlikeMåneder(10.apr, 1000, 500, 2000)
        )
        val forventetOppsummering = simuleringResponse(
            detaljer = detaljer,
            oppsummeringer = listOf(
                oppsummeringForPeriode(
                    fom = 10.apr,
                    tom = 10.apr,
                    tidligereUtbetalt = 1000,
                    nyUtbetaling = 500,
                    totalEtterbetaling = 0,
                    totalFeilutbetaling = 0,
                ),
                oppsummeringForPeriode(
                    fom = 10.may,
                    tom = 10.may,
                    tidligereUtbetalt = 0,
                    nyUtbetaling = 2000,
                    totalEtterbetaling = 1500,
                    totalFeilutbetaling = 0,
                ),
            )
        )

        val actual = OppsummeringGenerator.lagOppsummering(detaljer)
        assertEquals(forventetOppsummering, actual)
    }

    @Test
    fun `skal lage oppsummering for flere perioder`() {
        val fremtiden = LocalDate.now().plusMonths(2)
        val detaljer = simuleringDetaljer(
            datoBeregnet = 28.may,
            totalBeløp = 900,
            perioder = listOf(
                Periode(1.mar, 1.mar, feilutbetaling(1.mar, 1.mar, 700, 600)),
                Periode(1.apr, 1.apr, etterbetaling(1.apr, 1.apr, 700, 800)),
                Periode(fremtiden, fremtiden, nyUtbetaling(fremtiden, fremtiden, 800)),
            )
        )
        val expected = simuleringResponse(
            detaljer = detaljer,
            oppsummeringer = listOf(
                oppsummeringForPeriode(
                    fom = 1.mar,
                    tom = 1.mar,
                    tidligereUtbetalt = 700,
                    nyUtbetaling = 600,
                    totalEtterbetaling = 0,
                    totalFeilutbetaling = 100,
                ),
                oppsummeringForPeriode(
                    fom = 1.apr,
                    tom = 1.apr,
                    tidligereUtbetalt = 700,
                    nyUtbetaling = 800,
                    totalEtterbetaling = 100,
                    totalFeilutbetaling = 0,
                ),
                oppsummeringForPeriode(
                    fom = fremtiden,
                    tom = fremtiden,
                    tidligereUtbetalt = 0,
                    nyUtbetaling = 800,
                    totalEtterbetaling = 0,
                    totalFeilutbetaling = 0,
                )
            )
        )
        val actual = OppsummeringGenerator.lagOppsummering(detaljer)
        assertEquals(expected, actual)
    }
}
