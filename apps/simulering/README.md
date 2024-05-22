![img](simulering-banner.png)

# Simulering
API for å utføre simuleringer av beregninger mot oppdrag/UR.

Dette gjøres med ws-kall via en [proxy](https://github.com/navikt/helved-ws-proxy) i dev-fss.
Proxy autentiseres med azure client-credentials, og soap-tjenestene autentiseres med sts.
STS-autentisering skjer ved hjelp av [gandalf](https://github.com/navikt/gandalf).


# references
- https://dzone.com/articles/solving-the-xml-problem-with-jackson