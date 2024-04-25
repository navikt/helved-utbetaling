# utsjekk-oppdrag

## DEVS

### Reusable testcontainers

Add this to your `~/.testcontainers.properties` file

```properties
checks.disable=true                 # faster startup
testcontainers.reuse.enable=true    # reuse container
ryuk.disabled=true                  # disable ryuk
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

