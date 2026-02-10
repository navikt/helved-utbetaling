package smokesignal

import libs.auth.AzureConfig
import java.net.URI
import java.net.URL
import java.time.LocalDate

object TestRuntime {
    val vedskiva = FakeVedskiva()

    val config = Config(
        azure = AzureConfig(
            tokenEndpoint = URI("http://localhost").toURL(),
            jwks = URI("http://localhost").toURL(),
            issuer = "issuer",
            clientId = "clientId",
            clientSecret = "clientSeecret",
        ),
        vedskiva = VedskivaConfig(
            scope = "scope",
            host = URI("http://localhost").toURL()
        )
    )
}

class FakeVedskiva: Vedskiva {
    val signals = mutableListOf<AvstemmingRequest>()
    var respondWith = AvstemmingRequest(
        today = LocalDate.of(2026, 2, 10),
        fom = LocalDate.of(2026, 2, 9).atStartOfDay(),
        tom = LocalDate.of(2026, 2, 10).atStartOfDay().minusNanos(1),
    )

    override suspend fun next(): AvstemmingRequest {
        return respondWith
    }

    override suspend fun signal(req: AvstemmingRequest) {
        signals.add(req)
    }
}

