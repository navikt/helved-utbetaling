package urskog

import libs.jdbc.await
import models.Utbetaling
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import javax.sql.DataSource

object TestHelpers {
    fun produceUtbetaling(
        key: String,
        utbetaling: Utbetaling,
    ) {
        TestRuntime.topics.utbetalinger.produce(key) { utbetaling }
    }

    fun DataSource.awaitOppdragSakerAck(
        sakId: String,
        timeoutMs: Long = 3_000,
    ): DaoOppdrag? = await(timeoutMs) {
        DaoOppdrag.query(
            """
                SELECT * FROM ${DaoOppdrag.table}
                WHERE sak_id = ?
                  AND saker_ack = true
            """.trimIndent(),
        ) { stmt ->
            stmt.setString(1, sakId)
        }.firstOrNull()
    }

    fun assertRetryKvitteringReceived(
        key: String,
        assertions: (Oppdrag) -> Unit = {},
    ): Oppdrag {
        return TestRuntime.topics.retryKvittering.assertThat()
            .has(key)
            .with(key) { assertions(it) }
            .get(key)
    }
}
