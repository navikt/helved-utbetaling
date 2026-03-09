# Statistikkern

Statistikkern leser fra Kafka topics og skriver til BigQuery for analyseformål.

## Topics
- `helved.utbetalinger.v1` — utbetalingsdata (perioder, beløp, stønad, fagsystem)
- `helved.status.v1` — statusoppdateringer (OK/FEILET)

## BigQuery
Data skrives til datasettet `helved_stats` med tabellene `utbetalinger` og `status`.

## Referanser
- [Flytte data fra Kafka til BigQuery (NADA-docs)](https://docs.knada.io/dataprodukter/dele/dataoverf%C3%B8ring/#flytte-data-fra-kafka-til-bigquery)
- [Metabase](https://docs.knada.io/analyse/metabase/)

## Dataprodukt
#### 1. Opprett dataproduktet
Gå til [data.ansatt.nav.no](https://data.ansatt.nav.no), logg inn, og klikk "Legg til nytt dataprodukt" i headeren.

#### 2. Legg til datasett
Klikk deg inn på det nye dataproduktet og velg "Legg til datasett". Her kobler du til BigQuery-tabellen eller viewet ditt.

## Metabase
[Metabase](https://metabase.ansatt.nav.no/) kan brukes til å lage dashboards og visualiseringer over dataene i BigQuery.

#### 1. Legg til i Metabase
Fra Datamarkedsplassen, trykk "Legg til i Metabase" på datasettet. Det opprettes da en database og en collection i Metabase med samme tilgangsstyring som i Datamarkedsplassen.

#### 2. Lag dashboards
Metabase-queries (kalt "Questions") og dashboards lagres under teamets mappe i "Our analytics". Tilgangen styres av mappen elementene ligger i.

Se [NADA Metabase-docs](https://docs.knada.io/analyse/metabase/) for detaljer om tilgangsstyring, sync av skjemaendringer og FAQ.