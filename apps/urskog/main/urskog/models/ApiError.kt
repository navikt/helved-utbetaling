package urskog.models

internal const val DEFAULT_DOC_STR = "https://navikt.github.io/utsjekk-docs/"

data class ApiError(
    val statusCode: Int,
    val msg: String,
    val field: String?,
    private val doc: String?,
) : RuntimeException(msg)

fun badRequest(
    msg: String,
    field: String? = null,
    doc: String? = null,
) : Nothing = throw ApiError(400, msg, field, doc)

fun unauthorized(
    msg: String,
    field: String? = null,
    doc: String? = null,
) : Nothing = throw ApiError(401, msg, field, doc)

fun forbidden(
    msg: String,
    field: String? = null,
    doc: String? = null,
) : Nothing = throw ApiError(403, msg, field, doc)

fun notFound(
    msg: String,
    field: String? = null,
    doc: String? = null,
) : Nothing = throw ApiError(404, msg, field, doc)

fun conflict(
    msg: String,
    field: String? = null,
    doc: String? = null,
) : Nothing = throw ApiError(409, msg, field, doc)

fun unprocessable(
    msg: String,
    field: String? = null,
    doc: String? = null,
) : Nothing = throw ApiError(422, msg, field, doc)

fun locked(
    msg: String, 
    field: String? = null,
    doc: String? = null,
) : Nothing = throw ApiError(423, msg, field, doc)

fun internalServerError(
    msg: String, 
    field: String? = null,
    doc: String? = null,
) : Nothing = throw ApiError(500, msg, field, doc)

fun unavailable(
    msg: String, 
    field: String? = null,
    doc: String? = null,
) : Nothing = throw ApiError(503, msg, field, doc)

