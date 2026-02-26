package abetal.consumers

import abetal.TestRuntime
import org.junit.jupiter.api.AfterEach

abstract class ConsumerTestBase {
    
    @AfterEach
    fun `assert empty topic`() {
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.simulering.assertThat()
        TestRuntime.topics.status.assertThat().isEmpty()
        TestRuntime.topics.saker.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat()
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
    }
}
