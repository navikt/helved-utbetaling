# Korrigering av utbetalinger

For én utbetalingsperiode er det i hovedsak 5 typer endringer man kan gjøre: 
- Endre beløp for hele perioden
- Endre beløp for deler av perioden
- Forlenge periode:
  - i starten
  - i slutten
  - i begge ender
- Forlenge periode:
  - i starten
  - i slutten
  - i begge ender
- Opphøre hele perioden

Vi går gjennom hvert av disse casene og ser hvordan oppragslinjene vi sender til OS ser ut. 

## Førstegangsutbetaling
Gitt en sak med id `S1` og en førstegangsbehandling med id `B1` og ferdig iverksatt utbetalingsperiode med tilhørende oppdragslinje:
```xml
<oppdrags-linje-150>
    <kodeEndringLinje>NY</kodeEndringLinje>
    <vedtakId>2024-11-03</vedtakId>
    <delytelseId>S1#0</delytelseId>
    <kodeKlassifik>DPORAS</kodeKlassifik>
    <datoVedtakFom>2024-10-01+02:00</datoVedtakFom>
    <datoVedtakTom>2024-11-01+02:00</datoVedtakTom>
    <sats>500</sats>
    <fradragTillegg>T</fradragTillegg>
    <typeSats>DAG</typeSats>
    <brukKjoreplan>N</brukKjoreplan>
    <saksbehId>S994230</saksbehId>
    <utbetalesTilId>16418245135</utbetalesTilId>
    <henvisning>B1</henvisning>
    <attestant-180>
        <attestantId>B123456</attestantId>
    </attestant-180>
</oppdrags-linje-150>
```

## Endre beløp
Når beløpet endres, kan man skrive over oppdragslinja med en ny, oppdatert oppdragslinje. Hvis vi i revurdering med id `B2` skulle endret beløpet til
1000 kr, ville ny oppdragslinje sett sånn ut:

```xml
<oppdrags-linje-150>
    <kodeEndringLinje>NY</kodeEndringLinje>
    <vedtakId>2024-11-10</vedtakId>
    <delytelseId>S1#1</delytelseId>
    <kodeKlassifik>DPORAS</kodeKlassifik>
    <datoVedtakFom>2024-10-01+02:00</datoVedtakFom>
    <datoVedtakTom>2024-11-01+02:00</datoVedtakTom>
    <sats>1000</sats>
    <fradragTillegg>T</fradragTillegg>
    <typeSats>DAG</typeSats>
    <brukKjoreplan>N</brukKjoreplan>
    <saksbehId>S994230</saksbehId>
    <utbetalesTilId>16418245135</utbetalesTilId>
    <henvisning>B2</henvisning>
    <refFagsystemId>S1</refFagsystemId>
    <refDelytelseId>S1#0</refDelytelseId>
    <attestant-180>
        <attestantId>B123456</attestantId>
    </attestant-180>
</oppdrags-linje-150>
```
Dette er altså den eneste oppdragslinjen vi trenger å sende for å endre beløpet. 

### Endre beløp for deler av perioden
Dersom vi ønsker å endre beløpet for _siste del av perioden_, gjelder det samme som over. Da kan vi sende inn én ny oppdragslinje med ny fom-dato og nytt beløp:

```xml
<oppdrags-linje-150>
    <kodeEndringLinje>NY</kodeEndringLinje>
    <vedtakId>2024-11-10</vedtakId>
    <delytelseId>S1#1</delytelseId>
    <kodeKlassifik>DPORAS</kodeKlassifik>
    <datoVedtakFom>2024-10-15+02:00</datoVedtakFom>
    <datoVedtakTom>2024-11-01+02:00</datoVedtakTom>
    <sats>1000</sats>
    <fradragTillegg>T</fradragTillegg>
    <typeSats>DAG</typeSats>
    <brukKjoreplan>N</brukKjoreplan>
    <saksbehId>S994230</saksbehId>
    <utbetalesTilId>16418245135</utbetalesTilId>
    <henvisning>B2</henvisning>
    <refFagsystemId>S1</refFagsystemId>
    <refDelytelseId>S1#0</refDelytelseId>
    <attestant-180>
        <attestantId>B123456</attestantId>
    </attestant-180>
</oppdrags-linje-150>
```

Hvis vi derimot vil endre beløpet i starten/midten av perioden og beholde opprinnelig beløp på slutten, må vi eksplisitt kommunisere det til OS.
Hvis vi feks. skal korrigere beløpet til 1000 kr fra 10.10-15.10, ville vi sendt følgende perioder til OS:

