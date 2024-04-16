package libs.auth

import libs.http.HttpClientFactory
import libs.utils.env
import java.net.URI
import java.net.URL

data class AzureConfig(
    val tokenEndpoint: URL = env("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientId: String = env("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = env("AZURE_APP_CLIENT_SECRET"),
    val jwksUri: URI = env("AZURE_OPENID_CONFIG_JWKS_URI"),
    val issuer: String = env("AZURE_OPENID_CONFIG_ISSUER")
)

class AzureTokenProvider(
    private val config: AzureConfig = AzureConfig(),
    private val client: TokenClient = TokenClient(
        http = HttpClientFactory.new(),
        name = TokenProvider.AzureAd,
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
