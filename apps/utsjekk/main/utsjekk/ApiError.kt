package utsjekk

sealed class ApiError(override val message: String) : RuntimeException(message) {
    class BadRequest(override val message: String) : ApiError(message)
    class NotFound(override val message: String) : ApiError(message)
    class Conflict(override val message: String) : ApiError(message)
    class Forbidden(override val message: String) : ApiError(message)
    class Unavailable(override val message: String) : ApiError(message)

    companion object {
        fun badRequest(msg: String): Nothing = throw BadRequest(msg)
        fun notFound(msg: String): Nothing = throw NotFound(msg)
        fun conflict(msg: String): Nothing = throw Conflict(msg)
        fun forbidden(msg: String): Nothing = throw Forbidden(msg)
        fun serviceUnavailable(msg: String): Nothing = throw Unavailable(msg)
    }
}

