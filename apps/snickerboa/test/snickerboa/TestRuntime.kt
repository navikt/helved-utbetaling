package snickerboa

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import libs.kafka.KafkaConsumerFake
import libs.kafka.KafkaProducerFake
import libs.kafka.VanillaKafkaMock
import models.*

class TestTopics(kafka: VanillaKafkaMock) {
    val aapIntern: KafkaProducerFake<String, ByteArray> = kafka.getProducer(Topics.aapIntern)
    val status: KafkaConsumerFake<String, StatusReply> = kafka.getConsumer(Topics.status)
    val dryrunAap: KafkaConsumerFake<String, Simulering> = kafka.getConsumer(Topics.dryrunAap)
}

object TestRuntime {
    val kafka: VanillaKafkaMock  = VanillaKafkaMock()
    val config = Config(kafka = kafka.config)
    val topics = TestTopics(kafka)
    val app = snickerboa(config, kafka)
}

fun aapUtbetaling(dryrun: Boolean = false) = AapUtbetaling(
    dryrun = dryrun,
    sakId = "SAK-123",
    behandlingId = "BEH-456",
    ident = "12345678901",
    utbetalinger = listOf(
        AapUtbetalingsdag(
            id = UUID.randomUUID(),
            fom = LocalDate.of(2025, 1, 6),
            tom = LocalDate.of(2025, 1, 6),
            sats = 1000u,
            utbetaltBeløp = 800u,
        )
    ),
    vedtakstidspunktet = LocalDateTime.of(2025, 1, 1, 12, 0),
    saksbehandler = "Z999999",
    beslutter = "Z888888",
)