package utsjekk.iverksetting

import models.kontrakter.felles.objectMapper
import org.intellij.lang.annotations.Language
import utsjekk.utbetaling.UtbetalingV2
import kotlin.test.Test
import kotlin.test.assertEquals

class IverksettingMigratorTest {
    @Test
    fun `migrerer iverksetting`() {
        // Gammel iverksetting slik den leses fra DBen
        val iverksetting = objectMapper.readValue(iverksettingJson, Iverksetting::class.java)
        // Gammel tilkjent ytelse for en iverksetting slik den leses fra DBen (iverksettingsresultat)
        val tilkjentYtelse = objectMapper.readValue(tilkjentYtelseJson, TilkjentYtelse::class.java)

        val expected = objectMapper.readValue(utbetalingJson, UtbetalingV2::class.java)
        assertEquals(expected, IverksettingMigrator.migrate(iverksetting, tilkjentYtelse))
    }
}

@Language("JSON")
private val iverksettingJson = """
    {
      "behandling": {
        "forrigeBehandlingId": "1127",
        "forrigeIverksettingId": "a70a8145-760d-41d4-b0eb-bf5520908d39",
        "behandlingId": "1131",
        "iverksettingId": "cf0bf6c6-5147-4baf-a6d0-2f820c6da727"
      },
      "fagsak": {
        "fagsakId": "200000505",
        "fagsystem": "TILLEGGSSTØNADER"
      },
      "søker": {
        "personident": "02439107599"
      },
      "vedtak": {
        "vedtakstidspunkt": "2025-01-10T09:02:57.578",
        "saksbehandlerId": "Z990223",
        "beslutterId": "Z990288",
        "brukersNavKontor": null,
        "tilkjentYtelse": {
          "id": "10oOijPRLh81Iyq6quLQ",
          "utbetalingsoppdrag": null,
          "andelerTilkjentYtelse": [
            {
              "beløp": 620,
              "satstype": "DAGLIG",
              "periode": {
                "fom": "2024-11-01",
                "tom": "2024-11-01"
              },
              "stønadsdata": {
                "@type": "tilleggsstønader",
                "stønadstype": "TILSYN_BARN_AAP",
                "brukersNavKontor": null
              },
              "periodeId": null,
              "forrigePeriodeId": null,
              "id": "5e534b5b-a708-4b07-9829-91a95959ed92"
            },
            {
              "beløp": 207,
              "satstype": "DAGLIG",
              "periode": {
                "fom": "2025-01-07",
                "tom": "2025-01-07"
              },
              "stønadsdata": {
                "@type": "tilleggsstønader",
                "stønadstype": "TILSYN_BARN_ENSLIG_FORSØRGER",
                "brukersNavKontor": null
              },
              "periodeId": null,
              "forrigePeriodeId": null,
              "id": "4636f95a-51ea-4e76-8a0d-ac42e7d1e3a4"
            }
          ],
          "sisteAndelIKjede": null,
          "sisteAndelPerKjede": {}
        }
      }
    }
""".trimIndent()

