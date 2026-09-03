package libs.auth

import kotlinx.serialization.json.Json
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import libs.cache.TokenCache
import libs.http.HttpClientFactory
import libs.utils.env
import libs.utils.sha256
import java.net.URL

data class AzureConfig(
    val tokenEndpoint: URL = env("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    override val jwks: URL = env("AZURE_OPENID_CONFIG_JWKS_URI"),
    override val issuer: String = env("AZURE_OPENID_CONFIG_ISSUER"),
    override val clientId: String = env("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = env("AZURE_APP_CLIENT_SECRET")
) : TokenConfig(clientId, jwks, issuer)

class AzureTokenProvider(
    private val json: Json,
    private val config: AzureConfig = AzureConfig(),
    private val client: TokenClient = TokenClient(
        http = HttpClientFactory.new(json),
        name = TokenProvider.AZURE,
        cache = TokenCache(),
    )
) {

    suspend fun getClientCredentialsToken(scope: String): TokenResponse =
        client.getAccessToken(config.tokenEndpoint, scope, tokenBody(scope, "client_credentials"))

    suspend fun getOnBehalfOfToken(access_token: String, scope: String): TokenResponse =
        client.getAccessToken(
            config.tokenEndpoint,
            "$scope:${access_token.sha256()}",
            tokenBody(scope, "urn:ietf:params:oauth:grant-type:jwt-bearer") {
                append("assertion", access_token)
                append("requested_token_use", "on_behalf_of")
            },
        )

    suspend fun getUsernamePasswordToken(scope: String, username: String, password: String): TokenResponse =
        client.getAccessToken(
            config.tokenEndpoint,
            "$scope:${username.sha256()}",
            tokenBody(scope, "password") {
                append("username", username)
                append("password", password)
            },
        )

    private fun tokenBody(scope: String, grantType: String, extra: ParametersBuilder.() -> Unit = {}) =
        Parameters.build {
            append("client_id", config.clientId)
            append("client_secret", config.clientSecret)
            append("scope", scope)
            append("grant_type", grantType)
            extra()
        }
}
