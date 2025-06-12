package models

import java.time.LocalDate
import java.time.LocalDateTime

typealias AvstemmingId = String

data class Avstemming(
    val id: AvstemmingId,
    val fom: LocalDateTime,
    val tom: LocalDateTime,
    val oppdragsdata: List<Oppdragsdata>
) {
    val fagsystem get() = oppdragsdata.first().fagsystem
}

data class Oppdragsdata(
    val fagsystem: Fagsystem,
    val personident: Personident,
    val sakId: SakId,
    val lastDelytelseId: String,
    val innsendt: LocalDateTime,
    val totalBeløpAllePerioder: UInt,
    val kvittering: Kvittering?,
)

data class Kvittering(
    val alvorlighetsgrad: String, 
    val kode: String?,
    val melding: String?,
)
