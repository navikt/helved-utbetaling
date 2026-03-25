package abetal

import org.junit.jupiter.api.AfterEach

open class ConsumerTestBase {
    
    @AfterEach
    fun `assert empty topic`() {
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.simulering.assertThat().isEmpty()
        TestRuntime.topics.status.assertThat().isEmpty()
        TestRuntime.topics.saker.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().isEmpty()
        TestRuntime.topics.dryrunAap.assertThat().isEmpty()
        TestRuntime.topics.dryrunDp.assertThat().isEmpty()
        TestRuntime.topics.dryrunTs.assertThat().isEmpty()
        TestRuntime.topics.dryrunTp.assertThat().isEmpty()
    }
}