```xml
<oppdrags-linje-150>
    <kodeEndringLinje>NY</kodeEndringLinje>
    <vedtakId>2024-11-10</vedtakId>
    <delytelseId>S1#1</delytelseId>
    <kodeKlassifik>DPORAS</kodeKlassifik>
    <datoVedtakFom>2024-10-10+02:00</datoVedtakFom>
    <datoVedtakTom>2024-10-15+02:00</datoVedtakTom>
    <sats>1000</sats>
    <fradragTillegg>T</fradragTillegg>
    <typeSats>DAG</typeSats>
    <brukKjoreplan>N</brukKjoreplan>
    <saksbehId>S994230</saksbehId>
    <utbetalesTilId>16418245135</utbetalesTilId>
    <henvisning>B2</henvisning>
    <refFagsystemId>S1</refFagsystemId>
    <refDelytelseId>S1#0</refDelytelseId>
    <attestant-180>
        <attestantId>B123456</attestantId>
    </attestant-180>
</oppdrags-linje-150>
<oppdrags-linje-150>
    <kodeEndringLinje>NY</kodeEndringLinje>
    <vedtakId>2024-11-10</vedtakId>
    <delytelseId>S1#2</delytelseId>
    <kodeKlassifik>DPORAS</kodeKlassifik>
    <datoVedtakFom>2024-10-16+02:00</datoVedtakFom>
    <datoVedtakTom>2024-11-01+02:00</datoVedtakTom>
    <sats>500</sats>
    <fradragTillegg>T</fradragTillegg>
    <typeSats>DAG</typeSats>
    <brukKjoreplan>N</brukKjoreplan>
    <saksbehId>S994230</saksbehId>
    <utbetalesTilId>16418245135</utbetalesTilId>
    <henvisning>B2</henvisning>
    <refFagsystemId>S1</refFagsystemId>
    <refDelytelseId>S1#1</refDelytelseId>
    <attestant-180>
        <attestantId>B123456</attestantId>
    </attestant-180>
</oppdrags-linje-150>
```

Det generelle prinsippet som slår inn her er at hele kjeden overskrives fra endringstidspunktet. Hvis man ønsker å beholde noe av den tidligere
kjeden etter endringstidspunktet, må man derfor eksplisitt sende periodene på nytt til OS. (TODO mer om dette i eget dokument om kjeding)

## Forlenge periode

Hvis man ønsker å forlenge en periode i en revurdering, kan man følge samme prinsipp som ved endring av beløp. Her kan man sende en ny oppdragslinje
med ny fom-/tom-dato som overskriver den tidligere perioden. Hvis man i revurdering `B2` vil forlenge perioden fra 01.11 til 15.11, kan man sende følgende
oppdragslinje til OS:
```xml
<oppdrags-linje-150>
    <kodeEndringLinje>NY</kodeEndringLinje>
    <vedtakId>2024-11-10</vedtakId>
    <delytelseId>S1#1</delytelseId>
    <kodeKlassifik>DPORAS</kodeKlassifik>
    <datoVedtakFom>2024-10-01+02:00</datoVedtakFom>
    <datoVedtakTom>2024-11-15+02:00</datoVedtakTom>
    <sats>500</sats>
    <fradragTillegg>T</fradragTillegg>
    <typeSats>DAG</typeSats>
    <brukKjoreplan>N</brukKjoreplan>
    <saksbehId>S994230</saksbehId>
    <utbetalesTilId>16418245135</utbetalesTilId>
    <henvisning>B2</henvisning>
    <refFagsystemId>S1</refFagsystemId>
    <refDelytelseId>S1#0</refDelytelseId>
    <attestant-180>
        <attestantId>B123456</attestantId>
    </attestant-180>
</oppdrags-linje-150>
```
Hvis man ønsker å forlenge en periode bakover, dvs. sette en tidligere fom-dato, kan man gjøre det på akkurat samme måte. Hvis vi feks.
ville startet utbetalingsperioden 15.09, kunne vi sendt inn en identisk oppdragslinje som den over med nye datoer: 
```xml
<datoVedtakFom>2024-09-15+02:00</datoVedtakFom>
<datoVedtakTom>2024-11-01+02:00</datoVedtakTom>
```
Vi kunne selvfølgelig også forlenget oppdragslinjen i begge ender og oppdatert både fom- og tom-dato – Det fungerer på akkurat samme måte.

### Viktig å huske på om forlenging av periode
Når man forlenger en periode fremover og/eller bakover, må man huske at man overskriver hele kjeden for den nye perioden. I eksempelet over,
hvis man hadde hatt andre utbetalingsperioder i kjeden mellom 15.09 og 01.10 og mellom 01.11 til 15.11, ville disse blitt overskrevet med nytt beløp
for den perioden vi forlenger. 

## Forkorte periode

### I slutten
Hvis man ønsker å forkorte en periode i slutten av perioden, setter man opphør på oppdragslinja med fom-dato til den første dagen i perioden som
ikke lenger skal ha utbetaling. Hvis man i samme eksempel som over skulle forkortet perioden fra 01.10-01.11 til 01.10-15.10, ville man sendt følgende 
oppdragslinje til OS:
```xml
<oppdrags-linje-150>
    <kodeEndringLinje>ENDR</kodeEndringLinje>
    <kodeStatusLinje>OPPH</kodeStatusLinje>
    <datoStatusFom>2024-10-16+02:00</datoStatusFom>
    <vedtakId>2024-11-03</vedtakId>
    <delytelseId>S1#0</delytelseId>
    <kodeKlassifik>DPORAS</kodeKlassifik>
    <datoVedtakFom>2024-10-01+02:00</datoVedtakFom>
    <datoVedtakTom>2024-11-01+02:00</datoVedtakTom>
    <sats>500</sats>
    <fradragTillegg>T</fradragTillegg>
    <typeSats>DAG</typeSats>
    <brukKjoreplan>N</brukKjoreplan>
    <saksbehId>S994230</saksbehId>
    <utbetalesTilId>16418245135</utbetalesTilId>
    <henvisning>B1</henvisning>
    <attestant-180>
        <attestantId>B123456</attestantId>
    </attestant-180>
</oppdrags-linje-150>
```
Merk at vi ikke sender en _ny_ oppdragslinje her, men sender en _endring_ på den opprinnelige linjen. Vi kunne nok i prinsippet
sendt en ny, forkortet linje med oppdatert tom-dato, og det ville blitt tolket/beregnet på akkurat samme måte i OS. Det går evt. an å 
implementere og teste (feks. via simulering). 

