package abetal.consumers

import abetal.*
import java.time.LocalDate
import kotlin.test.assertEquals
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.*
import org.junit.jupiter.api.Test

internal class DpTest {

    @Test
    fun `1 meldekort blir til 1 utbetaling`() {
        val uid = randomUtbetalingId()
        val sid = "$nextInt"
        val bid = "$nextInt"

        TestTopics.dp.produce("${uid.id}") {
            Dp.utbetaling(sid) {
                Dp.meldekort(
                    meldeperiode = "132460781",
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u, 
                    utbetaling = 553u,
                )
            }
        }
        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasValue(StatusReply(Status.MOTTATT))

        // TODO: uid skal være meldeperiode
        TestTopics.utbetalinger.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .hasLastValue("${uid.id}") {
                utbetaling(Action.CREATE, uid, SakId(sid), BehandlingId(bid)) {
                    listOf(
                        periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 7), 553u),
                        periode(LocalDate.of(2021, 6, 8), LocalDate.of(2021, 6, 8), 553u),
                        periode(LocalDate.of(2021, 6, 9), LocalDate.of(2021, 6, 9), 553u),
                        periode(LocalDate.of(2021, 6, 10), LocalDate.of(2021, 6, 10), 553u),
                        periode(LocalDate.of(2021, 6, 11), LocalDate.of(2021, 6, 11), 553u),
                        periode(LocalDate.of(2021, 6, 14), LocalDate.of(2021, 6, 14), 553u),
                        periode(LocalDate.of(2021, 6, 15), LocalDate.of(2021, 6, 15), 553u),
                        periode(LocalDate.of(2021, 6, 16), LocalDate.of(2021, 6, 16), 553u),
                        periode(LocalDate.of(2021, 6, 17), LocalDate.of(2021, 6, 17), 553u),
                        periode(LocalDate.of(2021, 6, 18), LocalDate.of(2021, 6, 18), 553u),
                    )
                }
            }
        TestTopics.oppdrag.assertThat()
            .hasNumberOfRecordsForKey("${uid.id}", 1)
            .withLastValue {
                assertEquals("NY", it!!.oppdrag110.kodeEndring)
            }

    }
}
