package abetal

import abetal.models.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import org.junit.jupiter.api.Test

val Int.jan: LocalDate
  get() = LocalDate.of(2025, 1, this)

internal class AapTest {

  @Test
  fun `add to utbetalinger`() {
    val uid = UtbetalingId(UUID.randomUUID())
    TestTopics.aap.produce("$uid") {
      Utbetaling(
          sakId = SakId("1"),
          behandlingId = BehandlingId(""),
          lastPeriodeId = PeriodeId(),
          personident = Personident(""),
          vedtakstidspunkt = LocalDateTime.now(),
          stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
          beslutterId = Navident(""),
          saksbehandlerId = Navident(""),
          satstype = Satstype.DAG,
          perioder = listOf(
              Utbetalingsperiode(
                  fom = 1.jan,
                  tom = 1.jan,
                  beløp = 123u,
                  fastsattDagsats = 123u,
              ),
              Utbetalingsperiode(
                  fom = 2.jan,
                  tom = 2.jan,
                  beløp = 123u,
                  fastsattDagsats = 123u,
              )
          ),
      )
    }

    TestTopics.status.assertThat().hasKey("$uid").hasValue(StatusReply(Status.MOTTATT))
    TestTopics.utbetalinger.assertThat().hasKey("$uid")
  }
}
