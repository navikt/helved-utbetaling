package libs.postgres.concurreny

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import libs.postgres.H2
import libs.postgres.concurrency.CoroutineDatasource
import libs.postgres.concurrency.datasource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DatasourceTest : H2() {
    private val scope = CoroutineScope(Dispatchers.IO + CoroutineDatasource(datasource))

    @Test
    fun `can be in context`() = runBlocking {
        scope.async {
            val actual = coroutineContext.datasource
            assertEquals(datasource, actual)
        }.await()
    }

    @Test
    fun `fails without context`() {
        val err = assertThrows<IllegalStateException> {
            runBlocking {
                coroutineContext.datasource
            }
        }
        assertEquals("Datasource not in context", err.message)
    }
}