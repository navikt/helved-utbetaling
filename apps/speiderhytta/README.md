# speiderhytta

> Speiderhytta -- scout cabin. Observerer fra distanse hvordan helved leverer.

Eier observability-metrikker for helved-teamet:

- **DORA-metrikker** (Deployment Frequency, Lead Time for Changes, Change Failure Rate, MTTR) per app
- **SLO-status** per app, basert på definisjoner i hver `apps/*/slo.yml`

## Arkitektur

Pull-basert. Ingen offentlig ingress. `helved-peisen` er eneste innkommende klient.

```
                    ┌──────────────────────────────────────┐
                    │         speiderhytta (NAIS pod)      │
                    │                                      │
   no public ingress│  ┌─────────────────────────────────┐ │
                    │  │ DeployPoller   (every 60s)     ─┼──> api.github.com (Actions, multi-repo)
                    │  │ IncidentPoller (every 60s)     ─┼──> api.github.com (Issues, team-helved)
                    │  │ SloSnapshotter (every 5 min)   ─┼──> prometheus.nav.cloud.nais.io
                    │  └─────────────────────────────────┘ │
                    │  ┌─────────────────────────────────┐ │
                    │  │ Postgres: deployment, incident, │ │
                    │  │           slo_snapshot          │ │
                    │  └─────────────────────────────────┘ │
                    │  ┌─────────────────────────────────┐ │
                    │  │ REST: /dora/*, /slo/*           │ │
                    │  │ helved-peisen consumes          │ │
                    │  └─────────────────────────────────┘ │
                    └──────────────────────────────────────┘
```

## Datakilder

| Kilde | Repo(er) | Hva | Hvordan |
|---|---|---|---|
| GitHub Actions API (`/actions/workflows/{file}/runs` + `/actions/runs/{id}/jobs`) | `navikt/helved-utbetaling` (10 backend apps), `navikt/helved-peisen` (frontend `peisen`) | Deploy-forsøk per app — én rad per `deploy-prod`-jobb | GitHub App installation token |
| GitHub Issues API | `navikt/team-helved` (kanban-repoet) | Incidents (label `incident` + `app:<name>`) | GitHub App installation token |
| GitHub Commits API | Samme som deploy-kilden (commit-lookup gjøres mot riktig code-repo per deploy) | Commit-tidspunkt for lead time | GitHub App installation token |
| NAIS Prometheus | – | Live SLI-verdier og burn rates | In-cluster scrape API |

> **NB:** NAIS eksponerer ikke noe public deployment-history-API (kun
> write-only `POST /api/v1/deploy` via hookd). GitHub Actions er derfor den
> kanoniske kilden for deploy-data — workflow-runs *er* deploy-historikken.

### Hvorfor tre repoer?

- **`helved-utbetaling`** — backend-monorepoet. Hver app har sin egen
  workflow-fil `<app>.yml` (f.eks. `utsjekk.yml`).
- **`helved-peisen`** — frontend-repo. Én delt workflow-fil `deploy.yaml`
  (merk `.yaml`, ikke `.yml`).
- **`team-helved`** — teamets kanban-board. Alle issues, inkludert
  incidents, opprettes og lukkes her.

GitHub-appen må være installert på alle tre.

## Datamodell

`deployment` har én rad per `(app, sha, env, run_id)`. Et re-run av samme
commit produserer en ny rad med eget `outcome` og `runId` — slik at retries
telles som separate forsøk i CFR.

`outcome` er én av:

- `success` — `deploy-prod`-jobben gikk OK
- `failure` — `failure` eller `timed_out`
- `cancelled` — `cancelled` eller `skipped`

In-progress runs (`conclusion = null`) lagres ikke; de fanges opp ved neste poll.

## DORA-definisjoner

