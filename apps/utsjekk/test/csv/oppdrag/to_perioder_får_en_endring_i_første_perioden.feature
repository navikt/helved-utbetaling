# language: no
# encoding: UTF-8

Egenskap: 2 perioder med endring på en av periodene

  Scenario: Har 2 perioder og får en endring i første periode

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.02.2021 | 30.04.2021 | 700   |
      | 1            | 01.05.2021 | 31.07.2021 | 900   |
      | 2            | 01.02.2021 | 31.03.2021 | 700   |
      | 2            | 01.05.2021 | 31.07.2021 | 900   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.02.2021 | 30.04.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 1            | 01.05.2021 | 31.07.2021 |             | 900   | Ja                    | Nei        | 1          | 0                  |
      | 2            | 01.05.2021 | 31.07.2021 | 01.04.2021  | 900   | Nei                   | Ja         | 1          | 0                  |
      | 2            | 01.05.2021 | 31.07.2021 |             | 900   | Nei                   | Nei        | 2          | 1                  |
