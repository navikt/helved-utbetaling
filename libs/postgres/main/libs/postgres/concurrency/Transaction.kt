package libs.postgres.concurrency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.sql.Connection
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalContracts::class)
suspend inline fun <T> transaction(crossinline block: suspend CoroutineScope.() -> T): T {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    val existingTransaction = coroutineContext[CoroutineTransaction]

    return when {
        existingTransaction == null -> {
            withConnection {
                runTransactionally {
                    block()
                }
            }
        }

        !existingTransaction.completed -> {
            withContext(coroutineContext) {
                block()
            }
        }

        else -> error("Nested transactions not supported")
    }
}

@PublishedApi
@OptIn(ExperimentalContracts::class)
internal suspend inline fun <T> runTransactionally(crossinline block: suspend CoroutineScope.() -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    coroutineContext.connection.runWithManuelCommit {
        val transaction = CoroutineTransaction()

        try {
            val result = withContext(transaction) {
                block()
            }

            commit()
            return result
        } catch (e: Throwable) {
            rollback()
            throw e
        } finally {
            transaction.complete()
        }
    }
}

@PublishedApi
@OptIn(ExperimentalContracts::class)
internal inline fun <T> Connection.runWithManuelCommit(block: Connection.() -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val before = autoCommit
    return try {
        autoCommit = false
        run(block)
    } finally {
        autoCommit = before
    }
}
