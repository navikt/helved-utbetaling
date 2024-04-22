package simulering

import libs.utils.env
import libs.ws.SoapConfig
import libs.ws.StsConfig

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
