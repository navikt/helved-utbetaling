package libs.jdbc.concurrency

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import libs.jdbc.jdbcLog

/**
 * Try to aquire a postgres lock with the given name
 * @param on - name of the lock
 * @return true on success and false on failure
 */
suspend fun tryLock(on: String): Boolean = transaction {
    val query = "SELECT PG_TRY_ADVISORY_LOCK(${on.hashCode()})"
    coroutineContext.connection.prepareStatement(query).use { stmt ->
        stmt.execute().also {
            jdbcLog.debug("Locked $on")
        }
    }
}

/**
 * Unlock a postgres lock for the given name
 * @param on - name of the lock
 * @return true on success and false on failure
 */
suspend fun unlock(on: String): Boolean = transaction {
    val query = "SELECT PG_ADVISORY_UNLOCK(${on.hashCode()})"
    coroutineContext.connection.prepareStatement(query).use { stmt ->
        stmt.execute().also {
            jdbcLog.debug("Unlocked $on")
        }
    }
}

/**
 * Wrap som [action] inside a postgres lock, then release it.
 * @param on - name of the lock
 * @param action - the code block to execute inside the lock.
 * @return T - an arbitrary type like a resultset or some mapped structure
 */
@OptIn(ExperimentalContracts::class)
suspend inline fun <T> withLock(owner: String, action: () -> T): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    tryLock(owner)
    return try {
        action()
    } finally {
        unlock(owner)
    }
}
