# language: no
# encoding: UTF-8

Egenskap: Revurdering frem i tid

  Scenario: Revurdering frem i tid med beløp

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.02.2021 | 01.02.2021 | 700   |
      | 2            | 01.03.2021 | 01.03.2021 | 600   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.02.2021 | 01.02.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 2            | 01.02.2021 | 01.02.2021 | 01.02.2021  | 700   | Nei                   | Ja         | 0          |                    |
      | 2            | 01.03.2021 | 01.03.2021 |             | 600   | Nei                   | Nei        | 1          | 0                  |
