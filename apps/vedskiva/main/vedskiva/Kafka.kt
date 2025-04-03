package vedskiva

import kotlinx.coroutines.runBlocking
import libs.kafka.*
import models.Avstemming
import models.Oppdragsdata
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object Topics {
    val avstemming = Topic("helved.avstemming.v1", bytes())
}

fun Topology.avstemming() {

}


private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")

class AvstemmingScheduler(
    table: GlobalTable<String, Oppdragsdata>,
    private val mq: AvstemmingMQProducer,
    private val peisschtappern: PeisschtappernClient,
    private val config: AvstemmingConfig,
): GlobalStateScheduleProcessor<String, Oppdragsdata>(
    "${table.store.name}-scheduler",
    store = table.store,
    interval = 1.toDuration(DurationUnit.MINUTES)
) {
    override fun schedule(wallClockTime: Long, store: StateStore<String, Oppdragsdata>) {
        val now = LocalDateTime.now()
        if (now.hour != config.scheduleHour && now.minute != config.scheduleMinute) return
        val today = now.toLocalDate()
        val lastAvstemmingDate = runBlocking { peisschtappern.lastAvstemming() }

        store.filter(limit = Int.MAX_VALUE) {
            val avstemmingsdag = it.value.avstemmingsdag
            (avstemmingsdag == today || avstemmingsdag.isBefore(today))
        }.groupBy { (_, value) ->
            value.fagsystem
        }.forEach { (fagsystem, oppdragsdata) ->
            val avstemming = Avstemming(
                fom = lastAvstemmingDate ?: today.minusDays(2), // riktig?
                tom = today.minusDays(1), // riktig?
                oppdragsdata = oppdragsdata.map { (_, value) -> value },
            )
            val messages = AvstemmingService.create(avstemming)
            val avstemmingId = messages.first().aksjon.avleverendeAvstemmingId
            messages.forEach { message -> mq.send(message) }
            urskog.appLog.info("Fullført grensesnittavstemming for ${fagsystem.name} id: $avstemmingId")
            oppdragsdata.forEach { (key, _) -> store.delete(key) }
//            oppdragsdata.forEach { (key, _) -> TODO ("tombstone avstemming for key hvis lastAvstemmingDate skal hentes fra peisschtappern") // avstemmingProducer.tombstone(key) }
        }
    }
}