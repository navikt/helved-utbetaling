package abetal

import libs.utils.secureLog

sealed interface Result<out V, out E> {
    data class Ok<V>(val value: V) : Result<V, Nothing>
    data class Err<E>(val error: E) : Result<Nothing, E>

    companion object {
        fun <V> catch(block: () -> V): Result<V, ApiError> {
            return try {
                Ok(block())
            } catch(e: ApiError) {
                Err(e)
            } catch (e: Throwable) {
                secureLog.error("Unknown server error", e)
                Err(ApiError(500, "Unknown server error", null, DEFAULT_DOC_STR))
            }
        }
    }

    fun unwrap(): V = when (this) {
        is Ok -> value
        is Err -> error("Called Result.unwrap on an Err value $error")
    }

    fun isOk(): Boolean = when (this) {
        is Ok -> true
        is Err -> false
    }

    fun <U> map(transform: (V) -> U): Result<U, E> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }

    fun <F> mapError(transform: (E) -> F): Result<V, F> = when (this) {
        is Ok -> this
        is Err -> Err(transform(error))
    }

    fun unwrapErr(): E = when (this) {
        is Result.Ok -> error("Called Result.unwrapErr on an Ok")
        is Result.Err -> error
    }

    fun expect(msg: Any): V = when (this) {
        is Result.Ok -> value
        is Result.Err -> error("$msg $error")
    }

    fun <U> fold(success: (V) -> U, failure: (E) -> U): U = when (this) {
        is Result.Ok -> success(value)
        is Result.Err -> failure(error)
    }
}

fun <V, E> Iterable<Result<V, E>>.filterOk(): List<V> = filterIsInstance<Result.Ok<V>>().map { it.value }
fun <V, E> Iterable<Result<V, E>>.filterErr(): List<E> = filterIsInstance<Result.Err<E>>().map { it.error }

fun <V, E, F> Result<V, E>.or(result: Result<V, F>): Result<V, F> = when (this) {
    is Result.Ok -> this
    is Result.Err -> result
}

inline infix fun <V, E> Result<V, E>.onSuccess(action: (V) -> Unit): Result<V, E> {
    if (this is Result.Ok<V>) action(value)
    return this
}

inline infix fun <V, E> Result<V, E>.onFailure(action: (E) -> Unit): Result<V, E> {
    if (this is Result.Err<E>) action(error)
    return this
}

fun <V, E> Result<Result<V, E>, E>.flatten(): Result<V, E> = when (this) {
    is Result.Ok -> value
    is Result.Err -> this
}

