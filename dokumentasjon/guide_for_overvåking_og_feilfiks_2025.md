# Guide for overvåking og feilfiks (2025)

## Alerts og logger

Alerts finnes for både prod og dev i [#team-hel-ved-alerts](https://slack.com/archives/C06V6D12KHC). Hvis det ikke dukker opp noen alerts her, er alt (sannsynligvis) bra og man trenger ikke gjøre noe.


### Hvilke alerts har vi?

* Manglende kvittering (når trigges den?)
* Feil i logger
* App nede
* Ukjent feil i simulering (en eller flere feilmeldinger vi ikke har sett før fra simuleringstjenesten, og som vi følgelig ikke har oversatt til noe forståelig for brukerne våre)
* Mer?



### Logger

* Sikker logg er på https://console.cloud.google.com/logs/
* Applikasjonslogg i Grafana

## Repoer og dok

* Full oversikt over repoer finnes på https://github.com/orgs/navikt/teams/helved/repositories
* Intern dok på GitHub med forklaringer på avstemming, oppdrag-XML osv
* Dok for konsumenter på https://helved-docs.ansatt.dev.nav.no/
* Hel ved på NAIS https://console.nav.cloud.nais.io/team/helved
* Beskrivelse av input til OS (confluence)


Se også de forskjellige tabbene / bokmerkene vi har på [#team-hel-ved](https://slack.com/archives/C06SJTR2X3L)



### Feilsituasjoner



### Todo

* Hva om restart av apper kan være aktuelt? Hva kan en typisk årsak være, og er det en bestemt rekkefølge man bør restarte i?
* Liste opp appene våre og forklare kort hva de gjør / hvorfor vi har dem?



### Kvittering mangler

Det kan være flere grunner til at en kvittering mangler:

1. OS har ikke klart å ta i mot utbetalingen
2. OS har ikke klart å generere/sende ut kvittering
3. Vi (Urskog) har ikke klart å ta i mot/håndtere kvitteringen
4. Nettverksproblemer


Ved situasjon 2 er det mulig at kvitteringen ligger på en egen backoff-kø i MQ. Da kan man i så fall deploye en fiks og be om å få meldingen flyttet til den vanlige kvitteringskøen i [#mq](https://slack.com/archives/CBF1W2DAB).  Her er et eksempel på en slik melding:
https://nav-it.slack.com/archives/CBF1W2DAB/p1709286807404399

Ved situasjon 1 og 2 må dette følges opp med Utbetaling på slack, enten i [#utbetaling](https://slack.com/archives/CKZADNFBP) eller i [#utbetaling-p4-nye-løsninger](https://slack.com/archives/C054SA92WR4), også finner man beste løsning sammen. [Her er et eksempel](https://nav-it.slack.com/archives/CKZADNFBP/p1718173879004949) på en slik melding fra et annet team. Hvis utbetalingen faktisk har blitt prosessert i OS/UR og skulle blitt kvittert OK, så kan man trykke “Endre kvittering” på det aktuelle oppdraget i Peisen og velge `00 - OK`

Nettverksproblemer kan også gjøre at f.eks kvitteringer ikke kommer frem. Symptomet og fremgangsmåten vil likevel det samme som for situasjon 1 og 2.

I sjeldne tilfeller har MQ-lytteren vår, som lytter på kvitteringer, falt ut. Det har ikke vært er problem på veldig lenge og det ser ut som at [fiksen (lenke til kommentar på oppgave i boardet)](https://github.com/navikt/team-helved/issues/89#issuecomment-2634183688) har fungert. Sist det skjedde var i november 2024:
https://nav-it.slack.com/archives/C072V0SFUQH/p1731325805636459


### Feilkvittering fra OS


Kvitteringer kommer i form av XML med `<mmel>` i toppen. Resten av XML-en er lik den vi sendte inn. Innenfor `<mmel>` finner vi blant annet feltet `alvorlighetsgrad` hvor verdien `08` eller `12` betyr at noe feila og at utbetalingen ikke gikk gjennom. Alvorlighetsgrad kan ha følgende verdier:


* `00`: Alt OK
* `04`: OK, men med informasjon / varsel som bør ses på. Transaksjonen er uansett godtatt av OS
* `08`: Feil i validering / funksjonell feil. Transaksjonen er avvist av OS
    * Fiks feil i dataene og send på nytt
* `12: Teknisk [forbigående feilhos](https://nav-it.slack.com/archives/CKZADNFBP/p1682661207600579?thread_ts=1682491699.828299&cid=CKZADNFBP) OS eller andre de avhenger av (PDL e.l)
    * Prøv på nytt senere


Fra tid til annen, og som oftest i dev, kan noe feile. Her er et eksempel på en feilkvittering:
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

Her er feilkvitteringene vi har sett mest av:


* #### Oppdraget finnes fra før (alvorlighetsgrad 08)
  Dette skjer dersom vi skal korrigere en sak, men setter `<kodeEndring>NY</kodeEndring>` rett innenfor noden `<oppdrag-110>` . OS vil da tolke det som at vi prøver å opprette saken på nytt. Når man endrer eller legger til noe nytt på eksisterende sak, må man i stedet sette `<kodeEndring>ENDR</kodeEndring>`. [Se doken vår på oppdrag-XML](https://github.com/navikt/helved-utbetaling/blob/main/dokumentasjon/oppdrag_xml.md) for hvordan oppdragene er bygget opp. Det er alltid én `<oppdrag-110>`. Den kan ha flere `<oppdragslinje-150>` som hver representerer en oppdragslinje i OS.

    * [Her er en slack-tråd med et eksempel på en sak](https://nav-it.slack.com/archives/C06SJTR2X3L/p1747040121503279) med denne feilen, som skyldtes en optimistisk timeout hos tiltakspenger. De sendte samme oppdrag med fem sekunders mellomrom.

    * Denne feilen har også blitt trigget av AAP som sendte flere utbetalinger samtidig. Det trigget et samtidighetsproblem: Vi må si til OS hva som er første utbetaling på en sak. Det gjør vi ved å sette `<kodeEndring>NY</kodeEndring>` på `oppdrag-110` på det oppdraget som faktisk er første utbetaling på saken. Om vi gjør det på to utbetalinger som sendes samtidig på samme sak, så vil OS akseptere den ene men avvise den andre. Problemet er løst ved at AAP sender utbetalinger, innenfor samme sak, i sekvens. Ref Frode: [Frode Lindås: "Har nå lagt ut en fiks for å sørge for sek..."](https://nav-it.slack.com/archives/C06SJTR2X3L/p1748955375346549?thread_ts=1748424655.966809&cid=C06SJTR2X3L)

* #### DELYTELSE-ID finnes i oppdragsbasen fra før (alvorlighetsgrad 08)
  Dette betyr at oppdragslinjen finnes fra før i OS. Vi får denne om vi prøver å sende inn samme oppdrag to ganger. Vi fikk den sist i dev 13 juni da vi la samme oppdrag på MQ to ganger i forbindelse med en deploy. Appen la det først på MQ rett før shut down, også ble det lagt på MQ på nytt 8 sekunder senere da appen var oppe igjen. Her er saken og kommentarer vi hadde på det, ink XML-en med kvitteringen: https://github.com/navikt/team-helved/issues/211

* #### DELYTELSE-ID/LINJE-ID ved endring finnes ikke (alvorlighetsgrad 08)
  Vi får denne om vi prøver å endre en oppdragslinje som ikke finnes. Det kan jo skje at vi kåler det til og setter ENDR der det skulle vært NY (i feltet `kodeEndringLinje` under `oppdragslinje-150`), slik som i det tilfellet her


Det kan komme andre feil, men det er sjeldent. Kvitteringen kommer med en `<beskrMelding>` som man kan kikke på, og de er stort sett forklarende nok. Ta ellers kontakt med f.eks [#utbetaling](https://slack.com/archives/CKZADNFBP) for å spørre hva feilmeldinger betyr eller hjelp til å debugge


### Feil eller avvik i grensesnittavstemming

Avstemming har ingenting å si for hvorvidt brukere får utbetalt eller ikke, så det er sånn sett ikke kritisk å fikse i ferien. Dersom en avstemming har et utbetalingsoppdrag for mye eller for lite, eller at det av andre årsaker oppstår avvik, så skal man ikke korrigere det på neste avstemming. Da blir det avvik der også. Økonomi / utbetaling kontakter oss hvis det er avvik de ikke finner ut av selv. Så kan vi bistå med å finne årsaken.

Her er et [eksempel på hvordan vi har håndtert avvik ved å avstemme manuelt](https://nav-it.slack.com/archives/CKZADNFBP/p1750328706716519) på slack.

Vi logger hva innholdet i avstemmingene var. [Denne henter slike loggmeldinger](https://cloudlogging.app.goo.gl/zGf5Fu9vGwdt7AQF8) for de to siste dagene. 





