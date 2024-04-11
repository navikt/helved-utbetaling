package simulering

import felles.env
import no.nav.common.cxf.StsConfig

data class Config(
    val simulering: SimuleringConfig = SimuleringConfig(),
    val sts: StsConfig = StsConfig.builder()
        .url(env("SECURITYTOKENSERVICE_URL"))
        .username("srvdp-simulering")
        .password(env("servicebruker_passord")) // from secret utsjekk-oppdrag-simulering
        .build(),
)

data class SimuleringConfig(
    val host: String = env("OPPDRAG_SERVICE_URL"),
)
