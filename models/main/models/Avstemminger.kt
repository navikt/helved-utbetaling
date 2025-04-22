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
    val lastDelytelseId: String?, // før 14.apr 2025 fantes ikke denne
    val avstemmingsdag: LocalDate,
    val totalBeløpAllePerioder: UInt,
    val kvittering: Kvittering?,
)

data class Kvittering(
    val kode: String?,
    val alvorlighetsgrad: String, 
    val melding: String?,
)
