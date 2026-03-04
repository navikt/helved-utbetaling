package statistikkern

import com.google.cloud.bigquery.QueryJobConfiguration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import libs.kafka.KafkaConsumerFake
import libs.kafka.VanillaKafkaMock
import libs.ktor.KtorRuntime
import models.Action.CREATE
import models.BehandlingId
import models.Fagsystem
import models.Navident
import models.PeriodeId
import models.Periodetype
import models.Personident
import models.SakId
import models.StatusReply
import models.StønadTypeAAP
import models.Utbetaling
import models.UtbetalingId
import models.Utbetalingsperiode

class TestTopics(kafka: VanillaKafkaMock) {
    val utbetalinger: KafkaConsumerFake<String, Utbetaling> = kafka.getConsumer(Topics.utbetalinger)
    val status: KafkaConsumerFake<String, StatusReply> = kafka.getConsumer(Topics.status)
}

object TestRuntime {
    val kafka = VanillaKafkaMock()
    val bq = BigQueryService(
        projectId = BigQueryEmulator.projectId,
        datasetName = BigQueryEmulator.datasetName,
        bigQuery = BigQueryEmulator.bigQuery,
    )
    val config = Config(kafka = kafka.config)
    val topics = TestTopics(kafka)

    val ktor = KtorRuntime<Config>(
        appName = "statistikkern",
        module = {
            statistikkern(config, kafka, bq, kafka)
        }
    )
}


fun BigQueryService.queryUtbetalinger(uid: String) = query(uid, "utbetalinger")
fun BigQueryService.queryStatus(uid: String) = query(uid, "status")

private fun BigQueryService.query(uid: String, table: String): List<Map<String, Any?>> {
    val result = bigQuery.query(
        QueryJobConfiguration.of(
            "SELECT * FROM `$datasetName.$table` WHERE uid = '$uid'"
        )
    )
    val fields = result.schema?.fields
    return result.iterateAll().map { row ->
        fields?.mapIndexed { i, field -> field.name to row[i].value }?.toMap() as Map<String, Any?>
    }
}

fun utbetaling(dryrun: Boolean = false) = Utbetaling(
    dryrun = dryrun,
    originalKey = "original-key",
    fagsystem = Fagsystem.AAP,
    uid = UtbetalingId(UUID.randomUUID()),
    action = CREATE,
    førsteUtbetalingPåSak = true,
    sakId = SakId("SAK-123"),
    behandlingId = BehandlingId("BEH-456"),
    lastPeriodeId = PeriodeId(),
    personident = Personident("12345678901"),
    vedtakstidspunkt = LocalDateTime.of(2025, 1, 1, 12, 0),
    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
    beslutterId = Navident("Z888888"),
    saksbehandlerId = Navident("Z999999"),
    periodetype = Periodetype.UKEDAG,
    avvent = null,
    perioder = listOf(
        Utbetalingsperiode(
            fom = LocalDate.of(2025, 1, 6),
            tom = LocalDate.of(2025, 1, 7),
            beløp = 800u,
        )
    ),
)