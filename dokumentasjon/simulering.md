# Tolkning av respons fra simulering

## Generelt

Generelt gjelder følgende om responsen som kommer fra OS:
- Tidligere utbetalte beløp posteres med type `YTEL` (for "ytelse") og negativt beløp
- Nye beløp på ytelsen posteres med type `YTEL` og positivt beløp
- Hvis det ikke skal betales ut noe til bruker for en gitt periode, vil alle posteringene summere til 0. 

Simuleringen er delt opp i ulike _beregningsperioder_. Beregningsperiodene vil aldri spenne over flere måneder. Man kan få flere
beregningsperioder innenfor samme måned – for dagytelser kan periodene være på dagsnivå. Innenfor hver beregningsperiode har man ett eller
flere _stoppnivåer_. Man kan få flere stoppnivåer i responsen feks. i tilfeller med flere ulike mottakere på oppdragslinjene for perioden. Vi slår
sammen alle stoppnivåer i vår kode. Innenfor hvert stoppnivå er det et sett med _detaljer_ som vi kaller posteringer. 

Under følger en beskrivelse med eksempler på hvordan responsen fra simuleringstjenesten vil se ut for ulike caser.

## Ny utbetaling
Ved første utbetaling på sak er det ingenting tidligere utbetalt. Da får man kun én postering, med positivt beløp, for den nye utbetalingen: 
```json
{
  "detaljer": [
    {
      "type": "YTEL",
      "faktiskFom": "2024-09-02",
      "faktiskTom": "2024-09-02",
      "belop": 1861,
      "klassekode": "TSTBASISP4-OP"
    }
  ]
}
```

## Økning i utbetaling
Når man øker utbetalingen i en revurdering, vil man få både en negativ og en positiv postering for ytelsen i den gitte perioden, med hhv. tidligere utbetalt beløp
og nytt beløp. Summen av disse blir positiv i dette tilfellet, fordi bruker skal ha etterbetaling. Posteringene vil da se slik ut – i dette eksempelet får bruker etterbetaling på 1589 kr:
```json
{
  "detaljer": [
    {
      "type": "YTEL",
      "faktiskFom": "2024-09-02",
      "faktiskTom": "2024-09-02",
      "belop": 5000,
      "klassekode": "TSTBASISP4-OP"
    },
    {
      "type": "YTEL",
      "faktiskFom": "2024-09-02",
      "faktiskTom": "2024-09-02",
      "belop": -3411,
      "klassekode": "TSTBASISP4-OP"
    }
  ]
}
```

## Reduksjon i utbetaling
Når en tidligere utbetaling reduseres i en revurdering, vil man få en _feilutbetaling_. Feilutbetalinger vil som oftest danne grunnlag for en tilbakekrevingsbehandling
og vil dukke opp på en _kravgrunnlagskø_. Feilutbetalingene vil alltid ha en tilsvarende negativ _motpostering_ (dette er bare en teknisk regnskapsgreie i OS), og i tillegg vil det være en ekstra postering
for ytelsen som tilsvarer feilutbetalingen slik at disse posteringene summerer til 0 (hvis de ikke hadde summert til 0, ville bruker fått utbetaling). 
I eksempelet under er det tidligere utbetalt 177 kr, så reduseres utbetalingen til 74 kr, som altså resulterer i en feilutbetaling på 103 kr.
```json
{
  "detaljer": [
    {
      "type": "YTEL",
      "faktiskFom": "2024-11-18",
      "faktiskTom": "2024-11-18",
      "belop": 103,
      "klassekode": "TSTBASISP4-OP"
    },
    {
      "type": "YTEL",
      "faktiskFom": "2024-11-18",
      "faktiskTom": "2024-11-18",
      "belop": 74,
      "klassekode": "TSTBASISP4-OP"
    },
    {
      "type": "FEIL",
      "faktiskFom": "2024-11-18",
      "faktiskTom": "2024-11-18",
      "belop": 103,
      "klassekode": "KL_KODE_FEIL_ARBYT"
    },
    {
      "type": "MOTP",
      "faktiskFom": "2024-11-18",
      "faktiskTom": "2024-11-18",
      "belop": -103,
      "klassekode": "TBMOTOBS"
    },
    {
      "type": "YTEL",
      "faktiskFom": "2024-11-18",
      "faktiskTom": "2024-11-18",
      "belop": -177,
      "klassekode": "TSTBASISP4-OP"
    }
  ]
}
```

## Reduksjon og økning innenfor samme måned eller påfølgende måneder
Dette caset inntreffer dersom man flytter penger fra en dag til en annen innenfor samme måned, eller hvis man reduserer beløpet en dag og øker beløpet en annen dag innenfor
samme måned. OS vil da gjøre en justering, eller _ompostering_, slik at økningen dekker opp for reduksjonen. Dersom beløpet er netto positivt, vil bruker få en etterbetaling tilsvarende differansen,
hvis det er netto negativt, vil feilutbetalingen reduseres tilsvarende differansen på beløpene. 

