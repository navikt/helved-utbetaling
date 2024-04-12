package simulering

import felles.env
import simulering.ws.SoapConfig
import simulering.ws.StsConfig

data class Config(
    val simulering: SoapConfig = SoapConfig(
        host = env("OPPDRAG_SERVICE_URL"),
        sts = StsConfig(
            host = env("SECURITYTOKENSERVICE_URL"),
            user = "srvdp-simulering",
            pass = env("servicebruker_passord") // from secret utsjekk-oppdrag-simulering
        )
    ),
)
