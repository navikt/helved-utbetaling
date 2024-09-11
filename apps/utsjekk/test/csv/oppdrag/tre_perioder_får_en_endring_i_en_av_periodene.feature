# language: no
# encoding: UTF-8

Egenskap: 3 perioder og får endring i en av periodene


  Scenario: Har 3 perioder og får en endring i den første perioden

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.02.2021 | 30.04.2021 | 700   |
      | 1            | 01.05.2021 | 31.07.2021 | 900   |
      | 1            | 01.08.2021 | 31.10.2021 | 1000  |
      | 2            | 01.02.2021 | 30.04.2021 | 500   |
      | 2            | 01.05.2021 | 31.07.2021 | 900   |
      | 2            | 01.08.2021 | 31.10.2021 | 1000  |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.02.2021 | 30.04.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 1            | 01.05.2021 | 31.07.2021 |             | 900   | Ja                    | Nei        | 1          | 0                  |
      | 1            | 01.08.2021 | 31.10.2021 |             | 1000  | Ja                    | Nei        | 2          | 1                  |
      | 2            | 01.02.2021 | 30.04.2021 |             | 500   | Nei                   | Nei        | 3          | 2                  |
      | 2            | 01.05.2021 | 31.07.2021 |             | 900   | Nei                   | Nei        | 4          | 3                  |
      | 2            | 01.08.2021 | 31.10.2021 |             | 1000  | Nei                   | Nei        | 5          | 4                  |

  Scenario: Har 3 perioder og får en endring i den andre perioden

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.02.2021 | 30.04.2021 | 700   |
      | 1            | 01.05.2021 | 31.07.2021 | 900   |
      | 1            | 01.08.2021 | 31.10.2021 | 1000  |
      | 2            | 01.02.2021 | 30.04.2021 | 700   |
      | 2            | 01.05.2021 | 31.07.2021 | 800   |
      | 2            | 01.08.2021 | 31.10.2021 | 1000  |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.02.2021 | 30.04.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 1            | 01.05.2021 | 31.07.2021 |             | 900   | Ja                    | Nei        | 1          | 0                  |
      | 1            | 01.08.2021 | 31.10.2021 |             | 1000  | Ja                    | Nei        | 2          | 1                  |
      | 2            | 01.05.2021 | 31.07.2021 |             | 800   | Nei                   | Nei        | 3          | 2                  |
      | 2            | 01.08.2021 | 31.10.2021 |             | 1000  | Nei                   | Nei        | 4          | 3                  |
