package models

import libs.utils.secureLog
import libs.utils.appLog


sealed interface Result<out V, out E> {
    data class Ok<V>(val value: V) : Result<V, Nothing>
    data class Err<E>(val error: E) : Result<Nothing, E>

    companion object {
        fun <V> catch(block: () -> V): Result<V, StatusReply> {
            return try {
                Ok(block())
            } catch(e: ApiError) {
                Err(StatusReply(status = Status.FEILET, error = e))
            } catch (e: Throwable) {
                val msg = "Result.catch failed with an unknown throwable"
                appLog.error(msg)
                secureLog.error(msg, e)
                val error = ApiError(500, msg)
                Err(StatusReply(status = Status.FEILET, error = error))
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

    fun isErr(): Boolean = !isOk()

    fun <U> map(transform: (V) -> U): Result<U, E> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }

    fun <F> mapError(transform: (E) -> F): Result<V, F> = when (this) {
        is Ok -> this
        is Err -> Err(transform(error))
    }

    fun unwrapErr(): E = when (this) {
        is Ok -> error("Called Result.unwrapErr on an Ok")
        is Err -> error
    }

    fun expect(msg: Any): V = when (this) {
        is Ok -> value
        is Err -> error("$msg $error")
    }

    fun <U> fold(success: (V) -> U, failure: (E) -> U): U = when (this) {
        is Ok -> success(value)
        is Err -> failure(error)
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