OS har samme regler for justering/ompostering innenfor samme måned som i påfølgende måned. Hvis beløpet reduseres en måned og øker _neste_ måned, kan neste måneds økning brukes for å dekke inn reduksjonen. 

Dersom man har økning og reduksjon med flere måneder i mellom, eller hvis beløpet økes måneden _før_ reduksjonen inntreffer, vil ikke OS gjøre slike justeringer/omposteringer. I disse tilfellene vil man få en 
alminnelig feilutbetaling med kravgrunnlag i måneden der beløpet reduseres, og en alminnelig etterbetaling/ny utbetaling i måneden der beløpet økes. 

### Netto positivt beløp
I eksempelet under har bruker tidligere fått utbetalt 2953 kr for 5. august, men skal nå ha utbetalt 0 kr denne dagen. Samtidig får bruker en ny utbetaling på 3953 kr 20. august. Bruker har altså en netto økning på
1000 kr. Vi får en positiv justeringspostering på 2953 kr for 5. august med en tilhørende negativ justeringspostering på -2953 kr for 20. august. Utbetalingen for 20. august blir da redusert tilsvarende denne negative
justeringen. 
```json
{
  "perioder": [
    {
      "fom": "2024-08-05",
      "tom": "2024-08-05",
      "detaljer": [
        {
          "type": "FEIL",
          "faktiskFom": "2024-08-05",
          "faktiskTom": "2024-08-05",
          "belop": 2953,
          "klassekode": "KL_KODE_JUST_ARBYT"
        },
        {
          "type": "YTEL",
          "faktiskFom": "2024-08-05",
          "faktiskTom": "2024-08-05",
          "belop": -2953,
          "klassekode": "TSTBASISP4-OP"
        }
      ]
    },
    {
      "fom": "2024-08-20",
      "tom": "2024-08-20",
      "detaljer": [
        {
          "type": "FEIL",
          "faktiskFom": "2024-08-20",
          "faktiskTom": "2024-08-20",
          "belop": -2953,
          "klassekode": "KL_KODE_JUST_ARBYT"
        },
        {
          "type": "YTEL",
          "faktiskFom": "2024-08-20",
          "faktiskTom": "2024-08-20",
          "belop": 3953,
          "klassekode": "TSTBASISP4-OP"
        }
      ]
    }
  ]
}
```

### Netto negativt beløp
I eksempelet under får bruker en reduksjon fra 266 kr til 133 kr i oktober, dvs. 133 kr for mye utbetalt. I november får bruker en økning på 88 kr (fra 142 kr til 230 kr). Disse 88 kr blir _ompostert_ for å redusere
feilutbetalingen i oktober. Den gjenstående feilutbetalingen blir da på 45 kr. Merk at justeringer også har type `FEIL`, men en egen klassekode `KL_KODE_JUST_ARBYT`. Den positive justeringen i oktober har en tilhørende negativ
justering i november. 
```json
{
  "perioder": [
    {
      "fom": "2024-10-14",
      "tom": "2024-10-14",
      "detaljer": [
        {
          "type": "YTEL",
          "faktiskFom": "2024-10-14",
          "faktiskTom": "2024-10-14",
          "belop": 45,
          "klassekode": "TSTBASISP2-OP"
        },
        {
          "type": "YTEL",
          "faktiskFom": "2024-10-14",
          "faktiskTom": "2024-10-14",
          "belop": 133,
          "klassekode": "TSTBASISP2-OP"
        },
        {
          "type": "FEIL",
          "faktiskFom": "2024-10-14",
          "faktiskTom": "2024-10-14",
          "belop": 45,
          "klassekode": "KL_KODE_FEIL_ARBYT"
        },
        {
          "type": "FEIL",
          "faktiskFom": "2024-10-14",
          "faktiskTom": "2024-10-14",
          "belop": 88,
          "klassekode": "KL_KODE_JUST_ARBYT"
        },
        {
          "type": "MOTP",
          "faktiskFom": "2024-10-14",
          "faktiskTom": "2024-10-14",
          "belop": -45,
          "klassekode": "TBMOTOBS"
        },
        {
          "type": "YTEL",
          "faktiskFom": "2024-10-14",
          "faktiskTom": "2024-10-14",
          "belop": -266,
          "klassekode": "TSTBASISP2-OP"
        }
      ]
    },
    {
      "fom": "2024-11-01",
      "tom": "2024-11-01",
      "detaljer": [
        {
          "type": "FEIL",
          "faktiskFom": "2024-11-01",
          "faktiskTom": "2024-11-01",
          "belop": -88,
          "klassekode": "KL_KODE_JUST_ARBYT"
        },
        {
          "type": "YTEL",
          "faktiskFom": "2024-11-01",
          "faktiskTom": "2024-11-01",
          "belop": 230,
          "klassekode": "TSTBASISP2-OP"
        },
        {
          "type": "YTEL",
          "faktiskFom": "2024-11-01",
          "faktiskTom": "2024-11-01",
          "belop": -142,
          "klassekode": "TSTBASISP2-OP"
        }
      ]
    }
  ]
}
```
