package models

enum class Fagsystem(val fagområde: String) {
    DAGPENGER("DP"),
    TILTAKSPENGER("TILTPENG"),
    TILLEGGSSTØNADER("TILLST"),
    TILLSTPB("TILLSTPB"), // TILLEGGSSTØNADER
    TILLSTLM("TILLSTLM"), // TILLEGGSSTØNADER
    TILLSTBO("TILLSTBO"), // TILLEGGSSTØNADER
    TILLSTDR("TILLSTDR"), // TILLEGGSSTØNADER
    TILLSTRS("TILLSTRS"), // TILLEGGSSTØNADER
    TILLSTRO("TILLSTRO"), // TILLEGGSSTØNADER
    TILLSTRA("TILLSTRA"), // TILLEGGSSTØNADER
    TILLSTFL("TILLSTFL"), // TILLEGGSSTØNADER
    AAP("AAP"),
    HISTORISK("HELSREF"),
;

    companion object {

        fun fromFagområde(fagområde: String) =
            entries.single { it.fagområde == fagområde }

        // fun from(stønad: Stønadstype) = when (stønad) {
        //     is StønadTypeDagpenger -> DAGPENGER
        //     is StønadTypeTiltakspenger -> TILTAKSPENGER
        //     is StønadTypeTilleggsstønader -> TILLEGGSSTØNADER
        //     is StønadTypeAAP -> AAP
        //     is StønadTypeHistorisk -> HISTORISK
        // }

        fun from(kode: String) = when (kode) {
            "DP" -> Fagsystem.DAGPENGER
            "TILTPENG" -> Fagsystem.TILTAKSPENGER
            "TILLST"   -> Fagsystem.TILLEGGSSTØNADER
            "TILLSTPB" -> Fagsystem.TILLSTPB
            "TILLSTLM" -> Fagsystem.TILLSTLM
            "TILLSTBO" -> Fagsystem.TILLSTBO
            "TILLSTDR" -> Fagsystem.TILLSTDR
            "TILLSTRS" -> Fagsystem.TILLSTRS
            "TILLSTRO" -> Fagsystem.TILLSTRO
            "TILLSTRA" -> Fagsystem.TILLSTRA
            "TILLSTFL" -> Fagsystem.TILLSTFL
            "AAP" -> Fagsystem.AAP
            "HELSREF" -> Fagsystem.HISTORISK
            else -> error("fagområde $kode not implemented")
        }
    }
}

