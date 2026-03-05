# Application Domain Guide

> **Parent context:** See root `AGENTS.md` for conventions, build system, and patterns.

## Norwegian Domain Terminology

Quick reference for Norwegian domain terms used throughout the codebase:

| Norwegian | English | Description |
|-----------|---------|-------------|
| **Iverksetting** | Execution | Payment decision execution/processing |
| **Utbetaling** | Payment/Disbursement | Actual money transfer to recipient |
| **Oppdrag** | Payment Order | XML order sent to OS (Oppdragssystemet) |
| **Simulering** | Simulation | Dry-run of payment without actual transfer |
| **Avstemming** | Reconciliation | Matching internal records with OS |
| **Kvittering** | Receipt/Acknowledgment | Confirmation from OS |
| **Stønadstype** | Benefit Type | Type of financial support |
| **Fagområde** | Benefit Area | Domain/category of benefit |
| **Fagsystem** | Source System | Originating system (AAP, DP, TS, TP) |
| **Søker** | Applicant | Person applying for benefits |
| **Sak** | Case | Benefit case (sakId + fagsystem) |
| **Behandling** | Treatment/Processing | Case processing instance |
| **Personident** | Person Identifier | National ID number |
| **Vedtak** | Decision | Official benefit decision |
| **Vedtakstidspunkt** | Decision Timestamp | When decision was made |

## System Architecture

### Payment Flow Overview

```
External Teams                  helved-utbetaling                External Systems
─────────────                  ─────────────────                ────────────────
                                                                
team-aap ──────┐                                                
team-dagpenger ┼──> [snickerboa] ──> [abetal] ──> [urskog] ──> IBM MQ/OS
team-tillegg ──┘         │              │            │          
                         │              │            │          SOAP Services
                         v              v            v          
                   [peisschtappern]  [utsjekk]  [vedskiva]
                         │              │            │
                         v              v            v
                   PostgreSQL      PostgreSQL   PostgreSQL
                   
                   [statistikkern] ──> BigQuery
                   [branntaarn] ──> Slack
                   [smokesignal] (cron)
```

### Kafka Topic Architecture

See the **Kafka Topics** section below for complete topic contracts.

---

## Applications

### Core Payment Flow

#### **utsjekk** - Payment Orchestration Service
- **Location:** `apps/utsjekk/main/utsjekk/Utsjekk.kt`
- **Purpose:** Main payment orchestration API. Handles iverksetting (execution), utbetaling (payment operations), and simulering endpoints. Maintains aggregated view of cases (saker).

**Key Components:**
- `iverksetting/`: Payment execution workflow routes and service
- `utbetaling/`: Payment CRUD operations
- `simulering/`: Simulation request handling
- `routes/`: Authenticated HTTP route definitions
- `Kafka.kt`: Kafka Streams topology for status sync and aggregation

**Kafka Consumption:**
- `helved.status.v1` → syncs status to PostgreSQL
- `helved.utbetalinger.v1` → final payments
- `helved.dryrun-dp.v1`, `helved.dryrun-ts.v1` → simulation results (GlobalKTable)

**Kafka Production:**
- `helved.oppdrag.v1` → payment orders (legacy REST API path)
- `helved.utbetalinger.v1` → payments from REST API
- `helved.saker.v1` → aggregated case UIDs (Set<UtbetalingId> per SakKey)

**Database Schema:**
- `iverksetting` - Payment execution requests (behandling_id, data json, mottatt_tidspunkt)
- `iverksettingsresultat` - Execution results (indexed on fagsystem, sakId, behandling_id, iverksetting_id)
- `utbetaling` - Append-only payment tracking (utbetaling_id, sak_id, behandling_id, personident, stønad, status, data jsonb, created_at, updated_at, deleted_at)

**API Contracts:**
- `POST /iverksetting` - Start payment execution
- `GET /iverksetting/{behandlingId}` - Get execution status
- `POST /utbetaling` - Create payment
- `GET /utbetaling/{utbetalingId}` - Get payment details
- `POST /simulering` - Request simulation

**Testing:**
- TestRuntime provides: Kafka mock, PostgreSQL datasource, Ktor test server
- TestData factory methods for creating fixtures

---

#### **urskog** - Payment Order Dispatcher
- **Location:** `apps/urskog/main/urskog/Urskog.kt`
- **Purpose:** Gateway between Kafka and external systems (IBM MQ, SOAP). Sends payment orders and simulations to OS (Oppdragssystemet), receives kvitteringer.

