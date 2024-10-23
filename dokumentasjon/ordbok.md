# Ordbok

### Oppdrag
En melding til OS for en utbetaling som skal iverksettes. Utsjekk sender et oppdrag til OS hver gang den mottar en iverksetting
fra en vedtaksløsning, så lenge iverksettingen inneholder en ny eller endret utbetaling.

### Oppdragslinje
OS sitt begrep for en utbetalingsperiode. Et oppdrag kan inneholde mange oppdragslinjer.

### Klassekode
Kalles også klassifiseringskode. En klassekode sendes med på alle utbetalingsperioder/oppdragslinjer til OS. Hver ytelse har et sett med
klassekoder, og ofte skiller deles utbetalingen opp i ulike klassekoder fordi det skal regnskapsføres på ulike _kontoer_ i NAVs regnskap.
Det kan også være flere klassifiseringskoder på grunn av andre behov, eksempelvis at noen typer utbetalinger skal rapporteres til A-ordningen
og andre ikke, eller at man ønsker mer detaljert informasjon på kontoutskrift eller Mine Utbetalinger utover hvilken ytelse det gjelder. 
Klassekodene har ofte en logisk sammenheng med ulike undergrupper for den gitte ytelsen. For eksempel har tiltakspenger en klassekode for hver
tiltakstype, og dagpenger har en klassekode for hver _rettighetsgruppe_ (arbeidssøker, permittert etc). 

Klassekodene har som oftest ikke noe meningsbærende navn, men noen av dem ender med IOP. IOP står for ikke oppgavepliktig, og kan f.eks. bety
at utbetalinger på klassekoden ikke skal rapporteres til A-ordningen.

### Rammestyrte midler
Noen ytelser er rammestyrte midler for NAV. Dette betyr at ytelsen knyttes til brukers lokale NAV-kontor, og hvert NAV-kontor må føre regnskap
for hvor mye penger de bruker hvert år på ytelsen. De har gjerne en viss ramme/budsjett som de ikke kan overstige. For ytelser med rammestyrte midler
må vi sende med brukers lokale NAV-kontor på alle utbetalinger slik at regnskapet blir riktig. Hvis bruker flytter, benytter vi alltid det nyeste
NAV-kontoret på utbetalingene uavhengig av om det er en ny utbetaling eller en korrigering tilbake i tid.

### Feilutbetaling
En utbetalingsperiode som allerede er utbetalt, som så korrigeres, kan føre til en feilutbetaling. Feilutbetaling kan i noen kontekster referere til både for mye utbetalt og 
for lite utbetalt, men stort sett (eksempelvis i kontekst av simulering) betyr feilutbetaling at det har blitt utbetalt for mye. Noen feilutbetalinger fører til tilbakekreving,
men ikke alle. Eksempelvis kan en feilutbetaling motregnes mot en utbetaling på en annen ytelse.

### Etterbetaling
En utbetaling som har blitt betalt ut til bruker, men som i ettertid korrigeres, fører til en etterbetaling hvis bruker skulle hatt et høyere beløp utbetalt. Bruker får da
en etterbetaling med det resterende beløpet. 
