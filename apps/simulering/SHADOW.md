# Sokos-oppdrag REST shadow

In non-production environments, Kafka simulation requests also trigger a background REST call to sokos-oppdrag. The SOAP result remains authoritative; shadow calls never change the published result. The legacy REST endpoint is unaffected.

Two observed REST/SOAP differences are temporarily normalized to SOAP-equivalent `Info.UgyldigRequest` results, without throwing:

- Status `8`, `B110008F - Oppdraget finnes fra før` → `Utbetaling med SakId/BehandlingId finnes fra før`.
- Status `4`, `Navn er ikke funnet ved oppslag` → `Personen finnes ikke i PDL`, only when the calculation is absent or empty.

Other failures remain `Info.Feilet`; other status `0` and `4` responses retain their calculation mapping. Shadow comparison uses exact equality of mapped results, without a separate missing-test-person suppression.

Shadow events go only to `secureLog`. Use `comparisonId` to find related events:

| Event | Content |
| --- | --- |
| `simulering_shadow_soap_mapped` | SOAP result mapped to v1/v2. |
| `simulering_shadow_rest_request` | REST request body. |
| `simulering_shadow_rest_response` | Raw REST response and HTTP status. |
| `simulering_shadow_comparison` | `soapMapped`, `restMapped`, business status, and `equal` indicating whether mapped results match exactly. |
| `simulering_shadow_failed` | REST call, decoding, or mapping failed. |
| `simulering_shadow_skipped` | Shadow queue full or shutting down. |
