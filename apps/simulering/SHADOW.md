# Sokos-oppdrag REST shadow

In non-production environments, Kafka simulation requests also trigger a background REST call to sokos-oppdrag. The SOAP result remains authoritative; shadow calls never change the published result. The legacy REST endpoint is unaffected.

Shadow events go only to `secureLog`. Use `comparisonId` to find related events:

| Event | Content |
| --- | --- |
| `simulering_shadow_soap_mapped` | SOAP result mapped to v1/v2. |
| `simulering_shadow_rest_request` | REST request body. |
| `simulering_shadow_rest_response` | Raw REST response and HTTP status. |
| `simulering_shadow_comparison` | `soapMapped`, `restMapped`, business status, and `equal` indicating whether mapped results match exactly. |
| `simulering_shadow_failed` | REST call, decoding, or mapping failed. |
| `simulering_shadow_skipped` | Shadow queue full or shutting down. |
