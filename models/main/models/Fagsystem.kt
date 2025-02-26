package models

enum class Fagsystem(val fagområde: String) {
    DAGPENGER("DP"),
    TILTAKSPENGER("TILTPENG"),
    TILLEGGSSTØNADER("TILLST"),
    AAP("AAP");

    companion object {
        fun from(stønad: Stønadstype) = when (stønad) {
            is StønadTypeDagpenger -> DAGPENGER
            is StønadTypeTiltakspenger -> TILTAKSPENGER
            is StønadTypeTilleggsstønader -> TILLEGGSSTØNADER
            is StønadTypeAAP -> AAP
        }
    }
}