**Key Components:**
- MQ integration: Sends oppdrag to external payment system via IBM MQ
- WS client: SOAP integration for simulations
- `DaoOppdrag`: Oppdrag persistence and send coordination
- `DaoPendingUtbetaling`: Tracks individual payments awaiting kvittering
- `SimuleringMapperV1`: Transforms Kafka simulation requests to SOAP format

**Kafka Consumption:**
- `helved.oppdrag.v1` → payment orders to send via MQ
- `helved.simuleringer.v1` → simulations to send via SOAP
- `helved.pending-utbetalinger.v1` → pending payments
- `helved.avstemming.v1` → reconciliation data
- (MQ queue): Oppdrag kvittering responses

**Kafka Production:**
- `helved.status.v1` → status after sending (HOS_OPPDRAG, OK, FEILET)
- `helved.dryrun-aap.v1`, `helved.dryrun-dp.v1`, `helved.dryrun-ts.v1`, `helved.dryrun-tp.v1` → simulation results
- `helved.kvittering.v1` → raw kvittering from MQ

**Database Schema:**
- `oppdrag` - Payment orders (hash_key for idempotency, oppdrag XML, sak_id, behandling_id, uids, sent, sent_at)
- `pending_utbetaling` - Individual payments within oppdrag (hash_key, uid, mottatt, mottatt_at)

**Coordination Pattern:**
- Waits for ALL pending_utbetalinger for an oppdrag before sending to MQ
- Uses database coordination to ensure exactly-once delivery
- Enriches oppdrag with kvittering (mmel field) and produces back to `helved.oppdrag.v1`

**Dependencies:** `libs:mq`, `libs:ws`, `libs:jdbc`

---

### Data Aggregation

#### **abetal** - Payment Aggregation Engine
- **Location:** `apps/abetal/main/abetal/Abetal.kt`
- **Purpose:** Core Kafka Streams aggregation and transformation. Consumes payment requests from multiple teams, joins with state, transforms to Oppdrag format, handles idempotency.

**Key Components:**
- `AggregatService`: Aggregates individual payments into Oppdrag batches
- `OppdragService`: Transforms domain models to Oppdrag XML
- `SimuleringService`: Handles simulation request mapping
- Kafka topology with multiple state stores (utbetalinger, saker, pendingUtbetalinger)

**Kafka Consumption:**
- **External topics:** `aap.utbetaling.v1`, `teamdagpenger.utbetaling.v1`, `tilleggsstonader.utbetaling.v1`, `historisk.utbetaling.v1`
- **Internal topics:** `helved.utbetalinger-aap.v1`, `helved.utbetalinger-dp.v1`, `helved.utbetalinger-ts.v1`, `helved.utbetalinger-tp.v1`, `helved.utbetalinger-historisk.v1`
- **State topics:** `helved.saker.v1`, `helved.pending-utbetalinger.v1`, `helved.oppdrag.v1` (with kvittering), `helved.retry-oppdrag.v1`

**Kafka Production:**
- `helved.oppdrag.v1` → payment orders to send
- `helved.simuleringer.v1` → simulation requests
- `helved.status.v1` → status updates (MOTTATT, etc.)
- `helved.pending-utbetalinger.v1` → pending payments per UID
- `helved.utbetalinger.v1` → final payments after kvittering confirmation
- `helved.dryrun-aap.v1`, `helved.dryrun-dp.v1`, `helved.dryrun-ts.v1`, `helved.dryrun-tp.v1` → simulation results
- `helved.retry-oppdrag.v1` → retry for failed joins

**Architecture Pattern:**
1. Consumes external payment requests
2. Repartitions to 3 partitions for consistent processing
3. Joins with existing saker (case state) for idempotency
4. Aggregates individual payments into Oppdrag batches
5. Produces oppdrag + pending per UID
6. Waits for kvittering (00/04 codes) from oppdrag topic
7. Joins pending with kvittering, moves to final utbetalinger topic

**State Stores:**
- GlobalKTable for utbetalinger (all final payments)
- GlobalKTable for saker (Set<UtbetalingId> per SakKey)
- GlobalKTable for pendingUtbetalinger (awaiting kvittering)

**No Database** - Pure Kafka Streams application

