# Forklaring på felter vi sender i XML til oppdrag ved iverksetting 

OppdragXML-en er bygget opp av to hovednivåer: Oppdrag110 og Oppdragslinje150. På Oppdrag110-nivå er det informasjon som
gjelder hele saken, som fagsystem, sakId og hvem saken gjelder. Det kan være flere Oppdragslinje150 på hver Oppdrag110.
Oppdragslinjene inneholder informasjon om hver enkelt utbetalingsperiode. 

## Felter på Oppdrag110
| Felt                  | Type/gyldige verdier                                 | Beskrivelse                                                                                                                                                                                                        |
|:----------------------|:-----------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| kodeAksjon            | 1 eller 3                                            | Vet ikke hva dette er. Vi sender alltid 1                                                                                                                                                                          | 
| kodeEndring           | NY, ENDR, UEND                                       | Om det er et nytt oppdrag (dvs. ny sak) eller endring på et eksisterende oppdrag. Vi bruker ikke UEND (vet ikke helt hva det er/når man evt ville brukt det).                                                      | 
| kodeFagomraade        | Kode i OS for fagområdet                             | Hver ytelse har sitt eget fagområde i OS. OS har to separate fagområder for ny vedtaksløsning og Arena.                                                                                                            | 
| fagsystemId           | Maks 30 tegn string (Vi har maks 25 tegn i Utsjekk)  | ID på saken i fagsystemet, dvs. saksbehandlingsløsningen.                                                                                                                                                          | 
| utbetFrekvens         | DAG, MND, ENG ++                                     | Hvor ofte det skal utbetales på saken. For Arena-ytelsene har det ikke noe å si hva man setter i dette feltet, for det er ikke konfigurert noe kjøreplan i OS og alle perioder beregnes og betales ut umiddelbart. | 
| oppdragGjelderId      | Fødselsnummer/D-nummer eller org.nr                  | ID på personen eller organisasjonen saken gjelder.                                                                                                                                                                 | 
| datoOppdragGjelderFom | Timestamp                                            | Vet ikke hva dette er. Vi setter alltid 01.01.2000                                                                                                                                                                 | 
| saksbehId             | NAV-ident                                            | NAV-ident på saksbehandleren som har gjort behandlingen i vedtaksløsningen. Denne oppdaterer vi for hver nye utbetaling på saken                                                                                   | 
| Avstemming-115         | Kodekomponent, nokkelAvstemming og tidspktAvstemming | Informasjon som OS trenger for å plukke ut riktige oppdrag til avstemming. Vi setter både nøkkelAvstemming og tidspktAvstemming til timestampet oppdraget blir sendt. Kodekomponent er det samme som fagområde.  <br/><br/> [Se egen side om grensesnittavstemming](https://github.com/navikt/helved-utbetaling/blob/main/dokumentasjon/grensesnittavstemming.md) | 
| Avvent-118 | Liste | Se eget [avsnitt](https://github.com/navikt/helved-utbetaling/blob/main/dokumentasjon/oppdrag_xml.md#avvent-118) |
| OppdragsEnhet120      | Liste                                                | Se eget avsnitt                                                                                                                                                                                                   | 
| OppdragsLinje150      | Liste                                                | Se eget avsnitt                                                                                                                                                                                                    | 

## Felter på Oppdragslinje150

| Felt             | Type/gyldige verdier                | Beskrivelse                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
|:-----------------|:------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| kodeEndringLinje | NY eller ENDR                       | Settes til NY for nye perioder, og ENDR hvis man endrer en periode (feks. opphører)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | 
| kodeStatusLinje | OPPH                                |  Optional felt som vi bare bruker ved Opphør. Da er verdien OPPH. I andre tilfeller utelater vi dette feltet                                                    |
| kodeStatusFom     | Dato                              | Optional felt som vi bare bruker dersom `kodeStatusLinje` også er satt. Vi bruker disse feltene bare ved opphør. `kodeStatusFom` sier når `kodeStatusLinje` skal gjelde fra. Altså når opphøret skal gjelde fra.
| vedtakId         | Maks 30 tegn string                 | Denne kan settes til vedtakId, men vi setter dato for vedtaket her fordi vi ikke tar i mot både vedtakId og behandlingId fra vedtaksløsningene. Brukes nok ikke til noe i OS annet enn evt. sporing, men viktig å huske på at DVH ikke kan bruke dette feltet for å koble data.                                                                                                                                                                                                                                                                                               | 
| delytelseId      | Maks 30 tegn string                 | En unik identifikator på hver enkelt oppdragslinje. Vi bruker `<sakId>#<periodeId>` her. Vi har satt makslengde på sakId til 25 tegn i Utsjekk slik at vi har 4 tegn tilgjengelig for periodeId. Dvs at vi tillater maks 9999 utbetalingsperioder på samme sak.                                                                                                                                                                                                                                                                                                               | 
| kodeKlassifik    | Koder fastsatt av OS                | Klassifiseringskode eller klassekode. Klassekodene brukes til å indikere hvilken undergruppe av ytelsen utbetaling tilhører. De er definert i OS enten fordi ulike undergrupper skal regnskapsføres på ulike kontoer, fordi visse undergrupper skal rapporteres til A-ordningen, eller fordi man vil vise mer spesifikk informasjon til bruker i Mine Utbetalinger. (Eller andre grunner som jeg ikke vet om). Det er ofte ikke helt 1-1 mellom undergruppene i vedtaksløsningen og økonomisystemet fordi ikke alle undergrupper er interessant å skille på i økonomi-øyemed. | 
| datoVedtakFom    | Dato                                | Fom-dato på utbetalingsperioden                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | 
| datoVedtakTom    | Dato                                | Tom-dato på utbetalingsperioden                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | 
| sats             | Tall                                | Satsen som skal utbetales iht satstypen. Merk at man kan sende inn 2 desimaler/øre her, men da vil OS avrunde beløpet til hele kroner. Det er mye bedre å gjøre avrundingen i vedtaksløsningen og sende sats i hele kroner til OS, da har man sporing hele veien og full kontroll på avrundingsmetoden.                                                                                                                                                                                                                                                                       | 
| fradragTillegg   | F eller T                           | Om man skal kreve inn penger eller betale ut. Vi sender alltid T for tillegg på utbetalingene våre.                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | 
| typeSats         | DAG, MND, ENG ++                    | Det finnes flere satstyper, men akkurat nå støtter vi dagsats, månedssats og engangssats. Hvis man vil sende en annen satstype, må man spørre PO Utbetaling. Noen av de andre satsene, feks. 14-dagers sats, er ustabilt i OS selv om det er teknisk mulig å sende i grensesnittet. Merk at satstype DAG er en dagsats for mandag-fredag. Sender man perioder med lørdag eller søndag her, blir ikke beløpet for helgen med i utbetalingen.                                                                                                                                   | 
| brukKjøreplan    | J eller N                           | Ja/Nei. Vi setter alltid N for nei. Usikker på hva dette feltet egentlig styrer. Vi satt også N for alle utbetalinger på barnetrygd og enslig bortsett fra G-omregninger, selv om de ytelsene hadde kjøreplaner med månedlig utbetaling.                                                                                                                                                                                                                                                                                                                                      | 
| saksbehId        | NAV-ident                           | NAV-ident på saksbehandleren som har gjort behandlingen i vedtaksløsningen.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | 
| utbetalesTilId   | Fødselsnummer/D-nummer eller org.nr | ID på personen eller organisasjonen som skal motta denne utbetalingen. Merk at det er støtte i OS for at denne kan være ulik fra personen oppdraget gjelder. Vi støtter ikke det i Utsjekk p.t.                                                                                                                                                                                                                                                                                                                                                                               | 
| henvisning       | Maks 30 tegn string                 | Henvisning brukes for å spore hver enkelt utbetalingsperiode tilbake til hendelsen i vedtaksløsningen som trigget utbetalingen. Vi setter behandlingId i dette feltet, men kunne evt. brukt noe annet som feks vedtakId, så lenge id-en er granulær nok. Det hadde mao ikke vært OK å sette sakId i dette feltet, fordi det finnes flere hendelser/behandlinger/vedtak på samme sak.                                                                                                                                                                                          | 
| Attestant180     | NAV-ident, wrappes i et objekt      | Vi setter beslutter på vedtaket i dette feltet.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |

## OppdragsEnhet120

| Felt         | Type/gyldige verdier  | Beskrivelse                                                                                               |
|:-------------|:----------------------|:----------------------------------------------------------------------------------------------------------|
| typeEnhet    | BOS eller BEH         | Enten BOS for Bostedsenhet eller BEH for Behandlende enhet                                                |
| enhet        | 4-sifret enhetsnummer | Defaultverdien er 8020 (tror dette er enhetsnummeret til NØS)                                             |
| datoEnhetFom | Dato                  | Settes til 1.1.1970 for lokale enheter og 1.1.1900 for 8020. Begge kunne sikkert blitt satt til 1.1.1970. |

Alle oppdrag må ha OppdragsEnhet120. For de aller fleste ytelser settes denne bare til 8020 som bostedsenhet: 
```xml
<oppdrags-enhet-120>
    <typeEnhet>BOS</typeEnhet>
    <enhet>8020</enhet>
    <datoEnhetFom>1900-01-01+01:00</datoEnhetFom>
</oppdrags-enhet-120>
```

Det er imidlertid noen ytelser som må regnskapsføre utbetalingene på det lokale NAV-kontoret som brukeren tilhører. Dette refereres ofte til som rammestyrte midler og gjelder bl.a. tiltakspenger og noen av tilleggsstønadene. 
I disse tilfellene sender vi to OppdragEnhet120 til OS. Da setter vi bostedsenhet til brukerens lokale NAV-kontor, og behandlende enhet til 8020:
```xml
<oppdrags-enhet-120>
    <typeEnhet>BOS</typeEnhet>
    <enhet>0220</enhet>
    <datoEnhetFom>1970-01-01+01:00</datoEnhetFom>
</oppdrags-enhet-120>
<oppdrags-enhet-120>
    <typeEnhet>BEH</typeEnhet>
    <enhet>8020</enhet>
    <datoEnhetFom>1900-01-01+01:00</datoEnhetFom>
</oppdrags-enhet-120>
```

## Avvent-118

Avvent-funksjonen gjør det mulig å holde tilbake hele eller deler av en utbetaling. Avvent-funksjonen kun aktuell for AAP. AAP bruker avvent i følgende scenarioer:

1. Bruker har mottatt en annen folketrygdeytelse for samme tidsrom (årsakskode `AVAV` = Avvent avregning mot annen ytelse)
2. Bruker har mottatt sosialstønad i påvente av ytelse, og Nav og skal avvente et refusjonskrav fra den kommunale sosialtjenesten (årsakskode `AVRK` = Avvent refusjonskrav)
3. Bruker har mottatt støtte fra tjenestepensjonsordning i påvente av ytelse, og Nav skal avvente et refusjonskrav fra tjenestepensjonsordningen (årsakskode `AVRK` = Avvent refusjonskrav)

Når man bruker avvent-funksjonen må man angi en årsakskode. Det er enten `AVAV` (avvent avregning mot annen ytelse) eller `AVRK` (avvent refusjonskrav). Oppdrag med årsakskode `AVAV` ligger på vent til økonomilinja manuelt reaktiverer utbetalingen. `AVRK` ligger på vent 21 dager eller 42 dager, knyttet til lovbestemte frister for å kreve refusjon. Dersom krav ikke mottas innenfor fristen går betalingen ut automatisk.

### Felter på avvent-118

| Felt         | Type/gyldige verdier  | Beskrivelse                                                                                               |
|:-------------|:----------------------|:----------------------------------------------------------------------------------------------------------|
| datoAvventFom    | Dato | Fom-dato for delen av utbetalingsperioden som skal holdes tilbake |
| datoAvventTom    | Dato | Tom-dato for delen av utbetalingsperioden som skal holdes tilbake. `datoAvventFom` og `datoAvventTom` brukes altså til å angi hvilken del av en utbetalingsperiode man ønsker å avvente beregning og utbetaling for |
| datoOverfores    | Dato | Brukes til å angi hvor lenge utbetalingen skal holdes tilbake. Optional / ikke påkrevet i Utsjekk fordi oppdrag hvor man har brukt avvent noen ganger skal plukkes så fort som mulig av økonomimedarbeider, og ikke på en gitt dato  |
| kodeArsak        | AVAV, AVRK | `AVAV` betyr AVvent AVregning. `AVRK` betyr AVvent RefusjonsKrav |
| feilreg          | J,N | Optional felt som brukes for å fjerne instruksen om å utsette beregningen og utbetalingen, dersom det ble gjort ved en feil. `J` her betyr altså at oppdraget ikke skal avventes likevel |

Eksempel:
```xml
<avvent-118>
    <datoAvventFom>2025-07-01+02:00</datoAvventFom>
    <datoAvventTom>2025-07-20+02:00</datoAvventTom>
    <kodeArsak>AVAV</kodeArsak>
    <datoOverfores>2025-09-17+02:00</datoOverfores>
    <feilreg>N</feilreg>
</avvent-118>
```

## Fullstendig eksempel
TODO finn eksempel med Opphør
```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:oppdrag xmlns:ns2="http://www.trygdeetaten.no/skjema/oppdrag">
    <oppdrag-110>
        <kodeAksjon>1</kodeAksjon>
        <kodeEndring>ENDR</kodeEndring>
        <kodeFagomraade>TILTPENG</kodeFagomraade>
        <fagsystemId>202409271001</fagsystemId>
        <utbetFrekvens>MND</utbetFrekvens>
        <oppdragGjelderId>04479418840</oppdragGjelderId>
        <datoOppdragGjelderFom>2000-01-01+01:00</datoOppdragGjelderFom>
        <saksbehId>Z994363</saksbehId>
        <avstemming-115>
            <kodeKomponent>TILTPENG</kodeKomponent>
            <nokkelAvstemming>2024-10-09-16.48.46.708078</nokkelAvstemming>
            <tidspktMelding>2024-10-09-16.48.46.708078</tidspktMelding>
        </avstemming-115>
        <oppdrags-enhet-120>
            <typeEnhet>BOS</typeEnhet>
            <enhet>0220</enhet>
            <datoEnhetFom>1970-01-01+01:00</datoEnhetFom>
        </oppdrags-enhet-120>
        <oppdrags-enhet-120>
            <typeEnhet>BEH</typeEnhet>
            <enhet>8020</enhet>
            <datoEnhetFom>1900-01-01+01:00</datoEnhetFom>
        </oppdrags-enhet-120>
        <oppdrags-linje-150>
            <kodeEndringLinje>NY</kodeEndringLinje>
            <vedtakId>2024-10-09</vedtakId>
            <delytelseId>202409271001#1</delytelseId>
            <kodeKlassifik>TPTPATT</kodeKlassifik>
            <datoVedtakFom>2024-08-12+02:00</datoVedtakFom>
            <datoVedtakTom>2024-08-16+02:00</datoVedtakTom>
            <sats>285</sats>
            <fradragTillegg>T</fradragTillegg>
            <typeSats>DAG</typeSats>
            <brukKjoreplan>N</brukKjoreplan>
            <saksbehId>Z994363</saksbehId>
            <utbetalesTilId>04479418840</utbetalesTilId>
            <henvisning>DPMBNHSM5NEF69P</henvisning>
            <attestant-180>
                <attestantId>Z994127</attestantId>
            </attestant-180>
        </oppdrags-linje-150>
        <oppdrags-linje-150>
            <kodeEndringLinje>NY</kodeEndringLinje>
            <vedtakId>2024-10-09</vedtakId>
            <delytelseId>202409271001#2</delytelseId>
            <kodeKlassifik>TPTPATT</kodeKlassifik>
            <datoVedtakFom>2024-08-19+02:00</datoVedtakFom>
            <datoVedtakTom>2024-08-23+02:00</datoVedtakTom>
            <sats>285</sats>
            <fradragTillegg>T</fradragTillegg>
            <typeSats>DAG</typeSats>
            <brukKjoreplan>N</brukKjoreplan>
            <saksbehId>Z994363</saksbehId>
            <utbetalesTilId>04479418840</utbetalesTilId>
            <henvisning>DPMBNHSM5NEF69P</henvisning>
            <refFagsystemId>202409271001</refFagsystemId>
            <refDelytelseId>202409271001#1</refDelytelseId>
            <attestant-180>
                <attestantId>Z994127</attestantId>
            </attestant-180>
        </oppdrags-linje-150>
    </oppdrag-110>
</ns2:oppdrag>

```

## Relatert

https://github.com/navikt/tjenestespesifikasjoner/blob/master/nav-virksomhet-oppdragsbehandling-v1-meldingsdefinisjon/src/main/xsd/no/trygdeetaten/skjema/oppdrag/oppdragskjema-1.xsd
