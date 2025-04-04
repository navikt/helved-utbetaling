package utsjekk.task

// class UtbetalingTaskStrategy(
//     private val oppdragClient: Oppdrag,
//     // private val statusProducer: Kafka<UtbetalingStatus>,
// ) : TaskStrategy {
//     override suspend fun isApplicable(task: TaskDao): Boolean {
//         return task.kind == libs.task.Kind.Utbetaling
//     }
//
//     override suspend fun execute(task: TaskDao) {
//         val oppdrag = objectMapper.readValue<UtbetalingsoppdragDto>(task.payload)
//
//         iverksett(oppdrag)
//
//         Tasks.update(task.id, libs.task.Status.COMPLETE, "", TaskDao::exponentialSec)
//     }
//
//     private suspend fun iverksett(oppdrag: UtbetalingsoppdragDto) {
//         oppdragClient.utbetal(oppdrag)
//
//         transaction {
//             val dao = UtbetalingDao.findOrNull(oppdrag.uid) ?: error("utbetaling {uid} mangler. Kan løses ved å manuelt legge inn en rad i utbetaling")
//
//             dao.copy(status = Status.SENDT_TIL_OPPDRAG).update(oppdrag.uid)
//
//             Tasks.create(
//                 kind = libs.task.Kind.StatusUtbetaling,
//                 payload = oppdrag.uid
//             ) { uid ->
//                 objectMapper.writeValueAsString(uid)
//             }
//
//             // statusProducer.produce(oppdrag.uid.id.toString(), status.data)
//         }
//     }
//
//     companion object {
//         fun metadataStrategy(payload: String): Map<String, String> {
//             val utbetaling = objectMapper.readValue<UtbetalingsoppdragDto>(payload)
//             return mapOf(
//                 "sakId" to utbetaling.saksnummer,
//                 "behandlingId" to utbetaling.utbetalingsperioder.maxBy { it.fom }.behandlingId,
//                 "iverksettingId" to null.toString(),
//                 "fagsystem" to utbetaling.fagsystem.name,
//             )
//         }
//     }
// }
//
