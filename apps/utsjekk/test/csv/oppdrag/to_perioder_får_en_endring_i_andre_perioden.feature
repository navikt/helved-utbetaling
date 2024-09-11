# language: no
# encoding: UTF-8

Egenskap: 2 perioder med endring på en av periodene

  Scenariomal: Har 2 perioder og får en endring på andre perioden

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 05.02.2021 | 16.04.2021 | 700   |
      | 1            | 17.04.2021 | 21.07.2021 | 900   |
      | 2            | 05.02.2021 | 16.04.2021 | 700   |
      | 2            | <Fra dato> | 30.06.2021 | 900   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 05.02.2021 | 16.04.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 1            | 17.04.2021 | 21.07.2021 |             | 900   | Ja                    | Nei        | 1          | 0                  |
      | 2            | 17.04.2021 | 21.07.2021 | 17.04.2021  | 900   | Nei                   | Ja         | 1          | 0                  |
      | 2            | <Fra dato> | 30.06.2021 |             | 900   | Nei                   | Nei        | 2          | 1                  |

    Eksempler:
      | Fra dato   |
      | 03.05.2021 |
      | 04.06.2021 |