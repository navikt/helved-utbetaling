package utsjekk

class BadRequest(override val message: String) : RuntimeException(message)
class NotFound(override val message: String) : RuntimeException(message)

fun badRequest(msg: String): Nothing = throw BadRequest(msg)
fun notFound(msg: String): Nothing = throw NotFound(msg)
