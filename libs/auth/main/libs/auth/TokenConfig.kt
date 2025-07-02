package libs.auth

import libs.utils.env
import java.net.URL

open class TokenConfig(
    open val clientId: String = env("AZURE_APP_CLIENT_ID"),
    open val jwks: URL = env("AZURE_OPENID_CONFIG_JWKS_URI"),
    open val issuer: String = env("AZURE_OPENID_CONFIG_ISSUER")
)