---

### Reconciliation

#### **vedskiva** - Reconciliation Service
- **Location:** `apps/vedskiva/main/vedskiva/Vedskiva.kt`
- **Purpose:** Performs avstemming (reconciliation) between internal records and OS. Logs all oppdrag for audit and scheduled reconciliation runs.

**Key Components:**
- `AvstemmingService`: Reconciliation logic
- `AvstemmingFactory`: Creates avstemming data structures
- `OppdragDao`: Persists oppdrag from kvitteringer
- `ScheduledDao`: Tracks reconciliation runs

**Kafka Consumption:**
- `helved.oppdrag.v1` → all oppdrag (logs to DB)
- `helved.avstemming.v1` → avstemming triggers

**Database Schema:**
- `scheduled` - Reconciliation run tracking (created_at, avstemt_fom, avstemt_tom)
- `oppdrag` - Oppdrag extracted from kvitteringer (hash_key, nokkelAvstemming timestamp, kodeFagomraade, personident, fagsystemId, sats amount, alvorlighetsgrad, kodeMelding, beskrMelding)

**Dependencies:** `libs:kafka`, `libs:jdbc`

---

#### **smokesignal** - Reconciliation Trigger
- **Location:** `apps/smokesignal/main/smokesignal/Smokesignal.kt`
- **Purpose:** Scheduled job that triggers daily avstemming by producing to `helved.avstemming.v1`.

**Key Components:**
- `VedskivaClient`: HTTP client to trigger vedskiva
- Holiday-aware scheduling: Skips Norwegian holidays

**Kafka Production:**
- `helved.avstemming.v1` → triggers reconciliation

**No Database**

---

### Monitoring & Support

#### **peisschtappern** - Event Auditing Service
- **Location:** `apps/peisschtappern/main/peisschtappern/Peisschtappern.kt`
- **Purpose:** Universal Kafka audit logger. Stores every message from every topic to PostgreSQL with full metadata for debugging, monitoring, and manual operations.

**Key Components:**
- Kafka topology that consumes ALL topics as raw bytes
- `TimerDao`: Tracks alerts/timeouts
- `ManuellEndringService`: Manual intervention operations
- REST API for querying historical messages

**Kafka Consumption:**
- **ALL topics** (consumes as bytes for complete audit trail):
  - Core: oppdrag, kvittering, simuleringer, utbetalinger, pending_utbetalinger, saker, avstemming, status
  - External: aap, dp, ts, tp, fk, historisk
  - Internal: aapIntern, dpIntern, tsIntern, tpIntern, historiskIntern
  - Dryrun: dryrun_aap, dryrun_dp, dryrun_ts, dryrun_tp

**Database Schema:**
- **One table per topic** (standard schema for all):
  - `id` (bigserial, PK)
  - `version`, `topic_name`, `record_key`, `record_value` (JSON)
  - `record_partition`, `record_offset`, `timestamp_ms`, `stream_time_ms`, `system_time_ms`
  - `trace_id`, `commit`, `headers`
  - Some tables: `sak_id`, `fagsystem`, `status`
- `timer` - Scheduled task tracking for alerts (record_key, timeout, sak_id, fagsystem)

**API Contracts:**
- `GET /messages/{topic}` - Query messages by topic
- `GET /messages/{topic}/{key}` - Get specific message
- `POST /manual/{operation}` - Manual interventions

**Dependencies:** `libs:kafka`, `libs:jdbc`, `libs:ktor`

---

#### **branntaarn** - Alert Monitor
- **Location:** `apps/branntaarn/main/branntaarn/Branntaarn.kt`
- **Purpose:** Scheduled monitor that checks peisschtappern for timed-out oppdrag (missing kvittering) and alerts to Slack.

**Key Components:**
- `PeisschtappernClient`: Queries peisschtappern DB
- `SlackClient`: Posts alerts
- Holiday-aware checks

**No Kafka Consumption/Production**

**No Database** (queries peisschtappern DB)

---

#### **snickerboa** - Request-Reply Correlator
- **Location:** `apps/snickerboa/main/snickerboa/Snickerboa.kt`
- **Purpose:** REST API gateway for external teams. Provides request-reply correlation using Kafka Streams for async message routing.

**Key Components:**
- `RequestReplyCorrelator`: Matches requests with async status/dryrun responses
- REST routes for payment submission and result polling

