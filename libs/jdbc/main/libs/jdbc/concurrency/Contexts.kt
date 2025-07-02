package libs.jdbc.concurrency

import java.sql.Connection
import javax.sql.DataSource
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Gets the connection from the coroutine context if registered.
 */
val CoroutineContext.connection: Connection
    get() = get(CoroutineConnection)
        ?.connection
        ?: error("Connection not in context")

/**
 * Gets the datasource from the coroutine context if registered.
 */
val CoroutineContext.datasource: DataSource
    get() = get(CoroutineDatasource)
        ?.datasource
        ?: error("Datasource not in context")

class CoroutineDatasource(
    val datasource: DataSource,
) : AbstractCoroutineContextElement(CoroutineDatasource) {
    companion object Key : CoroutineContext.Key<CoroutineDatasource>

    override fun toString() = "CoroutineDataSource($datasource)"
}

class CoroutineConnection(
    val connection: Connection
) : AbstractCoroutineContextElement(CoroutineConnection) {

    companion object Key : CoroutineContext.Key<CoroutineConnection>

    override fun toString(): String = "CoroutineConnection($connection)"
}

@PublishedApi
internal class CoroutineTransaction : AbstractCoroutineContextElement(CoroutineTransaction) {
    companion object Key : CoroutineContext.Key<CoroutineTransaction>

    var completed: Boolean = false
        private set

    fun complete() {
        completed = true
    }

    override fun toString(): String = "CoroutineTransaction(completed=$completed)"
}
