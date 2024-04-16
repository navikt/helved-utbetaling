package libs.auth

import libs.http.HttpClientFactory
import libs.utils.env
import java.net.URL

data class AzureConfig(
    val tokenEndpoint: URL = env("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    override val jwks: URL = env("AZURE_OPENID_CONFIG_JWKS_URI"),
    override val issuer: String = env("AZURE_OPENID_CONFIG_ISSUER"),
    override val clientId: String = env("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = env("AZURE_APP_CLIENT_SECRET")
) : TokenConfig(clientId, jwks, issuer)

class AzureTokenProvider(
    private val config: AzureConfig = AzureConfig(),
    private val client: TokenClient = TokenClient(
        http = HttpClientFactory.new(),
        name = TokenProvider.AZURE,
        cache = TokenCache(),
    )
) {
    suspend fun getClientCredentialsToken(scope: String): Token =
        client.getAccessToken(config.tokenEndpoint, scope) {
            """
                client_id=${config.clientId}
                &client_secret=${config.clientSecret}
                &scope=$scope
                &grant_type=client_credentials
            """.asUrlPart()
        }

    suspend fun getOnBehalfOfToken(access_token: String, scope: String): Token =
        client.getAccessToken(config.tokenEndpoint, scope) {
            """
                client_id=${config.clientId}
                &client_secret=${config.clientSecret}
                &assertion=$access_token
                &scope=$scope
                &grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer
                &requested_token_use=on_behalf_of
            """.asUrlPart()
        }

    suspend fun getUsernamePasswordToken(scope: String, username: String, password: String): Token =
        client.getAccessToken(config.tokenEndpoint, username) {
            """
                client_id=${config.clientId}
                &client_secret=${config.clientSecret}
                &scope=$scope
                &username=$username
                &password=$password
                &grant_type=password
            """.asUrlPart()
        }
}

private fun String.asUrlPart() = trimIndent().replace("\n", "")
