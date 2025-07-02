package libs.utils

public sealed interface Result<out V, out E> {
    companion object {
        inline fun <V, reified E> catch(block: () -> V): Result<V, E> {
            return try {
                Ok(block())
            } catch (e: Throwable) {
                Err(e as E)
            }
        }
    }
}

public data class Ok<V>(val value: V): Result<V, Nothing>
public data class Err<E>(val err: E): Result<Nothing, E>

class ResultException(msg: String): RuntimeException(msg)
private fun err(msg: String): Nothing = throw ResultException(msg)

fun <E, V> Result<V, E>.unwrap(): V = when (this) {
    is Ok -> value
    is Err -> err("called Result.unwrap() on an Err: $err")
}

fun <V, E> Result<V, E>.expect(msg: Any): V = when (this) {
    is Ok -> value
    is Err -> err("$msg: $err")
}

fun <U, V, E> Result<V, E>.map(transform: (V) -> U): Result<U, E> = when (this) {
    is Ok -> Ok(transform(value))
    is Err -> this
}

fun <U, V, E> Result<V, E>.mapErr(transform: (E) -> U): Result<V, U> = when (this) {
    is Ok -> this
    is Err -> Err(transform(err))
}

fun <V, E, F> Result<V, E>.or(result: Result<V, F>): Result<V, F> = when (this) {
    is Ok -> this
    is Err -> result
}

inline infix fun <V, E> Result<V, E>.onSuccess(action: (V) -> Unit): Result<V, E> {
    if (this is Ok<V>) action(value)
    return this
}

inline infix fun <V, E> Result<V, E>.onFailure(action: (E) -> Unit): Result<V, E> {
    if (this is Err<E>) action(err)
    return this
}

inline fun <V, E, U> Result<V, E>.fold(success: (V) -> U, failure: (E) -> U): U = when(this) {
    is Ok -> success(value)
    is Err -> failure(err)
}

fun <V, E> Result<Result<V, E>, E>.flatten(): Result<V, E> = when (this) {
    is Ok -> value
    is Err -> this
} 

fun <V, E> Iterable<Result<V, E>>.filterOk(): List<V> = filterIsInstance<Ok<V>>().map { it.value }
fun <V, E> Iterable<Result<V, E>>.filterErr(): List<E> = filterIsInstance<Err<E>>().map { it.err }