**Kafka Consumption:**
- `helved.status.v1` → status replies
- `helved.dryrun-aap.v1`, `helved.dryrun-dp.v1`, `helved.dryrun-ts.v1`, `helved.dryrun-tp.v1` → simulation results

**Kafka Production:**
- `helved.utbetalinger-aap.v1`, `helved.utbetalinger-dp.v1`, `helved.utbetalinger-ts.v1`, `helved.utbetalinger-tp.v1`, `helved.utbetalinger-historisk.v1` → payment requests from REST API

**API Contracts:**
- `POST /utbetaling/{fagsystem}` - Submit payment
- `GET /utbetaling/{fagsystem}/{correlationId}` - Poll for status
- `POST /simulering/{fagsystem}` - Submit simulation
- `GET /simulering/{fagsystem}/{correlationId}` - Poll for result

**No Database** (uses Kafka state stores for correlation)

---

#### **statistikkern** - Analytics Pipeline
- **Location:** `apps/statistikkern/main/statistikkern/Statistikkern.kt`
- **Purpose:** Streams payment and status data to BigQuery for analytics.

**Key Components:**
- `BigQueryService`: BigQuery client and schema management
- Kafka consumer topology

**Kafka Consumption:**
- `helved.utbetalinger.v1` → final payments
- `helved.status.v1` → status updates

**No Kafka Production**

**No Database** (writes to BigQuery)

---

#### **simulering** - SOAP Proxy Service
- **Location:** `apps/simulering/main/simulering/Simulering.kt`
- **Purpose:** REST-to-SOAP proxy for payment simulations. Transforms REST requests to SOAP format for OS integration.

**Key Components:**
- `SimuleringService`: Transformation logic
- `models/rest`: REST DTOs
- `models/soap`: SOAP/JAXB classes
- Routing for simulation endpoints

**No Kafka Integration** (pure REST/SOAP proxy)

**No Database**

**API Contracts:**
- `POST /simulering` - Transform REST → SOAP and call OS

---

## Kafka Topics Reference

### Core Payment Topics

| Topic | Producer | Consumer | Data Type | Purpose |
|-------|----------|----------|-----------|---------|
| `helved.utbetalinger.v1` | abetal, utsjekk | utsjekk, statistikkern, peisschtappern | Utbetaling (JSON) | Final payments after kvittering |
| `helved.pending-utbetalinger.v1` | abetal | urskog, peisschtappern | Utbetaling (JSON) | Payments awaiting kvittering |
| `helved.oppdrag.v1` | abetal, utsjekk | urskog, vedskiva, peisschtappern, abetal | Oppdrag (XML) | Payment orders to OS |
| `helved.simuleringer.v1` | abetal | urskog, peisschtappern | SimulerBeregningRequest (XML) | Simulation requests |
| `helved.status.v1` | abetal, urskog, utsjekk | utsjekk, snickerboa, statistikkern, peisschtappern | StatusReply (JSON) | Status updates |
| `helved.saker.v1` | utsjekk | abetal, peisschtappern | Set<UtbetalingId> keyed by SakKey | Aggregated case UIDs |
| `helved.avstemming.v1` | smokesignal | urskog, vedskiva, peisschtappern | Avstemmingsdata (XML) | Reconciliation triggers |
| `helved.kvittering.v1` | urskog | peisschtappern | bytes | Kvittering from OS |

### External Input Topics

| Topic | Producer | Consumer | Data Type | Purpose |
|-------|----------|----------|-----------|---------|
| `aap.utbetaling.v1` | team-aap | abetal, peisschtappern | AapUtbetaling (JSON) | AAP payment requests |
| `teamdagpenger.utbetaling.v1` | team-dagpenger | abetal, peisschtappern | DpUtbetaling (JSON) | Dagpenger requests |
| `tilleggsstonader.utbetaling.v1` | team-tilleggsstønader | abetal, peisschtappern | TsDto (JSON) | Tilleggsstønader requests |
| `historisk.utbetaling.v1` | (external) | abetal, peisschtappern | HistoriskUtbetaling (JSON) | Historical migrations |

### Internal Repartitioned Topics

