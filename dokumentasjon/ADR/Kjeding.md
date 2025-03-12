# ADR: "Mikrokjeding" innenfor en utbetalingsperiode, men ikke på tvers av perioder

## Status

Akseptert

## Kontekst

Kjeding er en sammenkobling av utbetalingsperioder (oppdragslinjer i OS) som blant annet gjør at flere kan korrigeres i én operasjon mot Oppdragssystemet (OS). Men det gjør også at utbetalinger av nyere dato enn den som endres, på samme kjede, må følge med på nytt for å bekrefte at de fortsatt er gyldige. Ellers vil de opphøres (“angres” og settes til null).

Ved kjeding knytter man sammen utbetalingslinjer gjennom å referere til tidligere linjer. Da må man holde styr på hvilke linjer man har, og ved en endring må man passe på at alle linjer fortsatt er riktige. Ved feil risikerer man tilbakekreving. For å få til dette kreves kompleks kode. Dette gjøres i dag av [utbetalingsgeneratoren](https://github.com/navikt/utsjekk/blob/main/src/main/kotlin/no/nav/utsjekk/iverksetting/utbetalingsoppdrag/Utbetalingsgenerator.kt). Den sammenligner sannheten systemet kjenner fra før med det nye som sendes inn, og utleder basert på det hva som skal gjøres.

[Les mer om kjeding](https://github.com/navikt/helved-utbetaling/blob/main/dokumentasjon/kjeding.md) og hvordan det fungerer i dokumentasjonen.

## Alternativer vurdert

1. Skrive om [Utbetalingsgenerator](https://github.com/navikt/utsjekk/blob/main/src/main/kotlin/no/nav/utsjekk/iverksetting/utbetalingsoppdrag/Utbetalingsgenerator.kt)
2. Gå bort fra kjeding og kun sende en utbetalingslinje om gangen
3. Tillate kjeding, men kun ved revurdering (endring) og ikke ved oppretting av en utbetaling


## Beslutning

* Alternativ 1 har risko for å ende opp like kompleks som dagens løsning.
* Alternativ 2 kan føre til mange 0 utbetalinger. Ved korrigering får man veldig mange kall gjennom tjenestene våre, det fører også til merarbeid for ønonomi-medarbeider. Det vil føre til den enkleste koden. 
* Alternativ 3 vil forenkle koden betraktelig og kan lage et sterkere typet grensesnitt, som man også ville fått i alternativ 2. Skjermer konsumentene fra økonomidomene.

Vi går for alternativ 3 etter en vellykket POC. 

## Konsekvenser
Pros:
* Å gå vekk fra kjeding på tvers av utbetalinger muliggjør et enklere og mer intuitivt API-design
* Koden blir forenklet og enklere å sette seg inn i for nye utviklere
* Grensesnittet blir mer entydig og det blir mindre rom for feil

Cons:
* Konsument må holde styr på egne perioder / utbetalinger (flere utbetalinger kan overlappe i tid)
* Vi må migrere eksisterende data i prod

## Referanser:

-