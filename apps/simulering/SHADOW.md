# Sokos-oppdrag REST shadow

Only Kafka simulation requests trigger shadow calls. The existing SOAP result remains authoritative and is published before shadow work is submitted. The legacy `/simuler/legacy` endpoint does not use this integration.

Shadowing always runs for Kafka simulation requests. Configure:

| Environment variable | Value |
| --- | --- |
| `SOKOS_OPPDRAG_PROXY` | Proxy base URL, without the API path |
| `SOKOS_OPPDRAG_SCOPE` | Azure client-credentials scope accepted by the proxy |

The client posts to `<base URL>/api/osSimulerBeregningApi/simulerBeregning` with a bearer token. Proxy URL and scope are required at startup. Configure these values and the necessary outbound network policy and inbound access policy before deploying. No audience is inferred from the existing SOAP proxy configuration. Deployment manifests are not updated by this change.

HTTP requests use a fixed two-minute timeout. Timeouts are logged only to `secureLog`, without retries or changes to the SOAP result.

Two dedicated daemon threads process shadow work, with at most 16 queued comparisons. Full queues skip shadow work rather than blocking Kafka workers. Exceptions and timeouts cannot replace an already-published SOAP result. Queued work is best effort, not persisted, and is discarded on shutdown.

## Secure comparison logs

All shadow events use **only `secureLog`**, with a shared `comparisonId`, Kafka `key`, `fagsystem`, and `modelVersion`:

- `simulering_shadow_soap_mapped`: existing SOAP result after domain mapping.
- `simulering_shadow_rest_request`: actual REST request body, including POC envelopes.
- `simulering_shadow_rest_response`: raw response body and HTTP status, including non-2xx replies.
- `simulering_shadow_comparison`: both `restMapped` and `soapMapped`, business status, and exact equality.
- `simulering_shadow_failed` / `simulering_shadow_skipped`: incomplete comparison or exhausted capacity.

Authorization headers are never logged. If transport fails before a reply arrives, there is no response body to log. On HTTP or decoding failure, raw replies remain available in the secure response event.

The POC response is adapted to the existing calculation model and then passed through the same v1/v2 mapping as SOAP. Tilleggsstønader and tiltakspenger use v1; other supported fagsystem use v2. Status `0` and `4` permit calculation mapping; other business statuses produce a shadow-only `Info.Feilet`. A missing calculation uses the existing empty-result behavior.

SOAP fields without a REST equivalent are not invented. `belopsgrense` and `fradragTillegg=F` are explicitly rejected for shadow mapping rather than silently discarded. The JAXB simulation contract has no `avventPeriode` or `satsVedtak` input to copy. Dates are forwarded as supplied by Kafka's SOAP request. No new SOAP retries or REST retries are introduced.
