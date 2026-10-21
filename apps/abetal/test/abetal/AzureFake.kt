package abetal

import libs.auth.JwkGenerator
import libs.auth.TEST_JWKS
import libs.auth.TokenConfig
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttpLoom
import org.http4k.server.asServer
import java.net.URI

class AzureFake : AutoCloseable {
    private val server = routes("/jwks" bind Method.GET to { Response(Status.OK).body(TEST_JWKS) })
        .asServer(SunHttpLoom(0)).start()

    val config = TokenConfig(
        jwks = URI("http://localhost:${server.port()}/jwks").toURL(),
        issuer = "test",
        clientId = "hei"
    )

    private val jwksGenerator = JwkGenerator(config.issuer, config.clientId)

    fun generateToken() = jwksGenerator.generate()

    override fun close() { server.stop() }
}