- **Deployment Frequency:** antall `success`-deploys / dag (30d-vindu)
- **Lead Time for Changes:** `commit_ts -> deploy_finished_ts` for `success`-rader (median + p90 over 30d)
- **Change Failure Rate:** `(deploy_failures + incidents_linked_to_deploy) / non_cancelled_attempts`
  - `cancelled`-rader er ekskludert fra både teller og nevner
  - Failed deploys teller direkte mot CFR uten å kreve en kobla incident
- **MTTR:** `incident.opened_at -> resolved_at` (median over 30d)

### Kobling incident -> deploy

1. Eksplisitt: `Caused-by: <sha>` i issue-body
2. Heuristikk: nyeste *vellykka* prod-deploy for samme app innen 24t før `opened_at`

(Kun `success`-deploys er linkable — en feilet deploy nådde aldri prod.)

### Apps uten `deploy-prod`-jobb

`snickerboa` har for tida ingen `deploy-prod`-jobb i sin workflow.
DeployPolleren hopper stille over runs uten den jobben, slik at en tom
deploy-serie er det riktige resultatet (ikke en feil eller en hard
ekskludering).

## Inntak av incidents

Opprett GitHub-issue i **`navikt/team-helved`** med:

- Label `incident` (obligatorisk)
- Label `app:<name>` (obligatorisk, f.eks. `app:utsjekk`, `app:peisen`)
- Body inneholder valgfritt `Caused-by: <commit-sha>`

Bruk issue-templatet `.github/ISSUE_TEMPLATE/incident.md` i `team-helved`-repoet.

For frontend-incidents (peisen) der commiten ligger i `helved-peisen`-repoet,
skriv shaen rå (regexen plukker den opp uansett repo); GitHub auto-lenker
40-tegns SHAer.

## SLO-definisjoner

Hver backend-app definerer sine SLOer i `apps/<app>/slo.yml` (Sloth-format).
CI genererer Prometheus-regler via Sloth ved deploy.

speiderhytta leser `slo.yml`-filene ved oppstart for å vite hvilke SLOer som
finnes; live status hentes via PromQL mot NAIS Prometheus.

> **NB:** `prometheus.nav.cloud.nais.io` er federert på tvers av alle
> NAIS-clustere. PromQL-uttrykk i `apps/<app>/slo.yml` **må** derfor
> inkludere `k8s_cluster_name="prod"` i alle label-matchere — ellers vil
> queries returnere data fra dev-gcp og prod-gcp blandet sammen.
> speiderhytta injiserer ikke labelen automatisk; den er forfatterens ansvar.

### Frontend (peisen)

`peisen` har for øyeblikket **ingen SLO-støtte i speiderhytta** — kun
DORA-metrikker. `/slo/peisen` returnerer en tom liste. SLO-definisjoner for
frontend ligger i `helved-peisen`-repoet og leses ikke cross-repo.
Følg-opp-jobb: enten implementer cross-repo SLO-loading, eller speil
`apps/peisen/slo.yml` inn i `helved-utbetaling`.

## API

```
GET /dora                              # alle apps, 30d-vindu
GET /dora/{app}?window=7d              # én app
GET /dora/{app}/deployments?limit=50   # kun success-deploys
GET /dora/{app}/incidents?limit=50

GET /slo                               # alle SLOer
GET /slo/{app}                         # én apps SLOer
GET /slo/{app}/{slo_name}              # én SLO med live state
GET /slo/{app}/{slo_name}/history?window=30d

GET /actuator/health
GET /actuator/metric                   # Prometheus
```

## Sikkerhet

- **Ingen offentlig ingress.** Inbound kun fra `helved-peisen`.
- **Outbound** kun til `api.github.com` og `prometheus.nav.cloud.nais.io`.
- **GitHub App** installert på tre repoer (`helved-utbetaling`,
  `helved-peisen`, `team-helved`) med read-only `Issues`, `Contents` og
  `Actions` permissions, kortlivet installation token (1t).
- **Data** er ikke-PII observabilitymetadata.
