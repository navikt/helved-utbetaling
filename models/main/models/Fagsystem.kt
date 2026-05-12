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
    VALP("TILLSOPP")
;

    companion object {

        fun fromFagområde(fagområde: String) =
            entries.single { it.fagområde == fagområde }

        fun from(kode: String) = when (kode) {
            "DP" -> DAGPENGER
            "TILTPENG" -> TILTAKSPENGER
            "TILLST"   -> TILLEGGSSTØNADER
            "TILLSTPB" -> TILLSTPB
            "TILLSTLM" -> TILLSTLM
            "TILLSTBO" -> TILLSTBO
            "TILLSTDR" -> TILLSTDR
            "TILLSTRS" -> TILLSTRS
            "TILLSTRO" -> TILLSTRO
            "TILLSTRA" -> TILLSTRA
            "TILLSTFL" -> TILLSTFL
            "AAP" -> AAP
            "HELSREF" -> HISTORISK
            "TILLSOPP" -> VALP
            else -> error("fagområde $kode not implemented")
        }
    }

    fun isTilleggsstønader(): Boolean {
        return this in listOf(
            TILLEGGSSTØNADER,
            TILLSTPB,
            TILLSTLM,
            TILLSTBO,
            TILLSTDR,
            TILLSTRS,
            TILLSTRO,
            TILLSTRA,
            TILLSTFL,
        )
    }

    fun toName(): String =
        if (isTilleggsstønader()) TILLEGGSSTØNADER.name else this.name
}

