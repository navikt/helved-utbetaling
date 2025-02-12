package abetal

sealed interface Result<out V, out E> {
    data class Ok<V>(val value: V) : Result<V, Nothing>
    data class Err<E>(val error: E) : Result<Nothing, E>

    companion object {
        inline fun <V, reified E> catch(block: () -> V): Result<V, E> {
            return try {
                Ok(block())
            } catch(e: Throwable) {
                // throw e.cause ?: e // unwrap original cause from ExecutionException
                Err(e as E)
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
}

fun <V, E> Iterable<Result<V, E>>.filterOk(): List<V> = filterIsInstance<Result.Ok<V>>().map { it.value }
fun <V, E> Iterable<Result<V, E>>.filterErr(): List<E> = filterIsInstance<Result.Err<E>>().map { it.error }

// fun <V: Any, E> Result<V, E>.unwrap(): V = when (this) {
//     is Result.Ok -> value
//     is Result.Err -> error("Called Result.unwrap on an Err value $error")
// }

fun <V, E> Result<V, E>.unwrapErr(): E = when (this) {
    is Result.Ok -> error("Called Result.unwrapErr on an Ok")
    is Result.Err -> error
}

fun <V, E> Result<V, E>.expect(msg: Any): V = when (this) {
    is Result.Ok -> value
    is Result.Err -> error("$msg $error")
}

fun <V, E, F> Result<V, E>.or(result: Result<V, F>): Result<V, F> = when (this) {
    is Result.Ok -> this
    is Result.Err -> result
}

// inline fun <V, E, U> Result<V, E>.map(transform: (V) -> U): Result<U, E> = when (this) {
//     is Result.Ok -> Result.Ok(transform(value))
//     is Result.Err -> this
// }

// inline fun <V, E, F> Result<V, E>.mapError(transform: (E) -> F): Result<V, F> = when (this) {
//     is Result.Ok -> this
//     is Result.Err -> Result.Err(transform(error))
// }

inline infix fun <V, E> Result<V, E>.onSuccess(action: (V) -> Unit): Result<V, E> {
    if (this is Result.Ok<V>) action(value)
    return this
}

inline infix fun <V, E> Result<V, E>.onFailure(action: (E) -> Unit): Result<V, E> {
    if (this is Result.Err<E>) action(error)
    return this
}

inline fun <V, E, U> Result<V, E>.fold(success: (V) -> U, failure: (E) -> U): U = when (this) {
    is Result.Ok -> success(value)
    is Result.Err -> failure(error)
}

fun <V, E> Result<Result<V, E>, E>.flatten(): Result<V, E> = when (this) {
    is Result.Ok -> value
    is Result.Err -> this
}
