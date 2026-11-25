package simulering

import models.Fagsystem
import models.v2
import org.http4k.core.Response
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Isolated
class SimuleringShadowTest {
    private val config = SokosOppdragConfig(host = URI("http://sokos.test").toURL(), scope = "test", cluster = "test-cluster")

    @Test
    fun `full shadow koe forkaster nye kall uten aa blokkere Kafka arbeidet`() {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(18)
        val attempts = AtomicInteger()
        val client = SokosClient(config, {
            attempts.incrementAndGet()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            completed.countDown()
            Response(Status.OK).body("""{"OsSimulerBeregningOperationResponse":{"ZS1OCont":{"responsSimulering":{"statusKode":0}}}}""")
        }, { "Bearer test-token" })
        SimuleringShadow(client, config).use { shadow ->
            try {
                repeat(32) { shadow.submit("bounded-$it", simulering(fagområde = "AAP"), Fagsystem.AAP, v2.Simulering(emptyList())) }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertEquals(2, attempts.get())
            } finally {
                release.countDown()
                assertTrue(completed.await(5, TimeUnit.SECONDS))
            }
            // Two running calls and sixteen queued; the remaining submissions were discarded.
            assertEquals(18, attempts.get())
        }
    }
}