| Topic | Producer | Consumer | Data Type | Purpose |
|-------|----------|----------|-----------|---------|
| `helved.utbetalinger-aap.v1` | snickerboa | abetal, utsjekk, peisschtappern | AapUtbetaling (JSON) | Internal AAP (repartitioned) |
| `helved.utbetalinger-dp.v1` | snickerboa | abetal, utsjekk, peisschtappern | DpUtbetaling (JSON) | Internal DP (repartitioned) |
| `helved.utbetalinger-ts.v1` | snickerboa | abetal, utsjekk, peisschtappern | TsDto (JSON) | Internal TS (repartitioned) |
| `helved.utbetalinger-tp.v1` | (internal) | abetal, utsjekk, peisschtappern | TpUtbetaling (JSON) | Internal Tiltakspenger |
| `helved.utbetalinger-historisk.v1` | (internal) | abetal, peisschtappern | HistoriskUtbetaling (JSON) | Internal historical |

### Simulation Response Topics

| Topic | Producer | Consumer | Data Type | Purpose |
|-------|----------|----------|-----------|---------|
| `helved.dryrun-aap.v1` | abetal, urskog | utsjekk, snickerboa, peisschtappern | Simulering (JSON) | AAP simulation results |
| `helved.dryrun-dp.v1` | abetal, urskog | utsjekk, snickerboa, peisschtappern | Simulering (JSON) | DP simulation results |
| `helved.dryrun-ts.v1` | abetal, urskog | utsjekk, snickerboa, peisschtappern | Simulering (JSON) | TS simulation results |
| `helved.dryrun-tp.v1` | abetal, urskog | snickerboa, peisschtappern | Simulering (JSON) | TP simulation results |

### Data Flow Pattern Example

**Dagpenger Payment Flow:**
1. team-dagpenger → `teamdagpenger.utbetaling.v1` (DpUtbetaling)
2. abetal consumes, joins with `helved.saker.v1` state
3. abetal → `helved.oppdrag.v1` (Oppdrag XML) + `helved.pending-utbetalinger.v1` + `helved.status.v1` (MOTTATT)
4. urskog consumes oppdrag + pending, coordinates send via DB
5. urskog sends to OS via MQ → `helved.status.v1` (HOS_OPPDRAG)
6. OS sends kvittering via MQ → urskog → `helved.kvittering.v1`
7. urskog enriches oppdrag with kvittering → `helved.oppdrag.v1`
8. abetal consumes oppdrag with kvittering (00/04), joins with pending
9. abetal → `helved.utbetalinger.v1` (final payment)
10. utsjekk consumes → aggregates to `helved.saker.v1`
11. statistikkern streams to BigQuery

---

## Testing Applications

### Common Test Patterns

All apps follow the TestRuntime pattern:
```kotlin
object TestRuntime {
    val context: CoroutineContext // for runTest
    val datasource: DataSource // PostgreSQL Testcontainers
    val streamsMock: StreamsMock // Kafka Streams testing
    val httpClient: HttpClient // Ktor CIO client
    // App-specific fakes (MQ, WS, etc.)
}

object TestData {
    fun utbetaling(...): Utbetaling // Factory with defaults
    fun oppdrag(...): Oppdrag
    // Domain-specific fixtures
}
```

### App-Specific Test Notes

- **utsjekk**: Tests HTTP routes, Kafka topology, DB persistence with TestRuntime
- **urskog**: Uses MQFake from `libs:mq-test`, tests coordination logic with DB
- **abetal**: Pure Kafka Streams testing with StreamsMock, no DB
- **vedskiva**: Tests avstemming logic with PostgreSQL Testcontainers
- **peisschtappern**: Tests audit logging and manual operations REST API

---

## Common Workflows

### Adding a New App

1. Create `apps/newapp/main/newapp/NewApp.kt` with entry point
2. Create `apps/newapp/build.gradle.kts` with dependencies
3. Add to `settings.gradle.kts`
4. Follow app structure pattern (see root AGENTS.md)
5. Update this file with app details

### Debugging Payment Flow

1. Check `peisschtappern` for message history (all topics logged)
2. Query `utsjekk.utbetaling` table for payment status
3. Check `urskog.oppdrag` for send coordination state
4. Look for kvittering in `helved.kvittering.v1` via peisschtappern
5. Verify Oppdrag kvittering codes (00 = success, 04 = already sent, 08 = error)

### Manual Intervention

Use `peisschtappern` REST API for manual operations:
- Resend failed oppdrag
- Mark pending as mottatt
- Query historical messages by key/offset
