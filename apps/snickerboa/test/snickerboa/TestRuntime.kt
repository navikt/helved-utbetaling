package snickerboa

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import libs.kafka.KafkaConsumerFake
import libs.kafka.KafkaProducerFake
import libs.kafka.VanillaKafkaMock
import libs.ktor.KtorRuntime
import models.*

class TestTopics(kafka: VanillaKafkaMock) {
    val aapIntern: KafkaProducerFake<String, AapUtbetaling> = kafka.getProducer(Topics.aapIntern)
    val dpIntern: KafkaProducerFake<String, DpUtbetaling> = kafka.getProducer(Topics.dpIntern)
    val tsIntern: KafkaProducerFake<String, TsDto> = kafka.getProducer(Topics.tsIntern)
    val tpIntern: KafkaProducerFake<String, TpUtbetaling> = kafka.getProducer(Topics.tpIntern)
    val historiskIntern: KafkaProducerFake<String, HistoriskUtbetaling> = kafka.getProducer(Topics.historiskIntern)

    val status: KafkaConsumerFake<String, StatusReply> = kafka.getConsumer(Topics.status)
    val dryrunAap: KafkaConsumerFake<String, Simulering> = kafka.getConsumer(Topics.dryrunAap)
    val dryrunDp: KafkaConsumerFake<String, Simulering> = kafka.getConsumer(Topics.dryrunDp)
    val dryrunTs: KafkaConsumerFake<String, Simulering> = kafka.getConsumer(Topics.dryrunTs)
    val dryrunTp: KafkaConsumerFake<String, Simulering> = kafka.getConsumer(Topics.dryrunTp)
}

object TestRuntime {
    val kafka: VanillaKafkaMock  = VanillaKafkaMock()
    val config = Config(kafka = kafka.config)
    val topics = TestTopics(kafka)
    val ktor = KtorRuntime<Config>(
        appName = "snickerboa",
        module = {
            snickerboa(config, kafka, factory = kafka)
        }
    )
}

fun aapUtbetaling(dryrun: Boolean = false) = AapUtbetaling(
    dryrun = dryrun,
    sakId = "SAK-123",
    behandlingId = "BEH-456",
    ident = "12345678901",
    utbetalinger = listOf(
        AapUtbetalingsdag(
            meldeperiode = "2025-01",
            dato = LocalDate.of(2025, 1, 6),
            sats = 1000u,
            utbetaltBeløp = 800u,
        )
    ),
    vedtakstidspunktet = LocalDateTime.of(2025, 1, 1, 12, 0),
    saksbehandler = "Z999999",
    beslutter = "Z888888",
)

fun dpUtbetaling(dryrun: Boolean = false) = DpUtbetaling(
    dryrun = dryrun,
    sakId = "SAK-DP-123",
    behandlingId = "BEH-DP-456",
    ident = "12345678901",
    utbetalinger = listOf(
        DpUtbetalingsdag(
            meldeperiode = "2025-01",
            dato = LocalDate.of(2025, 1, 6),
            sats = 800u,
            utbetaltBeløp = 800u,
            utbetalingstype = Utbetalingstype.Dagpenger,
        )
    ),
    vedtakstidspunktet = LocalDateTime.of(2025, 1, 1, 12, 0),
    saksbehandler = "Z999999",
    beslutter = "Z888888",
)

fun tsDto(dryrun: Boolean = false) = TsDto(
    dryrun = dryrun,
    sakId = "SAK-TS-123",
    behandlingId = "BEH-TS-456",
    personident = "12345678901",
    vedtakstidspunkt = LocalDateTime.of(2025, 1, 1, 12, 0),
    periodetype = Periodetype.EN_GANG,
    utbetalinger = listOf(
        TsUtbetaling(
            id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            stønad = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
            perioder = listOf(
                TsPeriode(
                    fom = LocalDate.of(2025, 1, 6),
                    tom = LocalDate.of(2025, 1, 6),
                    beløp = 500u,
                )
            ),
        )
    ),
)

fun tpUtbetaling(dryrun: Boolean = false) = TpUtbetaling(
    dryrun = dryrun,
    sakId = "SAK-TP-123",
    behandlingId = "BEH-TP-456",
    personident = "12345678901",
    vedtakstidspunkt = LocalDateTime.of(2025, 1, 1, 12, 0),
    perioder = listOf(
        TpPeriode(
            meldeperiode = "2025-01",
            fom = LocalDate.of(2025, 1, 6),
            tom = LocalDate.of(2025, 1, 12),
            beløp = 700u,
            stønad = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
        )
    ),
)

fun historiskUtbetaling(dryrun: Boolean = false) = HistoriskUtbetaling(
    dryrun = dryrun,
    id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
    sakId = "SAK-HIST-123",
    behandlingId = "BEH-HIST-456",
    personident = "12345678901",
    stønad = StønadTypeHistorisk.REISEUTGIFTER,
    vedtakstidspunkt = LocalDateTime.of(2025, 1, 1, 12, 0),
    periodetype = Periodetype.EN_GANG,
    perioder = listOf(
        HistoriskPeriode(
            fom = LocalDate.of(2025, 1, 6),
            tom = LocalDate.of(2025, 1, 6),
            beløp = 1000u,
        )
    ),
)
