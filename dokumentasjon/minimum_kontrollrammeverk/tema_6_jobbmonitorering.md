# Tema 6: Jobbmonitorering

Status: Utkast

- [Powerpoint](https://navno.sharepoint.com/:p:/r/sites/Helhetligkvalitetssystem/Delte%20dokumenter/Minimum%20kontrollrammeverk%20%C3%B8konomisystem%20(MKR-%C3%98S)/Presentasjoner%20fra%20oppskytningsrampe/Tema%206%20oppstartsm%C3%B8te.pptx?d=w322b3c9a9f884e7aa1698e2d9d8d395d&csf=1&web=1&e=A0Imrs) med kravene for _Jobbmonitorering_.
- [Excel](https://navno.sharepoint.com/:x:/r/sites/Helhetligkvalitetssystem/_layouts/15/Doc.aspx?sourcedoc=%7B1416B70A-089A-49D8-99C1-AA5CA1D5DB1C%7D&file=Minimum%20kontrollrammeverk%20%25u00f8konomisystem%20(v1.1).xlsx&action=default&mobileredirect=true) med hele kontrollrammeverket.

## 6.1 Jobbmonitorering

I kontekst av jobbmonitorering kan vi dele løsningen i to hovedbolker:

1. Funksjonalitet for håndtering av utbetalinger
2. Funksjonalitet for grensesnittavstemming mot Oppdragssystemet (OS)


Løsningen for håndtering av utbetalinger er hendelsesdrevet og basert på kontinuerlig databehandling via Kafka Streams. Dataflyt skjer fortløpende, og ikke gjennom planlagte jobber mellom systemer. Det er implementert overvåking og varsling som ivaretar formålet med jobbmonitorering ved å sikre at avvik i dataflyten oppdages og håndteres.

Teamet har et eget verktøy, kalt Peisen, som viser en oversikt og status for alle transaksjoner per fagsystem. Her er det også mulig å resende et oppdrag manuelt, flytte meldinger mellom topics på Kafka, oppdatere statuser o.l. Alle slike endringer, som gjøres i forbindelse med feilsituasjoner, auditlogges.

Grensesnittavstemming mot OS er en planlagt jobb, som kjører kl 07:00 hver ukedag. Denne avstemmingen er beskrevet i [K121.1 Avstemming mellom fag og økonomi](https://etterlevelse.ansatt.nav.no/dokumentasjon/85240a1e-2b08-4298-b7a3-bbb520472dac/OKONOMI/krav/121/1)

Se også:
- [K120.1 Toveis sporing](https://etterlevelse.ansatt.nav.no/dokumentasjon/85240a1e-2b08-4298-b7a3-bbb520472dac/OKONOMI/krav/120/1)
- [Risiko 28442 (feilutbetaling) i ROS](https://apps.powerapps.com/play/f8517640-ea01-46e2-9c09-be6b05013566?app=567&ID=1674)