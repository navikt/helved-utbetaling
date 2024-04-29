package libs.postgres.coroutines

import javax.sql.DataSource
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class CoroutineDatasource(
    val datasource: DataSource,
) : AbstractCoroutineContextElement(CoroutineDatasource) {
    companion object Key : CoroutineContext.Key<CoroutineDatasource>

    override fun toString() = "CoroutineDataSource($datasource)"
}

val CoroutineContext.datasource: DataSource
    get() = get(CoroutineDatasource)?.datasource ?: error("Datasource not in context")