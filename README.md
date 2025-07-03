![img](utbetaling.png)

# Monorepo structure
```
.
├── apps/
│   ├── abetal            # async betaling
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
│   └── ws                # SOAP web-service and STS-proxy clients. 
│
├── documentasjon/        # intern doc
├── models/               # gjenbrukbart
└── topics/               # kafka-topic nais-manifester
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

# References
- https://docs.oracle.com/javaee/6/api/javax/jms/Session.html
- https://pawelpluta.com/optimise-testcontainers-for-better-tests-performance/
- https://github.com/navikt/tbd-libs/blob/main/minimal-soap-client/src/main/kotlin/com/github/navikt/tbd_libs/soap/SoapResponseHandler.kt
- https://github.com/navikt/helse-spenn/blob/master/spenn-mq/src/main/kotlin/no/nav/helse/spenn/oppdrag/Kvitteringer.kt
- https://jstobigdata.com/jms/jms-transactions-in-action/

