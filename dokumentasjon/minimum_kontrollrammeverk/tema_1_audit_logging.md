# Oppsett av logger

_Logging skal være konfigurert i henhold til krav i økonomiregelverket for å sikre sporbarhet._

## Team hel ved:
Alle våre databaser har påskrudd audit logging. Se krav [K125.2 i etterlevelsesløsningen](https://etterlevelse.ansatt.nav.no/dokumentasjon/85240a1e-2b08-4298-b7a3-bbb520472dac/OKONOMI/krav/125/2) for ytterligere dokumentasjon.


# Gjennomgang av logger

_Alle hendelser som registreres i de etablerte loggene skal gjennomgås for å sikre gyldighet._

## Team hel ved:
Vi har definert en [rutine for å sjekke audit logs](https://github.com/navikt/helved-utbetaling/blob/main/dokumentasjon/Rutiner/Rutine%20for%20kontroll%20av%20audit%20logs.md) minst en gang per tertial. 

Vi bruker verktøyet Gaal (gjennomgang av audit logs) for å kontrollere audit logs for databasene. Som beskrevet i [K125.2 i etterlevelsesløsningen](https://etterlevelse.ansatt.nav.no/dokumentasjon/85240a1e-2b08-4298-b7a3-bbb520472dac/OKONOMI/krav/125/2) har vi laget en egen løsning for audit logging på Kafka i tillegg. 
