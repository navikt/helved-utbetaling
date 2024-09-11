package utsjekk.iverksetting.utbetalingsoppdrag.bdd

import TestData.domain.andelData
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.BehandlingId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val NOR_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

class UtbetalingsgeneratorBddTest {

    data class Input(
        override val behandlingId: BehandlingId,
        val fom: LocalDate,
        val tom: LocalDate,
        val beløp: Int,
    ) : WithBehandlingId(behandlingId) {
        companion object {
            fun from(iter: Iterator<String>) = Input(
                behandlingId = BehandlingId(iter.next()),
                fom = LocalDate.parse(iter.next(), NOR_DATE),
                tom = LocalDate.parse(iter.next(), NOR_DATE),
                beløp = iter.next().toInt(),
            )
        }
    }

    data class Expected(
        override val behandlingId: BehandlingId,
        val fom: LocalDate,
        val tom: LocalDate,
        val opphørsdato: LocalDate?,
        val beløp: Int,
        val førsteUtbetSak: Boolean,
        val erEndring: Boolean,
        val periodeId: Long,
        val forrigePeriodeId: Long?,
    ) : WithBehandlingId(behandlingId) {
        companion object {
            fun from(iter: Iterator<String>) = Expected(
                behandlingId = BehandlingId(iter.next()),
                fom = LocalDate.parse(iter.next(), NOR_DATE),
                tom = LocalDate.parse(iter.next(), NOR_DATE),
                opphørsdato = iter.next().let { if (it.isBlank()) null else LocalDate.parse(it, NOR_DATE) },
                beløp = iter.next().toInt(),
                førsteUtbetSak = iter.next().toBoolean(),
                erEndring = iter.next().toBoolean(),
                periodeId = iter.next().toLong(),
                forrigePeriodeId = iter.next().let { if (it.isBlank()) null else it.toLong() }
            )
        }
    }

    @Test
    fun `en periode får en lik periode`() {
        val csv = Csv.read("/csv/oppdrag/en_periode_får_en_lik_periode.csv")
        Bdd.følgendeTilkjenteYtelser(csv, Input::from) {
            andelData(fom = it.fom, tom = it.tom, beløp = it.beløp)
        }

        Bdd.`beregner utbetalingsoppdrag`()
        Bdd.`forvent følgende utbetalingsoppdrag`(csv, Expected::from)
    }
}
