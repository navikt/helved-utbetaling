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
import no.trygdeetaten.skjema.oppdrag.ObjectFactory
import no.trygdeetaten.skjema.oppdrag.Oppdrag

class TestTopics(kafka: VanillaKafkaMock) {
    val utbetalinger: KafkaConsumerFake<String, Utbetaling> = kafka.getConsumer(Topics.utbetalinger)
    val status: KafkaConsumerFake<String, StatusReply> = kafka.getConsumer(Topics.status)
    val oppdrag: KafkaConsumerFake<String, Oppdrag> = kafka.getConsumer(Topics.oppdrag)
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

    init {
        KtorRuntime<Config>(
            appName = "statistikkern",
            module = {
                statistikkern(config, kafka, bq, kafka)
            }
        )
    }
}


fun BigQueryService.queryUtbetalinger(key: String) = query("key", key, "utbetalinger")
fun BigQueryService.queryStatus(key: String) = query("key", key, "status")
fun BigQueryService.queryOppdrag(sakId: String) = query("sak", sakId, "oppdrag")

private fun BigQueryService.query(column: String, value: String, table: String): List<Map<String, Any?>> {
    val result = bigQuery.query(
        QueryJobConfiguration.of(
            "SELECT * FROM `$datasetName.$table` WHERE $column = '$value'"
        )
    )
    val fields = result.schema?.fields
    return result.iterateAll().map { row ->
        fields?.mapIndexed { i, field -> field.name to row[i].value }?.toMap() as Map<String, Any?>
    }
}

fun utbetaling(dryrun: Boolean = false, originalKey: String = UUID.randomUUID().toString(),
perioder: List<Utbetalingsperiode> = defaultPerioder()) = Utbetaling(
    dryrun = dryrun,
    originalKey = originalKey,
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
    perioder = perioder,
)

fun defaultPerioder() = listOf(
    Utbetalingsperiode(fom = LocalDate.of(2025, 1, 6), tom = LocalDate.of(2025, 1, 7), beløp = 800u),
)

private val objectFactory = ObjectFactory()

fun oppdrag(
    sakId: String = "SAK-123",
    fagsystem: String = "AAP",
    tidspktMelding: String = "2025-01-01-10.10.00.000000",
    henvisning: String = "BEH-456",
): Oppdrag {
    val linje = objectFactory.createOppdragsLinje150().apply {
        this.henvisning = henvisning
    }
    val avstemming = objectFactory.createAvstemming115().apply {
        this.tidspktMelding = tidspktMelding
    }
    val oppdrag110 = objectFactory.createOppdrag110().apply {
        this.fagsystemId = sakId
        this.kodeFagomraade = fagsystem
        this.avstemming115 = avstemming
        this.oppdragsLinje150s.add(linje)
    }
    return objectFactory.createOppdrag().apply {
        this.oppdrag110 = oppdrag110
    }
}