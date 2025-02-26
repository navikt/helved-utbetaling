package models

const val DOC = "https://navikt.github.io/utsjekk-docs/"

data class ApiError(
    val statusCode: Int,
    val msg: String,
    val doc: String = DOC,
) : RuntimeException(msg)

fun badRequest(msg: String, doc: String = "") : Nothing          = throw ApiError(400, msg, "$DOC$doc")
fun unauthorized(msg: String, doc: String = "") : Nothing        = throw ApiError(401, msg, "$DOC$doc")
fun forbidden(msg: String, doc: String = "") : Nothing           = throw ApiError(403, msg, "$DOC$doc")
fun notFound(msg: String, doc: String = "") : Nothing            = throw ApiError(404, msg, "$DOC$doc")
fun conflict(msg: String, doc: String = "") : Nothing            = throw ApiError(409, msg, "$DOC$doc")
fun unprocessable(msg: String, doc: String = "") : Nothing       = throw ApiError(422, msg, "$DOC$doc")
fun locked(msg: String, doc: String = "") : Nothing              = throw ApiError(423, msg, "$DOC$doc")
fun internalServerError(msg: String, doc: String = "") : Nothing = throw ApiError(500, msg, "$DOC$doc")
fun unavailable(msg: String, doc: String = "") : Nothing         = throw ApiError(503, msg, "$DOC$doc")

