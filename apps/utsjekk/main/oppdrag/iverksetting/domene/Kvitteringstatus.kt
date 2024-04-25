package oppdrag.iverksetting.domene

enum class Kvitteringstatus(val kode: String) {
    OK("00"),
    AKSEPTERT_MEN_NOE_ER_FEIL("04"),
    AVVIST_FUNKSJONELLE_FEIL("08"),
    AVVIST_TEKNISK_FEIL("12"),
    UKJENT("Ukjent");
}
