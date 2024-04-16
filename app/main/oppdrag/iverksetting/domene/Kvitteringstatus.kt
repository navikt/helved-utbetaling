package oppdrag.iverksetting.domene

import no.trygdeetaten.skjema.oppdrag.Oppdrag

internal enum class Kvitteringstatus(val kode: String) {
    OK("00"),
    AKSEPTERT_MEN_NOE_ER_FEIL("04"),
    AVVIST_FUNKSJONELLE_FEIL("08"),
    AVVIST_TEKNISK_FEIL("12"),
    UKJENT("Ukjent"),
    ;

    companion object {
        fun fraKode(kode: String) =
            requireNotNull(
                entries.find {
                    it.kode == kode
                },
            ) {
                throw IllegalArgumentException("No enum constant with kode=$kode")
            }
    }
}

internal val Oppdrag.kvitteringstatus: Kvitteringstatus
    get() = Kvitteringstatus.fraKode(this.mmel?.alvorlighetsgrad ?: "Ukjent")
