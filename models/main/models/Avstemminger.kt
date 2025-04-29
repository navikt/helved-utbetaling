package models

import java.time.LocalDate

data class Avstemming(
    val fom: LocalDate,
    val tom: LocalDate,
    val oppdragsdata: List<Oppdragsdata>
) {
    val fagsystem get() = oppdragsdata.first().fagsystem
}

data class Oppdragsdata(
    val fagsystem: Fagsystem,
    val personident: Personident,
    val sakId: SakId,
    val lastDelytelseId: String,
    val avstemmingsdag: LocalDate,
    val totalBeløpAllePerioder: UInt,
    val kvittering: Kvittering?,
)

data class Kvittering(
    val alvorlighetsgrad: String, 
    val kode: String?,
    val melding: String?,
)
