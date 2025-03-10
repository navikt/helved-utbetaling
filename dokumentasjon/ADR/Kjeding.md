# ADR: "Mikrokjeding" innenfor en utbetalingsperiode, men ikke på tvers av perioder

## Status

Utkast

## Kontekst

Kjeding er tradisjonelt sett en sammenkobling av utbetalinger som blant annet gjør at flere kan korrigeres i én operasjon mot Oppdragssystemet (OS). Men det gjør også at utbetalinger av nyere dato enn den som endres, på samme kjede, må følge med på nytt for å bekrefte at de fortsatt er gyldige. Ellers vil de opphøres (“angres” og settes til null).

Ved kjeding knytter man sammen utbetalingslinjer gjennom å referere til tidligere linjer. Da må man holde styr på hvilke linjer man har, og ved en endring må man passe på at alle linjer fortsatt er riktige. Ved feil risikerer man tilbakekreving. For å få til dette kreves kompleks kode. Dette gjøres i dag av [utbetalingsgeneratoren](https://github.com/navikt/utsjekk/blob/main/src/main/kotlin/no/nav/utsjekk/iverksetting/utbetalingsoppdrag/Utbetalingsgenerator.kt). Den sammenligner sannheten systemet kjenner fra før med det nye som sendes inn, og utleder basert på det hva som skal gjøres.


Todo:
Å gå vekk fra kjeding muliggjør nytt API-design
Hvordan eksisterende kjeder migreres / håndteres
Konsument må holde styr på egne perioder / utbetalinger (overlapp, unngå utilsikta doble utbetalinger)
Korrigere / endre en ressurs om gangen i separate kall


## Alternativer vurdert

< Valgfri seksjon - med liste med alternativer >

1. Skrive om [Utbetalingsgenerator](https://github.com/navikt/utsjekk/blob/main/src/main/kotlin/no/nav/utsjekk/iverksetting/utbetalingsoppdrag/Utbetalingsgenerator.kt)
2. Gå bort fra kjeding og kun sende en utbetalingslinje om gangen
3. Tillate kjeding, men kun ved revurdering (endring) og ikke ved oppretting av en utbetaling


## Beslutning

< Hvilke(n) endring(er) foreslås som svar på utfordringen? >

## Konsekvenser

< Hva blir situasjonen etter at denne beslutningen eventuelt er tatt? Hva blir enklere eller vanskeligere på bakgrunn av dette? (Både positive og negative konsekvenser beskrives.) >

## Referanser:

-