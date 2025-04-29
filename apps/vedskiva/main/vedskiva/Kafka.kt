package vedskiva

import libs.kafka.*
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata

object Topics {
    val avstemming = Topic("helved.avstemming.v1", xml<Avstemmingsdata>())
}

open class Kafka : KafkaFactory

