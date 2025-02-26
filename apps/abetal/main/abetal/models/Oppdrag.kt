package abetal.models

enum class Kvitteringstatus(val kode: String) {
    OK("00"),
    MED_INFORMASJON("04"),
    FUNKSJONELL_FEIL("08"),
    TEKNISK_FEIL("12"),
    UKJENT("Ukjent");
}