### I starten
Hvis man ønsker å forkorte perioden i starten, følger det samme struktur med at man setter opphør på perioden. Man må da sende en ny
oppdragslinje for den delen av perioden man ønsker å beholde. I fortsatt samme eksempel, hvis man skulle forkortet perioden fra 01.10-01.11 til
15.10 til 01.11, ville man sendt følgende oppdragslinjer til OS:
```xml
<oppdrags-linje-150>
    <kodeEndringLinje>ENDR</kodeEndringLinje>
    <kodeStatusLinje>OPPH</kodeStatusLinje>
    <datoStatusFom>2024-10-01+02:00</datoStatusFom>
    <vedtakId>2024-11-03</vedtakId>
    <delytelseId>S1#0</delytelseId>
    <kodeKlassifik>DPORAS</kodeKlassifik>
    <datoVedtakFom>2024-10-01+02:00</datoVedtakFom>
    <datoVedtakTom>2024-11-01+02:00</datoVedtakTom>
    <sats>500</sats>
    <fradragTillegg>T</fradragTillegg>
    <typeSats>DAG</typeSats>
    <brukKjoreplan>N</brukKjoreplan>
    <saksbehId>S994230</saksbehId>
    <utbetalesTilId>16418245135</utbetalesTilId>
    <henvisning>B1</henvisning>
    <attestant-180>
        <attestantId>B123456</attestantId>
    </attestant-180>
</oppdrags-linje-150>
<oppdrags-linje-150>
    <kodeEndringLinje>NY</kodeEndringLinje>
    <vedtakId>2024-11-10</vedtakId>
    <delytelseId>S1#1</delytelseId>
    <kodeKlassifik>DPORAS</kodeKlassifik>
    <datoVedtakFom>2024-10-15+02:00</datoVedtakFom>
    <datoVedtakTom>2024-11-01+02:00</datoVedtakTom>
    <sats>500</sats>
    <fradragTillegg>T</fradragTillegg>
    <typeSats>DAG</typeSats>
    <brukKjoreplan>N</brukKjoreplan>
    <saksbehId>S994230</saksbehId>
    <utbetalesTilId>16418245135</utbetalesTilId>
    <henvisning>B2</henvisning>
    <refFagsystemId>S1</refFagsystemId>
    <refDelytelseId>S1#0</refDelytelseId>
    <attestant-180>
        <attestantId>B123456</attestantId>
    </attestant-180>
</oppdrags-linje-150>
```

### I begge ender
Hvis man vil forkorte en periode både i starten og slutten, kan man følge samme fremgangsmåte som når man forkorter
perioden i starten. Den nye perioden kan da inneholde både ny fom-dato og ny tom-dato. Hvis vi skal fortsette eksempelet over men
forkorte perioden fra 01.10-01.11 til 15.10-20.10, kunne vi i den nye oppdragslinja bare satt oppdatert tom-dato:

```xml
<datoVedtakFom>2024-10-15+02:00</datoVedtakFom>
<datoVedtakTom>2024-10-20+02:00</datoVedtakTom>
```

## Opphøre hele perioden
Hvis man vil opphøre hele perioden, sender man et opphør på oppdragslinja med fom-dato til første dato i perioden:
```xml
<oppdrags-linje-150>
    <kodeEndringLinje>ENDR</kodeEndringLinje>
    <kodeStatusLinje>OPPH</kodeStatusLinje>
    <datoStatusFom>2024-10-01+02:00</datoStatusFom>
    <vedtakId>2024-11-03</vedtakId>
    <delytelseId>S1#0</delytelseId>
    <kodeKlassifik>DPORAS</kodeKlassifik>
    <datoVedtakFom>2024-10-01+02:00</datoVedtakFom>
    <datoVedtakTom>2024-11-01+02:00</datoVedtakTom>
    <sats>500</sats>
    <fradragTillegg>T</fradragTillegg>
    <typeSats>DAG</typeSats>
    <brukKjoreplan>N</brukKjoreplan>
    <saksbehId>S994230</saksbehId>
    <utbetalesTilId>16418245135</utbetalesTilId>
    <henvisning>B1</henvisning>
    <attestant-180>
        <attestantId>B123456</attestantId>
    </attestant-180>
</oppdrags-linje-150>
```
