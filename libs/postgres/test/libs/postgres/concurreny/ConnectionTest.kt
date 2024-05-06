package libs.postgres.concurreny

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import libs.postgres.H2
import libs.postgres.concurrency.CoroutineDatasource
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConnectionTest : H2() {
    private val scope = CoroutineScope(Dispatchers.IO + CoroutineDatasource(datasource))

    @Test
    fun `can be in context`() = runBlocking {
        scope.async {
            transaction {
                assertNotNull(coroutineContext.connection)
            }
        }.await()
    }

    @Test
    fun `fails without context`() = runBlocking {
        scope.async {
            val err = assertThrows<IllegalStateException> {
                coroutineContext.connection
            }
            assertEquals("Connection not in context", err.message)
        }.await()
    }
}