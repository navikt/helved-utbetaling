package branntaarn

import libs.auth.AzureConfig
import java.net.URI
import java.time.LocalDateTime
import kotlin.test.Test

class BranntaarnTest {
    private val testConfig = Config(
        azure = AzureConfig(
            tokenEndpoint = URI("http://fake/token").toURL(),
            jwks = URI("http://fake/jwks").toURL(),
            issuer = "fake",
            clientId = "fake",
            clientSecret = "fake",
        ),
        peisschtappern = PeisschtappernConfig(
            scope = "fake-scope",
            host = URI("http://fake-peis").toURL(),
        ),
        peisen = PeisenConfig(host = URI("http://fake-peisen").toURL()),
        slack = SlackConfig(host = URI("http://fake-slack").toURL()),
        nais = NaisConfig(cluster = "test"),
    )

    @Test
    fun `returns early on Norwegian holiday`() {
        branntaarn(testConfig, LocalDateTime.of(2026, 5, 17, 12, 0))
    }

    @Test
    fun `returns early before 6 AM`() {
        branntaarn(testConfig, LocalDateTime.of(2026, 4, 22, 5, 59))
    }

    @Test
    fun `returns early after 21 hours`() {
        branntaarn(testConfig, LocalDateTime.of(2026, 4, 22, 22, 0))
    }
}
