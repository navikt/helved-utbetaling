![img](utbetaling.png)

# Structure
```
.
├── apps/
│   ├── abetal            # async betaling
│   ├── branntaarn        # varsel om manglende kvittering
│   ├── peisschtappern    # database-sink for alle våre topics
│   ├── simulering        # rest -> soap via proxy
│   ├── urskog            # økosystem med urgamle teknologier som MQ
│   ├── utsjekk           # restful betaling
│   └── vedskiva          # scheduler for avstemming
│
├── libs/
│   ├── auth              # token providers
│   ├── cache             # coroutine safe caching
│   ├── http              # ktor client factory
│   ├── jdbc              # coroutine enabled transactions, connections, migrations
│   ├── kafka             # kafka streams sane defaults kotlin wrappers
│   ├── ktor              # ktor extension 
│   ├── mq                # MQ consumer and producer
│   ├── tracing           # OpenTelemetry utils
│   ├── utils             # Common utils like log, result, env, etc
│   ├── ws                # SOAP web-service and STS-proxy clients. 
│   │   
│   ├── auth-test         # test-utils for auth
│   ├── jdbc-test         # test-utils for jdbc
│   ├── kafka-test        # test-utils for kafka
│   ├── ktor-test         # test-utils for ktor
│   └── mq-test           # test-utils for mq
│
├── documentasjon/        # intern doc
├── models/               # felles modeller
├── topics/               # nais manifester for kafka topics
│
├── Dockerfile.streams    # Dockerfile for kafka streams apper (deprecated)
└── Dockerfile            # Dockerfile for postgres apper (deprecated)
```

## OpenAPI 3.0
Spec for utsjekk sitt rest-api.

[openapi.yaml](dokumentasjon/openapi.yml)

## Development

### Reusable Testcontainers
`~/.testcontainers.properties`:
```properties
# Linux/MacOS uses unix sockets 
docker.client.strategy=org.testcontainers.dockerclient.UnixSocketClientProviderStrategy
# faster startup
checks.disable=true
# reuse container, never call stop()
testcontainers.reuse.enable=true
# disable orchestrator
ryuk.disabled=true
```

If containers are shut down and you get 409 conflict in the tests trying to setup testcontainers,
just manually start them again

```sh
docker start mq postgres 
```

If the testcontainer is updated, the running containers on your machine needs to be replaced.
Just delete them with

```shell
docker stop mq postgres
docker container prune
```

# Heap dump
### jcmd
`jcmd` må ligge i pod containeren.
Avhengig av hvordan man bygger JRE (Java Runtime Environment) så må `jcmd` legges til.
Ved bruke av `jlink` kan man enkelt legge til `--add-modules jdk.jcmd`.

### opprett heap dump
Kjør `jcmd` inne i pod containeren:
> kubectl exec abetal-5f6bfcfb85-f22n9 -- jcmd 1 GC.heap_dump /tmp/dump.hprof

### kopier heap dump
For å kopiere heap dumpen til din lokale maskin, kjør:
> kubectl cp abetal-5f6bfcfb85-f22n9:/tmp/dump.hprof ./dump.hprof

### Analyser heap dump
Installer `Eclipse Memory Analyzer Tool`.
Man kan lage 2 heap dumps med noen timer mellomrom, da kan dette verktøyet inspisere diffen og enklere finne minnelekasje/minnefeil 


# References
- https://docs.oracle.com/javaee/6/api/javax/jms/Session.html
- https://pawelpluta.com/optimise-testcontainers-for-better-tests-performance/
- https://github.com/navikt/tbd-libs/blob/main/minimal-soap-client/src/main/kotlin/com/github/navikt/tbd_libs/soap/SoapResponseHandler.kt
- https://github.com/navikt/helse-spenn/blob/master/spenn-mq/src/main/kotlin/no/nav/helse/spenn/oppdrag/Kvitteringer.kt
- https://jstobigdata.com/jms/jms-transactions-in-action/

