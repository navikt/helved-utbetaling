package libs.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias CacheKey = String

interface Cache {
    suspend fun add(key: CacheKey, token: Token)
    suspend fun get(key: CacheKey): Token?
    suspend fun rm(key: CacheKey)
}

/**
 * Coroutine safe token cache
 */
class TokenCache : Cache {
    private val tokens: HashMap<CacheKey, Token> = hashMapOf()
    private val mutex = Mutex()

    override suspend fun add(key: CacheKey, token: Token) {
        mutex.withLock {
            tokens[key] = token
        }
    }

    override suspend fun get(key: CacheKey): Token? {
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
