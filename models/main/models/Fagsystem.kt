package models

enum class Fagsystem(val fagområde: String) {
    DAGPENGER("DP"),
    TILTAKSPENGER("TILTPENG"),
    TILLEGGSSTØNADER("TILLST"),
    AAP("AAP"),
    HISTORISK("HELSREF");

    companion object {

        fun fromFagområde(fagområde: String) =
            Fagsystem.values().single { it.fagområde == fagområde }

        fun from(stønad: Stønadstype) = when (stønad) {
            is StønadTypeDagpenger -> DAGPENGER
            is StønadTypeTiltakspenger -> TILTAKSPENGER
            is StønadTypeTilleggsstønader -> TILLEGGSSTØNADER
            is StønadTypeAAP -> AAP
            is StønadTypeHistorisk -> HISTORISK
        }

        fun from(kode: String) = when (kode) {
            "DP" -> Fagsystem.DAGPENGER
            "TILTPENG" -> Fagsystem.TILTAKSPENGER
            "TILLST" -> Fagsystem.TILLEGGSSTØNADER
            "AAP" -> Fagsystem.AAP
            "HELSREF" -> Fagsystem.HISTORISK
            else -> error("fagområde $kode not implemented")
        }
    }
}

