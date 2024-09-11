# language: no
# encoding: UTF-8

Egenskap: Opphør

  Scenario: Opphør en periode

    Gitt følgende tilkjente ytelser
      | BehandlingId | Uten andeler | Fra dato   | Til dato   | Beløp |
      | 1            |              | 01.03.2021 | 01.03.2021 | 700   |
      | 2            | Ja           |            |            |       |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 01.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 2            | 01.03.2021 | 01.03.2021 | 01.03.2021  | 700   | Nei                   | Ja         | 0          |                    |

  Scenario: Opphør en periode med månedssats

    Gitt følgende tilkjente ytelser
      | BehandlingId | Uten andeler | Fra dato   | Til dato   | Beløp | Satstype |
      | 1            |              | 01.03.2021 | 31.03.2021 | 700   | MÅNEDLIG |
      | 2            | Ja           |            |            |       | MÅNEDLIG |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id | Satstype |
      | 1            | 01.03.2021 | 31.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    | MÅNEDLIG |
      | 2            | 01.03.2021 | 31.03.2021 | 01.03.2021  | 700   | Nei                   | Ja         | 0          |                    | MÅNEDLIG |

  Scenario: Opphør en av 2 perioder

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.03.2021 | 01.03.2021 | 700   |
      | 1            | 01.04.2021 | 01.04.2021 | 800   |
      | 2            | 01.03.2021 | 01.03.2021 | 700   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 01.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 1            | 01.04.2021 | 01.04.2021 |             | 800   | Ja                    | Nei        | 1          | 0                  |
      | 2            | 01.04.2021 | 01.04.2021 | 01.04.2021  | 800   | Nei                   | Ja         | 1          | 0                  |

  Scenario: Opphører den siste av to perioder

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.03.2021 | 31.03.2021 | 700   |
      | 1            | 01.04.2021 | 30.04.2021 | 800   |
      | 2            | 01.03.2021 | 31.03.2021 | 700   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 31.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 1            | 01.04.2021 | 30.04.2021 |             | 800   | Ja                    | Nei        | 1          | 0                  |
      | 2            | 01.04.2021 | 30.04.2021 | 01.04.2021  | 800   | Nei                   | Ja         | 1          | 0                  |

  Scenario: Opphører en lang periode

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.03.2021 | 01.06.2021 | 700   |
      | 2            | 01.03.2021 | 01.04.2021 | 700   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 01.06.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 2            | 01.03.2021 | 01.06.2021 | 02.04.2021  | 700   | Nei                   | Ja         | 0          |                    |

  Scenario: Opphører første måned av en lang periode

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.03.2021 | 01.06.2021 | 700   |
      | 2            | 01.04.2021 | 01.06.2021 | 700   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 01.06.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 2            | 01.03.2021 | 01.06.2021 | 01.03.2021  | 700   | Nei                   | Ja         | 0          |                    |
      | 2            | 01.04.2021 | 01.06.2021 |             | 700   | Nei                   | Nei        | 1          | 0                  |

  Scenario: Opphører en lang periode med månedssats

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp | Satstype |
      | 1            | 01.03.2021 | 30.06.2021 | 700   | MÅNEDLIG |
      | 2            | 01.03.2021 | 31.03.2021 | 700   | MÅNEDLIG |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id | Satstype |
      | 1            | 01.03.2021 | 30.06.2021 |             | 700   | Ja                    | Nei        | 0          |                    | MÅNEDLIG |
      | 2            | 01.03.2021 | 30.06.2021 | 01.04.2021  | 700   | Nei                   | Ja         | 0          |                    | MÅNEDLIG |

  Scenario: Opphør en tidligere periode da vi kun har med den andre av 2 perioder

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.03.2021 | 01.03.2021 | 700   |
      | 1            | 01.04.2021 | 01.04.2021 | 700   |
      | 2            | 01.04.2021 | 01.04.2021 | 700   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 01.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 1            | 01.04.2021 | 01.04.2021 |             | 700   | Ja                    | Nei        | 1          | 0                  |
      | 2            | 01.04.2021 | 01.04.2021 | 01.03.2021  | 700   | Nei                   | Ja         | 1          | 0                  |
      | 2            | 01.04.2021 | 01.04.2021 |             | 700   | Nei                   | Nei        | 2          | 1                  |

  Scenario: Opphører den første av to perioder

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp | Satstype |
      | 1            | 01.03.2021 | 31.03.2021 | 700   | MÅNEDLIG |
      | 1            | 01.04.2021 | 30.04.2021 | 700   | MÅNEDLIG |
      | 2            | 01.04.2021 | 30.04.2021 | 700   | MÅNEDLIG |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id | Satstype |
      | 1            | 01.03.2021 | 31.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    | MÅNEDLIG |
      | 1            | 01.04.2021 | 30.04.2021 |             | 700   | Ja                    | Nei        | 1          | 0                  | MÅNEDLIG |
      | 2            | 01.04.2021 | 30.04.2021 | 01.03.2021  | 700   | Nei                   | Ja         | 1          | 0                  | MÅNEDLIG |
      | 2            | 01.04.2021 | 30.04.2021 |             | 700   | Nei                   | Nei        | 2          | 1                  | MÅNEDLIG |

  Scenario: Opphører den første av to perioder der det er tid i mellom periodene

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp | Satstype |
      | 1            | 01.03.2021 | 31.03.2021 | 700   | MÅNEDLIG |
      | 1            | 01.06.2021 | 30.06.2021 | 700   | MÅNEDLIG |
      | 2            | 01.06.2021 | 30.06.2021 | 700   | MÅNEDLIG |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id | Satstype |
      | 1            | 01.03.2021 | 31.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    | MÅNEDLIG |
      | 1            | 01.06.2021 | 30.06.2021 |             | 700   | Ja                    | Nei        | 1          | 0                  | MÅNEDLIG |
      | 2            | 01.06.2021 | 30.06.2021 | 01.03.2021  | 700   | Nei                   | Ja         | 1          | 0                  | MÅNEDLIG |
      | 2            | 01.06.2021 | 30.06.2021 |             | 700   | Nei                   | Nei        | 2          | 1                  | MÅNEDLIG |

  Scenario: Opphører den siste av to perioder der det er tid i mellom periodene

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp | Satstype |
      | 1            | 01.03.2021 | 31.03.2021 | 700   | MÅNEDLIG |
      | 1            | 01.06.2021 | 30.06.2021 | 700   | MÅNEDLIG |
      | 2            | 01.03.2021 | 31.03.2021 | 700   | MÅNEDLIG |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id | Satstype |
      | 1            | 01.03.2021 | 31.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    | MÅNEDLIG |
      | 1            | 01.06.2021 | 30.06.2021 |             | 700   | Ja                    | Nei        | 1          | 0                  | MÅNEDLIG |
      | 2            | 01.06.2021 | 30.06.2021 | 01.06.2021  | 700   | Nei                   | Ja         | 1          | 0                  | MÅNEDLIG |
