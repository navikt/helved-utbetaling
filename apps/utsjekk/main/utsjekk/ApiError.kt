package utsjekk

import io.ktor.http.HttpStatusCode

sealed class ApiError(override val message: String, val statusCode: HttpStatusCode) : RuntimeException(message) {
    class BadRequest(override val message: String) : ApiError(message, HttpStatusCode.BadRequest)
    class NotFound(override val message: String) : ApiError(message, HttpStatusCode.NotFound)
    class Conflict(override val message: String) : ApiError(message, HttpStatusCode.Conflict)
    class Forbidden(override val message: String) : ApiError(message, HttpStatusCode.Forbidden)
    class Unavailable(override val message: String) : ApiError(message, HttpStatusCode.Forbidden)

    companion object {
        fun badRequest(msg: String): Nothing = throw BadRequest(msg)
        fun notFound(msg: String): Nothing = throw NotFound(msg)
        fun conflict(msg: String): Nothing = throw Conflict(msg)
        fun forbidden(msg: String): Nothing = throw Forbidden(msg)
        fun serviceUnavailable(msg: String): Nothing = throw Unavailable(msg)
    }
}

