# Grensesnittavstemming

En grensesnittavstemming består av en start-, data- og sluttmelding som sendes på MQ til Oppdragssystemet. 
Komplette eksempler på disse meldingene finnes lenger ned på denne siden.

Vi kan se resultatet av en avstemming i økonomiportalen i Q1, så fremt den sendes på riktig måte på riktig format. 

## Likt format på nøkler i oppdrag-XML og avstemming-XML
Når avstemmingen kjøres i hos Utbetaling kjøres det en `select` basert på `nokkelFom` og `nokkelTom` som finnes i avstemmingsXML-en. For at denne `select`-en skal gi resultat, 
så må formatet på disse nøklene være lik som `nokkelAvstemming` fra oppdragXML-en.

**Eksempel:**

I avstemming-XML (start-, data- og sluttmelding):

```xml
 <nokkelFom>2025-06-13-00.00.00.000000</nokkelFom>
 <nokkelTom>2025-06-15-23.59.59.999999</nokkelTom>
```

I oppdragXML for et av oppdragene som avstemmes:

```xml
<avstemming-115>
  <kodeKomponent>AAP</kodeKomponent>
  <nokkelAvstemming>2025-06-13-10.31.51.998858</nokkelAvstemming>
  <tidspktMelding>2025-06-13-10.31.51.998939</tidspktMelding>
</avstemming-115>
```

## Avstemmingsmeldingene
En grensesnittavstemming gjøres daglig (på virkedager når Oppdragsystemet er åpent) per fagområde / ytelse. Vi avstemmer AAP, Dagpenger, Tiltakspenger og Tilleggsstønader hver for seg i egne meldinger mot OS.

En grensesnittavstemming gjelder altså bare ett fagområde / ytelse og består av tre deler:

1. Startmelding
2. Datamelding
3. Sluttmedling

Alle de tre er XML-er som sendes til Oppdragssystemet via MQ. Meldingene knyttes sammen av en felles `avleverendeAvstemmingId`, som må være med i hver melding. Hvis avstemmingsmeldinger som hører sammen kommer med ulike `avleverendeAvstemmingId`, vil ikke OS klare å knytte de sammen. Følgelig feiler avstemmingen. Vi får ingen feedback på det utover at avstemmingen ikke dukker opp i økonomiportalen i Q1.

### Felter som finnes i alle tre meldinger

Alle start-, data- og sluttmeldinger inneholder `<aksjon>` som har følgene felter

| Felt         | Type/gyldige verdier  | Beskrivelse                                                                                               |
|:---------------|:----------------------|:--------------------------------------------------------------------------------------------------------|
| aksjonType     | START, DATA eller AVSL | Beskriver hvilken av de tre meldingstypene dette er |
| kildeType      | AVLEV | Vi setter alltid AVLEV |
| avstemmingType | GRSN | Vi setter alltid GRSN (grensesnittavstemming)|
| avleverendeKomponentKode | AAP, TILLST, TILTPENG eller DP | Fagområdet vi avstemmer for|
| mottakendeKomponentKode | OS |Alltid OS (Oppdragssystemet) |
| underkomponentKode | AAP, TILLST, TILTPENG eller DP | Fagområdet vi avstemmer for. Vet ikke hvorfor vi trenger både `avleverendeKomponentKode` og `underkomponentKode`, som har samme verdi  |
| nokkelFom | Vi bruker timestamp på formatet YYYY-MM-DD-HH.mm.ss.nnnnnn | Brukes i `select`-en OS gjør for å plukke oppdragene som skal avstemmes. Samme format som `nokkelAvstemming` i oppdrag-XML |
| nokkelTom | Vi bruker timestamp på formatet YYYY-MM-DD-HH.mm.ss.nnnnnn | Brukes i `select`-en OS gjør for å plukke oppdragene som skal avstemmes. Samme format som `nokkelAvstemming` i oppdrag-XML |
| avleverendeAvstemmingId | ID på avstemmingen | Det skal være samme ID på tvers av alle meldinger som inngår i en og samme avstemming (på tvers av START-, DATA-, og AVSL-meldingene for en og samme avstemming) |
| brukerId | AAP, TILLST, TILTPENG eller DP | Fagområdet vi avstemmer for. Vi setter Samme verdi her som i `avleverendeKomponentKode` og `underkomponentKode`|


### Felter som bare finnes i datamelding

Datameldingen er delt i fire bolker med felter gruppert i `<aksjon>`, `<total>`, `<periode>` og `<grunnlag>`. Førstnevnte inngår i alle meldinger, mens de tre siste bare finnes i datameldingen.

