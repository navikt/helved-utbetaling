# Guide for overvåking og feilfiks

## Alerts og logger

Alerts finnes for både prod og dev i [#team-hel-ved-alerts](https://slack.com/archives/C06V6D12KHC). Hvis det ikke dukker opp noen alerts her, er alt (sannsynligvis) bra og man trenger ikke gjøre noe.


### Hvilke alerts har vi?

* Manglende kvittering (trigges når oppdraget har stått ukvittert én time innenfor OS sine normale åpningstider 0600-2100)
* FEILET-status på utbetalingsoppdrag
* Mismatch mellom pending- og utbetalinger-topic (skal være like etter at oppdraget er kvittert)
* Feil i logger
* Ukjent feil i simulering (en eller flere feilmeldinger vi ikke har sett før fra simuleringstjenesten, og som vi følgelig ikke har oversatt til noe forståelig for brukerne våre)
* App nede
* Kafka-consumer har mer enn 5 uleste records (bottleneck)
* Nyoppdaget sårbarhet med CVSS 9.0 eller mer


### Logger

* Sikker logg er i [GCP](https://console.cloud.google.com/logs/)
* Applikasjonslogg i [Grafana](https://grafana.nav.cloud.nais.io/goto/cfqou2its3y80a?orgId=1)

## Repoer, dokumentasjon og verktøy

### Team hel ved sine ting
* Hel ved [på NAIS](https://console.nav.cloud.nais.io/team/helved) 
* Team hel ved sine [repos på GitHub](https://github.com/orgs/navikt/teams/helved/repositories)
* I monorepoet [helved-utbetaling](https://github.com/navikt/helved-utbetaling) er det README per app som forklarer hvorfor appen finnes og hva den gjør
* [Verktøy for generering og konvertering](https://probable-adventure-7pw92pw.pages.github.io/) av UUID med mer
* [Intern dok på GitHub](https://github.com/navikt/helved-utbetaling/tree/main/dokumentasjon) med forklaringer på avstemming, oppdrag-XML osv
* Dok for konsumenter finnes på https://helved-docs.ansatt.dev.nav.no/

### Utbetaling sine ting
* [Beskrivelse av input til OS](https://confluence.adeo.no/spaces/OKSY/pages/178067742/Inputdata+fra+fagrutinen+til+Oppdragssystemet) (confluence)
* [Utbetalingsportalen](https://utbetalingsportalen.intern.dev.nav.no/) er den nye økonomiportalen. Trenger ikke remote desktop.

### Mer på Slack
Se tabs-ene vi har i toppen på [#team-hel-ved](https://slack.com/archives/C06SJTR2X3L) på Slack. Der er det flere lenker til verktøy o.l.


## Feilsituasjoner

### Kvittering mangler

Det kan være flere grunner til at en kvittering mangler:

1. OS har ikke klart å ta i mot utbetalingen
2. OS har ikke klart å generere/sende ut kvittering
3. Vi (Urskog) har ikke klart å ta i mot/håndtere kvitteringen
4. Nettverksproblemer

Manglende kvittering følges opp med Utbetalingsseksjonen på slack, enten i [#utbetaling](https://slack.com/archives/CKZADNFBP) eller i [#utbetaling-p4-nye-løsninger](https://slack.com/archives/C054SA92WR4). 

Det kan være lurt å først sjekke kanaler som [#varsling-nedetid](https://nav-it.slack.com/archives/CEE8ZNS81), [#produksjonshendelser](https://nav-it.slack.com/archives/C9P60F4F3) og [#utbetaling](https://nav-it.slack.com/archives/CKZADNFBP) for å se om det er kjente årsaker til at kvitteringen uteblir. OS stenger noen ganger tidligere enn vanlig, som gjør at kvitteringen kommer på plass når det åpner igjen neste virkedag. Normal åpningstid er 06:00-21:00 på vanlige ukedager. Det er stengt i helg og på røddager.


Det kan hende at en manglende kvittering ligger på en egen backout-kø på MQ. Da kan man i så fall deploye en fiks og be om å få meldingen flyttet til den vanlige kvitteringskøen i [#mq](https://slack.com/archives/CBF1W2DAB)-kanalen på Slack. [Her er et eksempel](https://nav-it.slack.com/archives/CBF1W2DAB/p1709286807404399) på en slik melding.

Hvis utbetalingen faktisk har blitt prosessert i OS/UR og skulle blitt kvittert OK, så kan man trykke Legg til kvittering” på det aktuelle oppdraget i Peisen og velge `00 - OK`. Man kan også be Utbetalingsseksjonen om å sende kvitteringen på nytt, slik som i [denne tråden på slack](https://nav-it.slack.com/archives/CKZADNFBP/p1780396432528819).

I sjeldne tilfeller kan MQ-tilkoblinga falle ut. Det er retry-mekanisme på plass for å koble til på nytt automatisk. Om den feiler kan en fiks være å restarte Urskog. 


### Feilkvittering fra OS

Kvitteringer kommer i form av XML med `<mmel>` i toppen. Resten av XML-en er lik den vi sendte inn. Innenfor `<mmel>` finner vi blant annet feltet `alvorlighetsgrad` hvor verdien `08` eller `12` betyr at noe feila og at utbetalingen ikke gikk gjennom. Alvorlighetsgrad kan ha følgende verdier:


- `00`: Alt OK
- `04`: OK, men med informasjon / varsel som bør ses på. Transaksjonen er uansett godtatt av OS
- `08`: Feil i validering / funksjonell feil. Transaksjonen er avvist av OS
  - Fiks feil i dataene og send på nytt
* `12`: Teknisk [forbigående feilhos](https://nav-it.slack.com/archives/CKZADNFBP/p1682661207600579?thread_ts=1682491699.828299&cid=CKZADNFBP) OS eller andre de avhenger av (PDL e.l)
  - Prøv på nytt senere


Fra tid til annen kan noe feile. Her er et eksempel på en feilkvittering:
``` xml
<mmel>
    <systemId>231-OPPD</systemId>
    <kodeMelding>B110008F</kodeMelding>
    <alvorlighetsgrad>08</alvorlighetsgrad>
    <beskrMelding>Oppdraget finnes fra før</beskrMelding>
    <programId>K231BB10</programId>
    <sectionNavn>CA10-INPUTKONTROLL</sectionNavn>
  </mmel>
```

Her er noen av feilkvitteringene vi har sett:

#### Oppdraget finnes fra før (alvorlighetsgrad 08)
Dette skjer hvis det finnes tidligere utbetalinger på saken og man setter `<kodeEndring>NY</kodeEndring>` innenfor `<oppdrag-110>` i et nytt oppdrag. Da sier man i praksis "dette er første utbetaling på saken", selv om det ikke er det. Det aksepterer ikke OS.

En årsak til denne feilen kan være at et oppdrag har blitt sendt flere ganger til OS. Hvis det ikke finnes tidligere utbetalinger på saken, vil OS akseptere den første. Men påfølgende vil bli avvist fordi det da finnes en tidligere utbetaling. 
  
En annen årsak kan være simulering. Et utbetalingsoppdrag kan feile med `Oppdraget finnes fra før` hvis man samtidig har en pågående simuleringsrequest. Man må vente til simuleringsrequesten har returnert før man sender utbetalingsoppdraget. Det er fordi en simulering i praksis er et utbetalingsoppdrag med database-rollback i OS. Den følger altså samme løype som utbetalingen, men rulles tilbake. I tillegg må et ubetalingsoppdrag være kvittert før man kan simulere videre på den samme saken. Om man simulerer før siste utbetaling er kvittert gir vi statuskode 409 (conflict)

Når man endrer eller legger til noe nytt på eksisterende sak, må man sette `<kodeEndring>ENDR</kodeEndring>`. [Se doken vår på oppdrag-XML](https://github.com/navikt/helved-utbetaling/blob/main/dokumentasjon/oppdrag_xml.md) for hvordan oppdragene er bygget opp. 

#### DELYTELSE-ID finnes i oppdragsbasen fra før (alvorlighetsgrad 08)
Dette betyr at oppdragslinjen finnes fra før i OS. Vi får denne om vi sender samme oppdrag flere ganger. Hvis vi setter `<kodeEndring>NY</kodeEndring>` og sender flere ganger vil feilmeldinga være `Oppdraget finnes fra før`, ref første punkt over her. Men om vi setter `<kodeEndring>ENDR</kodeEndring>` og sender flere ganger, så vil feilmeldingen være `DELYTELSE-ID finnes i oppdragsbasen fra før`.
  
Eksempel fra dev: Vi fikk denne feilen da vi la samme oppdrag på MQ to ganger i forbindelse med en deploy. Appen la det først på MQ rett før shut down, også ble det lagt på MQ på nytt 8 sekunder senere da appen var oppe igjen. Her er saken og kommentarer vi hadde på det, ink XML-en med kvitteringen: https://github.com/navikt/team-helved/issues/211

#### DELYTELSE-ID/LINJE-ID ved endring finnes ikke (alvorlighetsgrad 08)
Vi får denne om vi prøver å endre en oppdragslinje som ikke finnes. Det kan skje om vi kåler det til og setter ENDR der det skulle vært NY (i feltet `kodeEndringLinje` på `oppdragslinje-150`), slik som i [det tilfellet her](https://nav-it.slack.com/archives/C072V0SFUQH/p1743166440444879)


#### Overlappende avvent-perioder (alvorlighetsgrad 08)
AAP bruker en funksjon kalt avvent i OS. [Avvent er dokumentert her](https://github.com/navikt/helved-utbetaling/blob/main/dokumentasjon/oppdrag_xml.md#avvent-118). 

Feilen skjer når AAP endrer fra-og-med-dato for en avventperiode, før dato for overføring har passert.

Når feilen skjer kan man først høre med AAP, for eksempel på [#team-aap-åpen](https://nav-it.slack.com/archives/C055PS4FV0W), om de håndterer det selv. [Det finnes et endepunkt](https://helved-docs.ansatt.dev.nav.no/async/rest#feilregistrer_avvent) for å sette avvent til feilregistrert. 

Utbetalingsseksjonen må involveres om nevnte endepunkt ikke kan brukes. Ta kontakt på [#utbetaling](https://nav-it.slack.com/archives/CKZADNFBP) og be om at det blir satt `J` i feilreg på avventperioden som ligger på den aktuelle saken.

Når avvent er satt til feilregistrert kan utbetalingsoppdraget sendes på nytt. Om ikke AAP-teamet gjør det selv, så kan vi gjøre det for dem via Peisen.

#### Referert vedtak/linje ikke funnet (alvorlighetsgrad 08)
Denne har vi ikke sett ofte, men den betyr at vi prøver å bygge videre på en kjede fra en delytelsesId som ikke finnes i OS.

Eksempel: Vi forsøkte å bygge videre fra en delytelsesId som var i et oppdrag som ble avvist og som aldri fant veien inn til OS. 

Det [er løst](https://github.com/navikt/helved-utbetaling/commit/d2b3a76061e57338fa2fffd359bfe95152d2cd82) slik at det ikke skal skje igjen. Den aktuelle saken ble fikset ved å flytte forrige utbetaling fra pending-topic til utbetalinger-topic (det oppstod en mismatch), for så å bygge og sende det siste oppdraget (som feilet) på nytt.


#### Øvrige feilmedlinger fra OS
Det kan komme andre feil, men det er sjeldent. Kvitteringen kommer med en `<beskrMelding>` som man kan kikke på, og de er stort sett forklarende nok. Ta ellers kontakt med f.eks [#utbetaling](https://slack.com/archives/CKZADNFBP) for å spørre hva feilmeldinger betyr eller hjelp til å debugge.


### Feil eller avvik i grensesnittavstemming

Avstemming har ingenting å si for hvorvidt brukere får utbetalt eller ikke, så det er sånn sett ikke kritisk å fikse i ferien. Dersom en avstemming har et utbetalingsoppdrag for mye eller for lite, eller at det av andre årsaker oppstår avvik, så skal man ikke korrigere det på neste avstemming. Da blir det avvik der også. Økonomi / utbetaling kontakter oss hvis det er avvik de ikke finner ut av selv. Så kan vi bistå med å finne årsaken.

Her er et [eksempel på hvordan vi har håndtert avvik ved å avstemme manuelt](https://nav-it.slack.com/archives/CKZADNFBP/p1750328706716519) på slack.

Om vi må avstemme manuelt er det enkelt og rett frem å hente tallene via avstemming-taben i Peisen. Der kan man kjøre en avstemming-dryrun av hvilken som helst periode. En dryrun sendes ikke til OS, men vi kan poste dem manuelt i ei melding på slack ref avsnittet over.

På avstemmings-taben ser man resultatet av siste avstemming. I tillegg kan man sjekke avstemming-topicet for å bla bakover i eldre avstemminger. Vi logger også hva innholdet i avstemmingene var. [Denne henter slike loggmeldinger](https://cloudlogging.app.goo.gl/zGf5Fu9vGwdt7AQF8) for de to siste dagene.

#### Hva om avstemmingsgrunnlaget er feil?
Det har hendt at avstemmingsgrunnlaget i vedskiva har vært feil. [Avstemmingsgrunnlaget i vedskiva ble feil](https://nav-it.slack.com/archives/C06SJTR2X3L/p1781611393367689) da det var Kafka-trøbbel hos Aiven i mai/juni. 

For å finne ut hvilke transaksjoner som manglet i avstemmingsgrunnlaget gjorde vi en diff på data i Urskog og vedskiva. Da fant vi transaksjoner i Urskog som ikke var lagret i Vedskiva. Se oppgave [#577](https://github.com/navikt/team-helved/issues/577) for detaljer.

### Andre typer feil

#### Dobbeltsending
Hvis et utbetalingsoppdrag ved en feil sendes flere ganger vil det som regel enten stoppes av duplikat/idempotenssjekk i Utsjekk eller avvises i OS. Da typisk på grunn av `Oppdraget finnes fra før` eller `DELYTELSE-ID finnes i oppdragsbasen fra før`. Om noen skulle slippe gjennom kan det for eksempel oppdages med [en spørring som denne](https://github.com/navikt/team-helved/issues/566#issuecomment-4716052363) som også kan være [grunnlag for alarmer](https://github.com/orgs/navikt/projects/133/views/1?pane=issue&itemId=202426099&issue=navikt%7Cteam-helved%7C579) e.l slik at de kan stoppes tidlig i OS.

Manuelt opphør eller stopping av utbetalingsoppdrag i OS kan forespørres ved å lage en [sak som denne](https://jira.adeo.no/plugins/servlet/desk/portal/541/FAGSYSTEM-434926) i Porten / Jira.

#### PendingReady=true, sakerReady=false
Om saker-topicet ikke blir klart selv etter litt tid kan det komme en feilstatus som sier `Status er satt til FEILET grunnet intern helved-inkonsistens og må fikses manuelt i helved. Konsument og OS trenger IKKE gjøre noe`. Oppdraget er i disse tilfellene OK. Man trenger som regel da bare å "flippe statusen" ved å trykke `Send OK-status` på den aktuelle FEILET-statusen i peisen.






