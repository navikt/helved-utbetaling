package peisschtappern

import libs.auth.*
import org.http4k.core.*
import org.http4k.routing.*
import org.http4k.server.SunHttpLoom
import org.http4k.server.asServer
import java.net.URI
import java.time.Instant

class AzureFake : AutoCloseable {
    private val azure = routes("/jwks" bind Method.GET to { Response(Status.OK).body(TEST_JWKS) })
        .asServer(SunHttpLoom(0)).start()

    val config by lazy {
        TokenConfig(
            jwks = URI("http://localhost:${azure.port()}/jwks").toURL(),
            issuer = "test",
            clientId = "hei"
        )
    }

    private val jwksGenerator = JwkGenerator(config.issuer, config.clientId)

    fun generateToken(claims: List<Claim> = defaultClaims) = jwksGenerator.generate(claims.toList())

    val defaultClaims = listOf(
        Claim("name", "test"),
        Claim("NAVident", "T123456"),
        Claim("preferred_username", "test@nav.no"),
    ) 

    override fun close() { azure.stop() }
}

class AuditLogReaderFake : AuditLogReader {
    val entries = mutableMapOf<String, MutableList<AuditLogEntry>>()
    var lastResourceName: String? = null
    var lastFom: Instant? = null
    var lastTom: Instant? = null
    var lastFilter: String? = null
    var lastPageSize: Int? = null
    var lastPageToken: String? = null

    override fun list(resourceName: String, fom: Instant?, tom: Instant?, pageSize: Int, pageToken: String?, filter: String?): AuditLogPage {
        lastResourceName = resourceName
        lastFom = fom
        lastTom = tom
        lastFilter = filter
        lastPageSize = pageSize
        lastPageToken = pageToken
        return AuditLogPage(entries[resourceName].orEmpty().take(pageSize), nextPageToken = null)
    }
}
