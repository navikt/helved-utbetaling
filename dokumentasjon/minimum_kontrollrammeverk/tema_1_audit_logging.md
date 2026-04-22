# Tema 1: Audit logging

- [Powerpoint](https://navno.sharepoint.com/:p:/r/sites/Helhetligkvalitetssystem/Delte%20dokumenter/Minimum%20kontrollrammeverk%20%C3%B8konomisystem%20(MKR-%C3%98S)/Presentasjoner%20fra%20oppskytningsrampe/Tema%201%20oppstartsm%C3%B8te.pptx?d=wae93b786345d46ceada33c43700868ce&csf=1&web=1&e=1iXlF2) med kravene for _Audit logging_
- [Excel](https://navno.sharepoint.com/:x:/r/sites/Helhetligkvalitetssystem/_layouts/15/Doc.aspx?sourcedoc=%7B1416B70A-089A-49D8-99C1-AA5CA1D5DB1C%7D&file=Minimum%20kontrollrammeverk%20%25u00f8konomisystem%20(v1.1).xlsx&action=default&mobileredirect=true) med hele kontrollrammeverket

---

Lenker til relevant, eksisterende dokumentasjon for Team hel ved og tjenesten Utsjekk:

- Tryggnok risiko 29609: [Audit-logg for endringer gjort på Kafka forsvinner om teamet leges ned](https://apps.powerapps.com/play/f8517640-ea01-46e2-9c09-be6b05013566?app=567&ID=1674)
- [Rutine for å sjekke audit logs](https://github.com/navikt/helved-utbetaling/blob/main/dokumentasjon/Rutiner/Rutine%20for%20kontroll%20av%20audit%20logs.md)
- [K125.2: Sikkerhet i økonomisystemet](https://etterlevelse.ansatt.nav.no/dokumentasjon/85240a1e-2b08-4298-b7a3-bbb520472dac/OKONOMI/krav/125/2). Se suksesskriterium 2 (audit logging).


## Oppsett av logger

Alle våre databaser har påskrudd audit logging. Se krav [K125.2: Sikkerhet i økonomisystemet](https://etterlevelse.ansatt.nav.no/dokumentasjon/85240a1e-2b08-4298-b7a3-bbb520472dac/OKONOMI/krav/125/2) for ytterligere dokumentasjon.


## Gjennomgang av logger

Vi har definert en [rutine for å sjekke audit logs](https://github.com/navikt/helved-utbetaling/blob/main/dokumentasjon/Rutiner/Rutine%20for%20kontroll%20av%20audit%20logs.md) minst en gang per tertial. 

Vi bruker verktøyet [Gaal (gjennomgang av audit logs)](https://audit-approval.iap.nav.cloud.nais.io/?team=helved&timeRange=7d) for å kontrollere audit logs for databasene. Som beskrevet i [etterlevelsesløsningen](https://etterlevelse.ansatt.nav.no/dokumentasjon/85240a1e-2b08-4298-b7a3-bbb520472dac/OKONOMI/krav/125/2) har vi laget en egen løsning for audit logging på Kafka i tillegg. 