| Felt         | Type/gyldige verdier  | Beskrivelse                                                                                               |
|:---------------|:----------------------|:--------------------------------------------------------------------------------------------------------|
| aksjon     |  | Se tabellen over. `aksjon/*` inngår i alle meldinger|
| total/totalAntall     | Antall transaksjoner | |
| total/totalBelop      | Summering av sats (per oppdragslinje?) per transaksjon | Ingen utregning / beregning av f.eks total for en periode med dagsats, men kun selve satsen ) |
| total/fortegn | T eller F  | T for Tillegg eller F for fradrag (Tror vi i praksis alltid setter T?) |
| periode/datoAvstemtFom | Dato på formatet YYYYMMDDHH  | Info som vises i avstemmingsskjermbilde for økonomimedarbeider. Ingen funksjonell betydning. Timen er ikke viktig og kan egentlig være hva som helst, da den ikke vises i skjermbildet. |
| periode/datoAvstemtTom | Dato på formatet YYYYMMDDHH | Info som vises i avstemmingsskjermbilde for økonomimedarbeider. Ingen funksjonell betydning. Timen er ikke viktig og kan egentlig være hva som helst, da den ikke vises i skjermbildet.|
| grunnlag/godkjentAntall |  | |
| grunnlag/godkjentBelop |  | |
| grunnlag/godkjentFortegn |  | |
| grunnlag/varselAntall |  | |
| grunnlag/varselBelop |  | |
| grunnlag/varselFortegn |  | |
| grunnlag/avvistAntall |  | |
| grunnlag/avvistBelop|  | |
| grunnlag/avvistFortegn |  | |
| grunnlag/manglerAntall |  | |
| grunnlag/manglerBelop |  | |
| grunnlag/manglerFortegn |  | |


## Komplett eksempel på grensesnittavstemming

Her følger et komplett eksempel på grensesnittavstemming for tilleggsstønader (TILLST)

### Startmelding
```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:avstemmingsdata xmlns:ns2="http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1">
  <aksjon>
    <aksjonType>START</aksjonType>
    <kildeType>AVLEV</kildeType>
    <avstemmingType>GRSN</avstemmingType>
    <avleverendeKomponentKode>TILLST</avleverendeKomponentKode>
    <mottakendeKomponentKode>OS</mottakendeKomponentKode>
    <underkomponentKode>TILLST</underkomponentKode>
    <nokkelFom>2025-05-22-00.00.00.000000</nokkelFom>
    <nokkelTom>2025-05-22-23.59.59.999999</nokkelTom>
    <avleverendeAvstemmingId>_DUw5oZATdm30cmd35jpAA</avleverendeAvstemmingId>
    <brukerId>TILLST</brukerId>
  </aksjon>
</ns2:avstemmingsdata>
```


### Datamelding
```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:avstemmingsdata xmlns:ns2="http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1">
  <aksjon>
    <aksjonType>DATA</aksjonType>
    <kildeType>AVLEV</kildeType>
    <avstemmingType>GRSN</avstemmingType>
    <avleverendeKomponentKode>TILLST</avleverendeKomponentKode>
    <mottakendeKomponentKode>OS</mottakendeKomponentKode>
    <underkomponentKode>TILLST</underkomponentKode>
    <nokkelFom>2025-05-22-00.00.00.000000</nokkelFom>
    <nokkelTom>2025-05-22-23.59.59.999999</nokkelTom>
    <avleverendeAvstemmingId>_DUw5oZATdm30cmd35jpAA</avleverendeAvstemmingId>
    <brukerId>TILLST</brukerId>
  </aksjon>
  <total>
    <totalAntall>1</totalAntall>
    <totalBelop>14859</totalBelop>
    <fortegn>T</fortegn>
  </total>
  <periode>
    <datoAvstemtFom>2025052200</datoAvstemtFom>
    <datoAvstemtTom>2025052223</datoAvstemtTom>
  </periode>
  <grunnlag>
    <godkjentAntall>1</godkjentAntall>
    <godkjentBelop>14859</godkjentBelop>
    <godkjentFortegn>T</godkjentFortegn>
    <varselAntall>0</varselAntall>
    <varselBelop>0</varselBelop>
    <varselFortegn>T</varselFortegn>
    <avvistAntall>0</avvistAntall>
    <avvistBelop>0</avvistBelop>
    <avvistFortegn>T</avvistFortegn>
    <manglerAntall>0</manglerAntall>
    <manglerBelop>0</manglerBelop>
    <manglerFortegn>T</manglerFortegn>
  </grunnlag>
</ns2:avstemmingsdata>
```

### Sluttmelding
```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:avstemmingsdata xmlns:ns2="http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1">
  <aksjon>
    <aksjonType>AVSL</aksjonType>
    <kildeType>AVLEV</kildeType>
    <avstemmingType>GRSN</avstemmingType>
    <avleverendeKomponentKode>TILLST</avleverendeKomponentKode>
    <mottakendeKomponentKode>OS</mottakendeKomponentKode>
    <underkomponentKode>TILLST</underkomponentKode>
    <nokkelFom>2025-05-22-00.00.00.000000</nokkelFom>
    <nokkelTom>2025-05-22-23.59.59.999999</nokkelTom>
    <avleverendeAvstemmingId>_DUw5oZATdm30cmd35jpAA</avleverendeAvstemmingId>
    <brukerId>TILLST</brukerId>
  </aksjon>
</ns2:avstemmingsdata>
```

### Oppdrag-XML
Som nevnt over må formatet på nøklene `nokkelFom` og `nokkelTom` i avstemmingsXML-ene ha samme format som `nokkelAvstemming` i oppdrag-XML.

Eksempel som harmonerer med start-, data- og sluttmeldingen vist over:

```xml
<avstemming-115>
      <kodeKomponent>TILLST</kodeKomponent>
      <nokkelAvstemming>2025-05-23-00.00.00.000000</nokkelAvstemming>
      <tidspktMelding>2025-05-23-00.00.00.000000</tidspktMelding>
</avstemming-115>

```
---
## Relatert

https://github.com/navikt/tjenestespesifikasjoner/blob/master/avstemming-v1-tjenestespesifikasjon/src/main/wsdl/no/nav/virksomhet/tjenester/avstemming/meldinger/meldinger.xsd 
