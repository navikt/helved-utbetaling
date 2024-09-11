# language: no
# encoding: UTF-8

Egenskap: Korrigere beløp tilbake i tid

  Scenario: Beløp endres tilbake i tid. Da skal vi ikke sende opphør, kun endret periode med nytt beløp som vil skrive
  over den eksisterende oppdragslinjen

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 02.06.2023 | 15.08.2023 | 800   |
      | 2            | 02.06.2023 | 15.07.2023 | 800   |
      | 2            | 16.07.2023 | 15.08.2023 | 400   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 02.06.2023 | 15.08.2023 |             | 800   | Ja                    | Nei        | 0          |                    |
      | 2            | 16.07.2023 | 15.08.2023 |             | 400   | Nei                   | Nei        | 1          | 0                  |

  Scenario: Endrer beløp fra en gitt dato for utbetaling med månedssats

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp | Satstype |
      | 1            | 01.06.2023 | 31.08.2023 | 800   | MÅNEDLIG |
      | 2            | 01.06.2023 | 31.07.2023 | 800   | MÅNEDLIG |
      | 2            | 01.08.2023 | 31.08.2023 | 400   | MÅNEDLIG |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id | Satstype |
      | 1            | 01.06.2023 | 31.08.2023 |             | 800   | Ja                    | Nei        | 0          |                    | MÅNEDLIG |
      | 2            | 01.08.2023 | 31.08.2023 |             | 400   | Nei                   | Nei        | 1          | 0                  | MÅNEDLIG |

  Scenario: Endrer beløp for en hel periode med månedssats

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp | Satstype |
      | 1            | 01.06.2023 | 31.08.2023 | 800   | MÅNEDLIG |
      | 2            | 01.06.2023 | 31.08.2023 | 400   | MÅNEDLIG |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id | Satstype |
      | 1            | 01.06.2023 | 31.08.2023 |             | 800   | Ja                    | Nei        | 0          |                    | MÅNEDLIG |
      | 2            | 01.06.2023 | 31.08.2023 |             | 400   | Nei                   | Nei        | 1          | 0                  | MÅNEDLIG |
