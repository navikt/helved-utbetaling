![img](logo.png)

Async betalingsløsning.

## Features
- Aggregater for å lage atomiske transaksjoner mot Oppdrag
- Større meldinger bestående av fler utbetalinger
- Dryrun av operasjonene (simulering)

## Topology
- Dagpenger sender en tykk melding med mange meldeperioder.
- Disse splittes opp i separate utbetalinger og vi utleder en deterministisk uid basert på meldeperioden.
- For å finne ut om utbetalingene er ny, endret, slettet, må vi joine med saker for å finne alle tidligere utbetalinger.
- Aggregatet slår sammen utbetalingene / oppdragene og lager èn oppdrag per sak (forventer bare 1 sak om gangen men fler er støttet).
- Hele oppdraget med alle utbetalingene blir enten OK eller FAILED.
- Status og oppdrag bruker ikke uid som kafka-key men den orginale keyen som kommer fra Topics.dp.
- Dette gjør det enklere for konsumentene å korrelere innsendt request med statuser.
- Til slutt lagrer vi en liste med primary keys på foreign-key topicet som senere brukes til å enten skippe eller persistere utbetalinger (setter de fra pending til aktuell).
- Vi bruker da Oppdraget (requesten) som kafka-key
  
![dp-stream](dp-stream.svg)

- Hver gang helved.utbetalinger.v1 blir produsert til akkumulerer vi uids (UtbetalingID) for saken og erstatter aggregatet på helved.saker.v1.
- Dette gjør at vi kan holde på alle aktive uids for en sakid per fagsystem.
- Slettede utbetalinger fjernes fra lista. 
- Hvis lista er tom men ikke null betyr det at det ikke er første utbetaling på sak.
  
![utbetalinger-to-sak](utbetalinger-to-sak.svg)

- Vi må vente med å lagre helved.utbetalinger.v1 til vi har fått en positiv kvittering.
- Hvis vi ikke klarer å validere med feil og Oppdrag UR svarer med 08 eller 12, så er det fortsatt den forrige utbetalingen som skal gjelde.
- Vi bruker Oppdrag (request) som kafka-key og må derfor fjerne mmel fra Oppdrag (response) for å trigge en join.
- Resultatet av joinen kan ikke være null, da har vi en bug.
  
![successful-utbetaling-stream](successful-utbetaling-stream.svg)

# Technical
### Heap Size
Kafka Streams (RocksDB) trenger eget minne som er uten for JVMen for state store.
Dette kan vi gjøre ved å f.eks sette `-XX:MaxRAMPercentage=60.0` i `Dockerfile`, 
dvs at 40% av minnet er tilgjengelig for resten av poden.

### CPU Cores
Fordi vi har 3 partisjoner på topicsa, trenger vi også 3 stream threads `num.stream.threads`.
Det betyr også at vi trenger 3 prosessor kjerner for å prosessere i parallell.
Derfor setter vi `spec.resources.requests.cpu: 3000m` i nais manifestet `prod.yml`.
Samtidig må vi fortelle JVM at det er 3 kjerner tilgjengelig i `Dockerfile` ved å sette `-XX:ActiveProcessorCount=3`.
Dette kan være nødvendig når man deployer til kubernetes/sky hvor antall prosessorer kan vise alt mellom 1 og 100000, 
uavhengig av hva vi har satt i nais-manifestet.

#### Resilience
Hvis man ønsker resilience må man sette `num.standby.replicas` til 1, men da trenger vi også
1 standby replica (pod) som har like mye ressurser tilgjengelig. Her er kobinasjonen av partisjoner og stream threads viktig.
Har man f.eks 2 stream threads, trenger vi 2 replicas med 2 cpuer hver, pluss 2 ekstra standby replikas med 2 cpuer hver.
Eller man kan ha 1 stream thread per replica og 1 core, da trenger vi totalt 6 replicas, etc.
Hvis vi tåler nedetid ved feil/restarts, må vi sette `num.standby.replicas` til `0` og vi trenger ikke lenger
ekstra ressurser for pod, og klarer oss med 3 stream threads og 3 cpu cores.

### Graceful shutdown
Vi har kafka, ktor og kubernetes vi må ta hensyn til ved shutdown.
Her har vi satt den lengste timeouten `spec.terminationGracePeriodSeconds: 70`.
For ktor har vi satt `shutdownTimeout` til `50s` og `shutdownGracePeriod` til `5s`.
For kafka har vi `45s` timeout. Det er viktig at kafka får minst, ktor i midten og kubernetes lengst, gjerne med litt leeway.

