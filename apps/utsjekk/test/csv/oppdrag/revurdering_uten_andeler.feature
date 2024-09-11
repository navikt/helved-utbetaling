# language: no
# encoding: UTF-8

Egenskap: Revurdering uten andeler beholder periodeId til neste behandling med periode med beløp


  Scenario: Revurdering uten andeler beholder periodeId til neste behandling med periode med beløp

    Gitt følgende tilkjente ytelser
      | BehandlingId | Uten andeler | Fra dato   | Til dato   | Beløp |
      | 1            |              | 01.02.2021 | 01.02.2021 | 700   |
      | 2            | Ja           | 01.02.2021 | 01.02.2021 |       |
      | 3            |              | 01.02.2021 | 01.02.2021 | 700   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.02.2021 | 01.02.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 2            | 01.02.2021 | 01.02.2021 | 01.02.2021  | 700   | Nei                   | Ja         | 0          |                    |
      | 3            | 01.02.2021 | 01.02.2021 |             | 700   | Nei                   | Nei        | 1          | 0                  |