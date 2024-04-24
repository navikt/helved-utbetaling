package libs.utils

import java.net.URI
import java.net.URL

inline fun <reified T : Any> env(variable: String): T {
    val env = System.getenv(variable)
        ?: error("missing envVar $variable")

    return when (val type = T::class) {
        String::class -> env as T
        Int::class -> env.toInt() as T
        Long::class -> env.toLong() as T
        Boolean::class -> env.toBoolean() as T
        URI::class -> URI(env) as T
        URL::class -> URI(env).toURL() as T
        else -> error("unsupported type $type")
    }
}

inline fun <reified T : Any> env(variable: String, default: T): T {
    return runCatching { env<T>(variable) }.getOrDefault(default)
}
