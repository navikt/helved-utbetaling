package oppdrag.iverksetting.tilstand

import libs.xml.XMLMapper
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.time.LocalDateTime
import java.util.*

data class OppdragLager(
    val uuid: UUID = UUID.randomUUID(),
    val fagsystem: String,
    val fagsak_id: String,
    val behandling_id: String,
    val iverksetting_id: String?,
    val utbetalingsoppdrag: Utbetalingsoppdrag,
    val utgaaende_oppdrag: String,
    var status: OppdragStatus = OppdragStatus.LAGT_PÅ_KØ,
    val avstemming_tidspunkt: LocalDateTime,
    val opprettet_tidspunkt: LocalDateTime = LocalDateTime.now(),
    val kvitteringsmelding: Mmel?,
    val versjon: Int = 0,
) {
    companion object {
        private val mapper: XMLMapper<Oppdrag> = XMLMapper()

        fun lagFraOppdrag(
            utbetalingsoppdrag: Utbetalingsoppdrag,
            oppdrag: Oppdrag,
            versjon: Int = 0,
        ) = OppdragLager(
            fagsystem = utbetalingsoppdrag.fagsystem.kode,
            fagsak_id = utbetalingsoppdrag.saksnummer,
            behandling_id = utbetalingsoppdrag.utbetalingsperiode.first().behandlingId,
            iverksetting_id = utbetalingsoppdrag.iverksettingId,
            avstemming_tidspunkt = utbetalingsoppdrag.avstemmingstidspunkt,
            utbetalingsoppdrag = utbetalingsoppdrag,
            utgaaende_oppdrag = mapper.writeValueAsString(oppdrag),
            kvitteringsmelding = null,
            versjon = versjon,
        )
    }
}