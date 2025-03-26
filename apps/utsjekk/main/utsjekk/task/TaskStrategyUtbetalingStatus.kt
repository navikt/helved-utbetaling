package utsjekk.task

// class UtbetalingStatusTaskStrategy(
//     private val oppdragClient: Oppdrag,
//     // private val statusProducer: Kafka<UtbetalingStatus>,
// ) : TaskStrategy {
//
//     override suspend fun isApplicable(task: TaskDao): Boolean {
//         return task.kind == libs.task.Kind.StatusUtbetaling
//     }
//
//     override suspend fun execute(task: TaskDao) {
//         val uId = objectMapper.readValue<UtbetalingId>(task.payload)
//
//         val uDao = transaction {
//             UtbetalingDao.findOrNull(uId, history = true)
//         }
//
//         if (uDao == null) {
//             appLog.error("Fant ikke utbetaling $uId. Stopper status task ${task.id}")
//             Tasks.update(task.id, libs.task.Status.FAIL, "fant ikke utbetaling med id $uId", TaskDao::exponentialMin)
//             return
//         }
//
//         val statusDto = oppdragClient.utbetalStatus(uId)
//         when (statusDto.status) {
//             OppdragStatus.KVITTERT_OK -> {
//                 Tasks.update(task.id, libs.task.Status.COMPLETE, "", TaskDao::exponentialMin)
//                 transaction { uDao.copy(status = Status.OK).update(uId) }
//                 // statusProducer.produce(uId.id.toString(), utbetalingStatus)
//             }
//
//             OppdragStatus.KVITTERT_MED_MANGLER, OppdragStatus.KVITTERT_TEKNISK_FEIL, OppdragStatus.KVITTERT_FUNKSJONELL_FEIL -> {
//                 appLog.error("Mottok feilkvittering ${statusDto.status} fra OS for utbetaling $uId")
//                 secureLog.error("Mottok feilkvittering ${statusDto.status} fra OS for utbetaling $uId. Feilmelding: ${statusDto.feilmelding}")
//                 Tasks.update(task.id, libs.task.Status.MANUAL, statusDto.feilmelding, TaskDao::exponentialMin)
//                 transaction { uDao.copy(status = Status.FEILET_MOT_OPPDRAG).update(uId) }
//                 // statusProducer.produce(uId.id.toString(), utbetalingStatus)
//             }
//
//             OppdragStatus.KVITTERT_UKJENT -> {
//                 appLog.error("Mottok ukjent kvittering fra OS for utbetaling $uId")
//                 Tasks.update(task.id, libs.task.Status.MANUAL, statusDto.feilmelding, TaskDao::exponentialMin)
//                 transaction { uDao.copy(status = Status.FEILET_MOT_OPPDRAG).update(uId) }
//             }
//
//             OppdragStatus.LAGT_PÅ_KØ -> {
//                 Tasks.update(task.id, libs.task.Status.IN_PROGRESS, null, TaskDao::exponentialMin)
//                 transaction { uDao.copy(status = Status.SENDT_TIL_OPPDRAG).update(uId) }
//             }
//
//             OppdragStatus.OK_UTEN_UTBETALING -> {
//                 error("Status ${statusDto.status} skal aldri mottas fra utsjekk-oppdrag")
//             }
//         }
//     }
//
//     companion object {
//         fun metadataStrategy(payload: String): Map<String, String> {
//             val uid = objectMapper.readValue<UtbetalingId>(payload)
//             return mapOf(
//                 "utbetalingId" to uid.id.toString(),
//             )
//         }
//     }
// }
//