@Language("JSON")
private val tilkjentYtelseJson = """{
  "id": "10oOijPRLh81Iyq6quLQ",
  "utbetalingsoppdrag": {
    "erFørsteUtbetalingPåSak": false,
    "fagsystem": "TILLEGGSSTØNADER",
    "saksnummer": "200000505",
    "iverksettingId": "cf0bf6c6-5147-4baf-a6d0-2f820c6da727",
    "aktør": "02439107599",
    "saksbehandlerId": "Z990223",
    "beslutterId": "Z990288",
    "avstemmingstidspunkt": "2025-01-10T09:02:58.263238542",
    "utbetalingsperiode": [
      {
        "erEndringPåEksisterendePeriode": true,
        "opphør": {
          "fom": "2025-01-07"
        },
        "periodeId": 2,
        "forrigePeriodeId": 1,
        "vedtaksdato": "2025-01-10",
        "klassifisering": "TSTBASISP4-OP",
        "fom": "2025-01-07",
        "tom": "2025-01-07",
        "sats": 561,
        "satstype": "DAGLIG",
        "utbetalesTil": "02439107599",
        "behandlingId": "1131",
        "utbetalingsgrad": null,
        "fastsattDagsats": null
      },
      {
        "erEndringPåEksisterendePeriode": false,
        "opphør": null,
        "periodeId": 3,
        "forrigePeriodeId": null,
        "vedtaksdato": "2025-01-10",
        "klassifisering": "TSTBASISP2-OP",
        "fom": "2025-01-07",
        "tom": "2025-01-07",
        "sats": 207,
        "satstype": "DAGLIG",
        "utbetalesTil": "02439107599",
        "behandlingId": "1131",
        "utbetalingsgrad": null,
        "fastsattDagsats": null
      }
    ],
    "brukersNavKontor": null
  },
  "andelerTilkjentYtelse": [
    {
      "beløp": 620,
      "satstype": "DAGLIG",
      "periode": {
        "fom": "2024-11-01",
        "tom": "2024-11-01"
      },
      "stønadsdata": {
        "@type": "tilleggsstønader",
        "stønadstype": "TILSYN_BARN_AAP",
        "brukersNavKontor": null
      },
      "periodeId": 0,
      "forrigePeriodeId": null,
      "id": "4d056ec0-ad26-4567-aeaa-833436f56317"
    },
    {
      "beløp": 207,
      "satstype": "DAGLIG",
      "periode": {
        "fom": "2025-01-07",
        "tom": "2025-01-07"
      },
      "stønadsdata": {
        "@type": "tilleggsstønader",
        "stønadstype": "TILSYN_BARN_ENSLIG_FORSØRGER",
        "brukersNavKontor": null
      },
      "periodeId": 3,
      "forrigePeriodeId": null,
      "id": "edc15d7b-023e-4de6-8766-570cd0bd8196"
    }
  ],
  "sisteAndelIKjede": null,
  "sisteAndelPerKjede": {
    "{\"@type\":\"standard\",\"klassifiseringskode\":\"TSTBASISP4-OP\"}": {
      "beløp": 561,
      "satstype": "DAGLIG",
      "periode": {
        "fom": "2025-01-07",
        "tom": "2025-01-07"
      },
      "stønadsdata": {
        "@type": "tilleggsstønader",
        "stønadstype": "TILSYN_BARN_AAP",
        "brukersNavKontor": null
      },
      "periodeId": 2,
      "forrigePeriodeId": 1,
      "id": "757265b6-3a54-47b5-bf7a-86bb18a66f71"
    },
    "{\"@type\":\"standard\",\"klassifiseringskode\":\"TSTBASISP2-OP\"}": {
      "beløp": 207,
      "satstype": "DAGLIG",
      "periode": {
        "fom": "2025-01-07",
        "tom": "2025-01-07"
      },
      "stønadsdata": {
        "@type": "tilleggsstønader",
        "stønadstype": "TILSYN_BARN_ENSLIG_FORSØRGER",
        "brukersNavKontor": null
      },
      "periodeId": 3,
      "forrigePeriodeId": null,
      "id": "edc15d7b-023e-4de6-8766-570cd0bd8196"
    }
  }
}""".trimIndent()

@Language("JSON")
val utbetalingJson = """
    {
      "sakId": "200000505",
      "behandlingId": "1131",
      "personident": "02439107599",
      "vedtakstidspunkt": "2025-01-10T09:02:57.578",
      "beslutterId": "Z990288",
      "saksbehandlerId": "Z990223",
      "kjeder": {
        "TILSYN_BARN_AAP": {
          "satstype": "VIRKEDAG",
          "stønad": "TILSYN_BARN_AAP",
          "perioder": [
            {
              "fom": "2024-11-01",
              "tom": "2024-11-01",
              "beløp": 620,
              "betalendeEnhet": null,
              "vedtakssats": null
            }
          ],
          "lastPeriodeId": "200000505#2"
        },
        "TILSYN_BARN_ENSLIG_FORSØRGER": {
          "satstype": "VIRKEDAG",
          "stønad": "TILSYN_BARN_ENSLIG_FORSØRGER",
          "perioder": [
            {
              "fom": "2025-01-07",
              "tom": "2025-01-07",
              "beløp": 207,
              "betalendeEnhet": null,
              "vedtakssats": null
            }
          ],
          "lastPeriodeId": "200000505#3"
        }
      }
    }
""".trimIndent()
