package oppdrag.iverksetting.domene

enum class Utbetalingsfrekvens(val kode: String) {
    DAGLIG("DAG"),
    UKENTLIG("UKE"),
    MÅNEDLIG("MND"),
    DAGLIG_14("14DG"),
    ENGANGSUTBETALING("ENG"),
}
