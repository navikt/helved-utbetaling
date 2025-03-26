package utsjekk.task

// class StatusTaskStrategy(
//     private val oppdragClient: Oppdrag,
// ) : TaskStrategy {
//     override suspend fun isApplicable(task: TaskDao): Boolean = task.kind == libs.task.Kind.SjekkStatus
//
//     override suspend fun execute(task: TaskDao) {
//         val oppdragIdDto = objectMapper.readValue<OppdragIdDto>(task.payload)
//         val status = oppdragClient.hentStatus(oppdragIdDto)
//
//         val iverksetting = transaction {
//             IverksettingDao.select {
//                 this.fagsystem = oppdragIdDto.fagsystem
//                 this.sakId = SakId(oppdragIdDto.sakId)
//                 this.behandlingId = BehandlingId(oppdragIdDto.behandlingId)
//                 this.iverksettingId = oppdragIdDto.iverksettingId?.let { IverksettingId(it) }
//             }.firstOrNull()
//         }
//
//         requireNotNull(iverksetting) { "Fant ikke iverksetting for oppdragId $oppdragIdDto i Status-task" }
//
//         when (status.status) {
//             OppdragStatus.KVITTERT_OK -> {
//                 IverksettingResultater.oppdater(oppdragIdDto.tilUtbetalingId(), OppdragResultat(status.status))
//                 Tasks.update(task.id, libs.task.Status.COMPLETE, "", TaskDao::exponentialSec)
//             }
//
//             OppdragStatus.KVITTERT_MED_MANGLER, OppdragStatus.KVITTERT_TEKNISK_FEIL, OppdragStatus.KVITTERT_FUNKSJONELL_FEIL -> {
//                 IverksettingResultater.oppdater(oppdragIdDto.tilUtbetalingId(), OppdragResultat(status.status))
//                 appLog.error("Mottok feilkvittering ${status.status} fra OS for oppdrag $oppdragIdDto")
//                 secureLog.error("Mottok feilkvittering ${status.status} fra OS for oppdrag $oppdragIdDto. Feilmelding: ${status.feilmelding}")
//                 Tasks.update(task.id, libs.task.Status.MANUAL, status.feilmelding, TaskDao::exponentialSec)
//             }
//
//             OppdragStatus.KVITTERT_UKJENT -> {
//                 IverksettingResultater.oppdater(oppdragIdDto.tilUtbetalingId(), OppdragResultat(status.status))
//                 appLog.error("Mottok ukjent kvittering fra OS for oppdrag $oppdragIdDto")
//                 Tasks.update(task.id, libs.task.Status.MANUAL, "Ukjent kvittering fra OS", TaskDao::exponentialSec)
//             }
//
//             OppdragStatus.LAGT_PÅ_KØ -> {
//                 Tasks.update(task.id, task.status, null, TaskDao::exponentialMinAccountForWeekendsAndPublicHolidays)
//             }
//
//             OppdragStatus.OK_UTEN_UTBETALING -> {
//                 error("Status ${status.status} skal aldri mottas fra utsjekk-oppdrag.")
//             }
//         }
//     }
//
//     companion object {
//         fun metadataStrategy(payload: String): Map<String, String> {
//             val oppdragIdDto = objectMapper.readValue<OppdragIdDto>(payload)
//             return mapOf(
//                 "sakId" to oppdragIdDto.sakId,
//                 "behandlingId" to oppdragIdDto.behandlingId,
//                 "iverksettingId" to oppdragIdDto.iverksettingId.toString(),
//                 "fagsystem" to oppdragIdDto.fagsystem.name,
//             )
//         }
//     }
// }
//
// fun OppdragIdDto.tilUtbetalingId() =
//     UtbetalingId(
//         fagsystem = this.fagsystem,
//         sakId = SakId(this.sakId),
//         behandlingId = BehandlingId(this.behandlingId),
//         iverksettingId = this.iverksettingId?.let { IverksettingId(it) },
//     )
