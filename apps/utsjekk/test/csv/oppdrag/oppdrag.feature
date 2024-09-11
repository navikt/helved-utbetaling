# language: no
# encoding: UTF-8

Egenskap: Vedtak for førstegangsbehandling


  Scenario: Vedtak med en periode

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.03.2021 | 01.03.2021 | 700   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id | Satstype |
      | 1            | 01.03.2021 | 01.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    | DAGLIG   |


  Scenario: Revurdering uten endring av andeler

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.03.2021 | 01.03.2021 | 700   |
      | 1            | 01.04.2021 | 01.04.2021 | 700   |
      | 2            | 01.03.2021 | 01.03.2021 | 700   |
      | 2            | 01.04.2021 | 01.04.2021 | 700   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 01.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 1            | 01.04.2021 | 01.04.2021 |             | 700   | Ja                    | Nei        | 1          | 0                  |


  Scenario: Vedtak med to perioder

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.03.2021 | 01.03.2021 | 700   |
      | 1            | 01.04.2021 | 01.05.2021 | 800   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 01.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 1            | 01.04.2021 | 01.05.2021 |             | 800   | Ja                    | Nei        | 1          | 0                  |


  Scenario: Revurdering som legger til en periode, simulering skal opphøre fra start for å kunne vise all historikk

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.03.2021 | 01.03.2021 | 700   |
      | 2            | 01.03.2021 | 01.03.2021 | 700   |
      | 2            | 01.04.2021 | 01.04.2021 | 800   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 01.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 2            | 01.04.2021 | 01.04.2021 |             | 800   | Nei                   | Nei        | 1          | 0                  |


  Scenario: 2 revurderinger som legger til en periode

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.03.2021 | 01.03.2021 | 700   |
      | 2            | 01.03.2021 | 01.03.2021 | 700   |
      | 2            | 01.04.2021 | 01.04.2021 | 800   |
      | 3            | 01.03.2021 | 01.03.2021 | 700   |
      | 3            | 01.04.2021 | 01.04.2021 | 800   |
      | 3            | 01.05.2021 | 01.05.2021 | 900   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 01.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 2            | 01.04.2021 | 01.04.2021 |             | 800   | Nei                   | Nei        | 1          | 0                  |
      | 3            | 01.05.2021 | 01.05.2021 |             | 900   | Nei                   | Nei        | 2          | 1                  |


  Scenario: Endrer beløp fra 2. mars

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.03.2021 | 01.06.2021 | 700   |
      | 2            | 01.03.2021 | 01.03.2021 | 700   |
      | 2            | 02.03.2021 | 01.06.2021 | 800   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 01.06.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 2            | 02.03.2021 | 01.06.2021 |             | 800   | Nei                   | Nei        | 1          | 0                  |


  Scenario: Første perioden blir avkortet, og den andre er lik

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.03.2021 | 01.04.2021 | 700   |
      | 1            | 01.05.2021 | 01.07.2021 | 700   |
      | 2            | 01.03.2021 | 15.03.2021 | 700   |
      | 2            | 01.05.2021 | 01.07.2021 | 700   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 01.04.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 1            | 01.05.2021 | 01.07.2021 |             | 700   | Ja                    | Nei        | 1          | 0                  |
      | 2            | 01.05.2021 | 01.07.2021 | 16.03.2021  | 700   | Nei                   | Ja         | 1          | 0                  |
      | 2            | 01.05.2021 | 01.07.2021 |             | 700   | Nei                   | Nei        | 2          | 1                  |

  Scenario: Endrer beløp fra start

    Gitt følgende tilkjente ytelser
      | BehandlingId | Fra dato   | Til dato   | Beløp |
      | 1            | 01.03.2021 | 01.06.2021 | 700   |
      | 2            | 01.03.2021 | 01.03.2021 | 800   |
      | 2            | 01.04.2021 | 01.06.2021 | 700   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 01.06.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 2            | 01.03.2021 | 01.03.2021 |             | 800   | Nei                   | Nei        | 1          | 0                  |
      | 2            | 01.04.2021 | 01.06.2021 |             | 700   | Nei                   | Nei        | 2          | 1                  |

    Så forvent følgende andeler med periodeId
      | BehandlingId | Id | Periode id | Forrige periode id |
      | 1            | 0  | 0          |                    |
      | 2            | 1  | 1          | 0                  |
      | 2            | 2  | 2          | 1                  |

  Scenario: Opphør alle perioder for å sen iverksette på nytt, verifiserer at man fortsatt sender Nei

    Gitt følgende tilkjente ytelser
      | BehandlingId | Uten andeler | Fra dato   | Til dato   | Beløp |
      | 1            |              | 01.03.2021 | 01.03.2021 | 700   |
      | 2            | Ja           |            |            |       |
      | 3            |              | 01.03.2021 | 01.03.2021 | 700   |

    Når beregner utbetalingsoppdrag

    Så forvent følgende utbetalingsoppdrag
      | BehandlingId | Fra dato   | Til dato   | Opphørsdato | Beløp | Første utbetaling sak | Er endring | Periode id | Forrige periode id |
      | 1            | 01.03.2021 | 01.03.2021 |             | 700   | Ja                    | Nei        | 0          |                    |
      | 2            | 01.03.2021 | 01.03.2021 | 01.03.2021  | 700   | Nei                   | Ja         | 0          |                    |
      | 3            | 01.03.2021 | 01.03.2021 |             | 700   | Nei                   | Nei        | 1          | 0                  |
