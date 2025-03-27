package models

import java.time.LocalDateTime

data class Avstemming(
    val fagsystem: Fagsystem,
    val fom: LocalDateTime,
    val tom: LocalDateTime,
    val oppdragsdata: List<Oppdragsdata>
)

data class Oppdragsdata(
    val status: StatusReply,
    val personident: Personident,
    val sakId: SakId,
    val avstemmingtidspunkt: LocalDateTime,
    val totalBeløpAllePerioder: UInt,
    val kvittering: Kvittering,
)

data class Kvittering(
    val kode: String,
    val alvorlighetsgrad: String, 
    val melding: String,
)
