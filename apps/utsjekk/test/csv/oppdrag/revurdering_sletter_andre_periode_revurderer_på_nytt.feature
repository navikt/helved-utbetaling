# language: no
# encoding: UTF-8

Egenskap: Revurderer og sletter periode 2, revurdererer på nytt og skal da bygge videre fra andre perioden sin periodeId


  Scenario: Revurderer og sletter periode 2, revurdererer på nytt og skal da bygge videre fra andre perioden sin periodeId

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.03.2021 | 01.03.2021 | 700   |
      | 1            | 01.04.2021 | 01.04.2021 | 800   |
      | 2            | 01.03.2021 | 01.03.2021 | 700   |
      | 3            | 01.03.2021 | 01.03.2021 | 700   |
      | 3            | 01.04.2021 | 01.04.2021 | 800   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 01.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 1            | 01.04.2021 | 01.04.2021 |             | 800   | Ja                    | Nei        | 1          | 0                  |
      | 2            | 01.04.2021 | 01.04.2021 | 01.04.2021  | 800   | Nei                   | Ja         | 1          | 0                  |
      | 3            | 01.04.2021 | 01.04.2021 |             | 800   | Nei                   | Nei        | 2          | 1                  |
