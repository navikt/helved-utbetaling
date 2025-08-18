# ADR: < kort frase som beskriver beslutning >

## Status

Utkast

## Kontekst
Alle konsumenter har fram til sommeren 2025 benyttet Utsjekk sitt REST API. Under panseret har Utsjekk hatt en egen task scheduler, som har holdt styr på hvilke utbetalingsoppdrag som var videresendt og hvilke som ikke var det. I tillegg hadde den egne tasker for å sjekke om oppdragene hadde fått kvittering fra OS. Scheduleren har hatt innebygget logikk for å prøve igjen dersom noe feilet (retry-mekanisme).

Scheduleren ble, sammen med resten av tjenesten for iverksetting av utbetalinger, arvet fra PO Familie. Team hel ved hadde ikke eierskap til koden.

REST API-er skaper krav om høy oppetid, siden konsumentene ikke får sendt oppdrag når API-et er nede. Det er i tillegg forventet høye topper i trafikken, ettersom både AAP og Dagpenger kjører mange utbetalinger etter hver meldeperiode.

Team hel ved tror det er bedre å ta i bruk Kafka – som er laget for hva scheduleren så langt har forsøkt å løse; altså kø, rekkefølge, feilhåndtering og gjentatte forsøk. Med Kafka streams får vi en enklere løsning som er mer robust og bedre skalerbar. I stedet for at alle konsumenter går mot samme REST API, ønsker vi at de produserer utbetalinger til hvert sitt Topic, som Utsjekk kan lese fra. Det betyr at de kan "sende" utbetalinger, selv om Utsjekk er nede. Utsjekk vil lese fra der den slapp, når den er oppe igjen. I tillegg vil topper i trafikk / belastning håndteres bedre enn hva som hadde vært tilfelle med et felles og synkront REST-API.



## Alternativer vurdert

< Valgfri seksjon - med liste med alternativer >

## Beslutning

< Hvilke(n) endring(er) foreslås som svar på utfordringen? >

## Konsekvenser

< Hva blir situasjonen etter at denne beslutningen eventuelt er tatt? Hva blir enklere eller vanskeligere på bakgrunn av dette? (Både positive og negative konsekvenser beskrives.) >

## Referanser:
- [Ansvarsdeling mellom vedtaksløsninger og Hel ved / Utsjekk](https://confluence.adeo.no/spaces/ARML/pages/688985372/013-ADR-P4+Ansvarsdeling+mellom+vedtaksl%C3%B8sninger+og+Utsjekk) på Confluence
