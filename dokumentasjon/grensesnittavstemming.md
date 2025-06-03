
> ⚠️ **TODO – Avklaringer med Utbetaling**
>
> - Hva brukes `datoAvstemtFom` og `datoAvstemtTom` i avstemming-XML til?  
>   Hva er gyldig format? Per 2. juni benytter vi `YYYYMMDDHH` (f.eks. `2025060200` og `2025060223`) i disse feltene.
>
> - Hva er forskjellen på `datoAvstemtFom` / `datoAvstemtTom` og `nokkelFom` / `nokkelTom`?  
>   Per 2. juni benytter vi ulike formater i disse feltene. `nokkelFom` og `nokkelTom` har timestamp med mikrosekunder, f.eks. `2025-06-02-00.00.00.000000`.
>
> - Hva brukes `tidspktMelding` i oppdrag-XML til?  
>   Hva er gyldig format? Per 2. juni bruker vi samme format som i `nokkelFom` og `nokkelTom`.
>
> - Skal vi avstemme alt vi har mottatt i Utsjekk mellom 00:00:00 - 23:59:59 hver dag? Eller tar vi f.eks kun med oppdrag som blir sendt fra Utsjekk til OS før OS stenger (kl 21:00)?
> - Har det noe å si *når* på døgnet en avstemming sendes fra Utsjekk til OS? Er det lik "tidsfrist" i Q1 og Prod? Per nå sender vi avstemmingene om morgenen, når OS har åpnet påfølgende virkedag, i både Q1 og prod.
---

# Grensesnittavstemming

En grensesnittavstemming består av en start-, data- og sluttmelding som sendes på MQ til Oppdragssystemet. 
Komplette eksempler på disse meldingene finnes lenger ned på denne siden.

Vi kan se resultatet av en avstemming i økonomiportalen i Q1, så fremt den sendes på riktig måte på riktig format. 

## Likt format på nøkler i oppdrag-XML og avstemming-XML
Når avstemmingen kjøres i OS kjøres det en SQL basert på `nokkelFom` og `nokkelTom` som finnes i avstemmingsXML-en. For at denne SQL-en skal gi resultat, 
så må formatet på disse nøklene være lik som `nokkelAvstemming` fra oppdragXML-en.

**Eksempel:**

I avstemming-XML (start-, data- og sluttmelding):

```xml
<nokkelFom>2025-05-21-00.00.00.000000</nokkelFom>
<nokkelTom>2025-05-21-23.59.59.999999</nokkelTom>
```

I oppdragXML for et av oppdragene som avstemmes:

```xml
<avstemming-115>
    <kodeKomponent>TILLST</kodeKomponent>
    <nokkelAvstemming>2025-05-21-14.00.00.000000</nokkelAvstemming>
    <tidspktMelding>2025-05-21-14.00.00.000000</tidspktMelding>
</avstemming-115>
```

Det viktige er at formatet på nøklene er likt på tvers. Et enklere format med kun dato vil også fungere. 
Så vi kunne altså like godt bare sagt `2025-05-21` for `nokkelFom` og `nokkelTom` i avstemming-XML og på `nokkelAvstemming` i oppdrag-XML.


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
| aksjonType     |  | |
| kildeType      |  | |
| avstemmingType |  | |
| avleverendeKomponentKode |  | |
| mottakendeKomponentKode |  | |
| underkomponentKode |  | |
| nokkelFom |  | |
| nokkelTom |  | |
| avleverendeAvstemmingId |  | |
| brukerId |  | |

### Felter som bare finnes i datamelding

Datameldingen er delt i fire bolker med felter gruppert i `<aksjon>`, `<total>`, `<periode>` og `<grunnlag>`. Førstnevnte inngår i alle meldinger, mens de tre siste bare finnes i datameldingen.

| Felt         | Type/gyldige verdier  | Beskrivelse                                                                                               |
|:---------------|:----------------------|:--------------------------------------------------------------------------------------------------------|
| aksjon     |  | Se tabellen over. `aksjon/*` inngår i alle meldinger|
| total/totalAntall     |  | |
| total/totalBelop      |  | |
| total/fortegn |  | |
| periode/datoAvstemtFom |  | |
| periode/datoAvstemtTom |  | |
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

