package libs.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias CacheKey = String

interface Cache<T> {
    suspend fun add(key: CacheKey, token: T)
    suspend fun get(key: CacheKey): T?
    suspend fun rm(key: CacheKey)
}

interface Token {
    fun isExpired(): Boolean
}

/**
 * Coroutine safe token cache
 */
class TokenCache<T: Token> : Cache<T> {
    private val tokens: HashMap<CacheKey, T> = hashMapOf()
    private val mutex = Mutex()

    override suspend fun add(key: CacheKey, token: T) {
        mutex.withLock {
            tokens[key] = token
        }
    }

    override suspend fun get(key: CacheKey): T? {
        mutex.withLock {
            tokens[key]
        }?.let {
            if (it.isExpired()) {
                rm(key)
            }
        }

        return mutex.withLock {
            tokens[key]
        }
    }

    override suspend fun rm(key: CacheKey) {
        mutex.withLock {
            tokens.remove(key)
        }
    }
}
