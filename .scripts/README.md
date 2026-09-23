
### Create a user
> nais aiven create kafka <username> helved

Example: `nais aiven create kafka robin helved`

### Grant access to stream
> nais aiven grant-access stream <username> <appname>

Example: `nais aiven grant-access stream robin abetal`

### Grant access to vanilla topic
> nais aiven grant-access topic <username> <topicname>

Example: `nais aiven grant-access topic robin status.v1`

### Get kafka.properties
> nais aiven get kafka <user resource> <namespace>

Will be placed somewhere like `/private/var/folders/bh/xltypjf53350jvww3cr_f4g00000gn/T/aiven-secret-542195830`

Example: `nais aiven get kafka robin-helved-f8fda16d helved`

### Console Utilization (restarts/OOMs)
First login to nais `nais login --nais`,

Then forward proxy the graphql api `nais api proxy`

Change the script with app, cluster and timestamps.

> ./console_graphql_utilization

### Migration schema overview
Generate standalone HTML from PostgreSQL migrations:

> ./.scripts/schema-overview apps/peisschtappern/migrations --output /tmp/peisschtappern-schema.html

Write report to a file instead of standard output:

> open /tmp/peisschtappern-schema.html

The script tracks final tables, columns, primary keys, and indexes after all migrations. It compares XML (`oppdrag`, `simuleringer`) and JSON Kafka-topic tables separately, including index differences. `timer`, `korrigerte_feilet_utbetalinger`, `kjent_dobbeltutbetaling`, `kvittering`, `fk`, and `oppdragsdata` are excluded from this comparison.

### JSON topic-table group template
`schema-overview` embeds a copy-and-fill SQL template below its comparison view for adding a source, internal, and dryrun table. Replace its four `{{...}}` placeholders before adding it as a migration.
