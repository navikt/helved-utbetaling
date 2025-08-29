# ADR: < kort frase som beskriver beslutning >

## Status

Utkast

## Kontekst
Alle konsumenter har fram til sommeren 2025 benyttet Utsjekk sitt REST API. Under panseret hadde Utsjekk en egen Postgres-basert task scheduler, arvet fra PO Familie. Den holdt styr på hvilke oppdrag som var sendt til OS/UR, sjekket jevnlig om de hadde fått kvittering, og forsøkte på nytt når noe feilet. Løsningen fungerte, men den var komplisert vanskelig å endre. Team Hel Ved hadde ikke eierskap til koden.

REST-API-et skapte også krav om høy oppetid, siden konsumentene ikke fikk sendt oppdrag når Utsjekk var nede. I tillegg forventes store topper i trafikken, for eksempel når AAP og Dagpenger kjører mange utbetalinger etter hver meldeperiode.

Vi tror Kafka passer bedre til behovene scheduleren forsøkte å løse: kø, rekkefølge, feilhåndtering og gjentatte forsøk. Med Kafka Streams får vi en enklere, mer robust og skalerbar løsning. I stedet for at alle konsumenter går mot samme REST-endepunkt, kan de produsere utbetalinger til egne topics som Utsjekk leser fra. Dermed kan oppdrag «sendes» selv om Utsjekk er nede, og systemet håndterer trafikk-topper bedre enn et felles, synkront API ville gjort.


## Alternativer vurdert

- **Videreføre REST-API og scheduler**<br/>
Ville fortsatt gitt krav om høy oppetid på API-et. Skalerbarhet og toppbelastning ville fortsatt vært en utfordring. Teamet måtte ha tatt eierskap til komplisert kode.
- **Kafka som asynkron integrasjon**<br/>
Konsumenter kan sende oppdrag når som helst, uten å være avhengig av at Utsjekk er oppe samtidig. Bedre håndtering av topper i trafikk, og fjerner behovet for å vedlikeholde en egen scheduler. Kafka er godt kjent i Nav.
- Mer?


## Beslutning

Utsjekk går over til en asynkron integrasjon basert på Kafka. Konsumentene publiserer utbetalingsoppdrag til egne topics, som Utsjekk leser og prosesserer med Kafka Streams. REST-API-et beholdes midlertidig for bakoverkompatibilitet, men fases ut når konsumentene har tatt i bruk den nye løsningen.

## Konsekvenser

**Fordeler**
- Konsumentene kan sende oppdrag når som helst, uten å være avhengig av at Utsjekk er oppe samtidig.
- Trafikktopper glattes ut i køen i stedet for å måtte håndteres i sanntid.
- Enklere og mer robust enn den gamle scheduleren
- Bedre håndtering av økt trafikk / belastning
- Kafka er godt kjent i Nav

**Ulemper**
- Overgangsperiode med to grensesnitt (REST + Kafka) å vedlikeholde
- Eksisterende konsumenter må gjøre en endring hos seg for å gå over fra REST til Kafka
- Historiske utbetalinger som har kommet inn via iverksetting-løypa på REST (tiltpeng og tillst) ligger i en annen datamodell. For at disse skal kunne endres videre i Kafka-løypa, må de migreres til den nye modellen 
- Ny kompetanse kreves i teamet på observabilitet med mer i Kafka?


## Referanser:
- [Ansvarsdeling mellom vedtaksløsninger og Hel ved / Utsjekk](https://confluence.adeo.no/spaces/ARML/pages/688985372/013-ADR-P4+Ansvarsdeling+mellom+vedtaksl%C3%B8sninger+og+Utsjekk) på Confluence
