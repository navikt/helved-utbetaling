package utsjekk

const val DEFAULT_DOC_STR = "https://helved-docs.ekstern.dev.nav.no/v3/doc/"

class ApiError(
    val statusCode: Int,
    val msg: String,
    val field: String?,
    private val doc: String,
) : RuntimeException(msg) {

    data class Response(
        val msg: String,
        val field: String?,
        val doc: String,
    )

    val asResponse get() = Response(
        this.msg,
        this.field,
        this.doc ?: DEFAULT_DOC_STR,
    )
}

fun badRequest(
    msg: String,
    field: String? = null,
    doc: String = "",
) : Nothing = throw ApiError(400, msg, field, "$DEFAULT_DOC_STR$doc")

fun unauthorized(
    msg: String,
    field: String? = null,
    doc: String = "",
) : Nothing = throw ApiError(401, msg, field, "$DEFAULT_DOC_STR$doc")

fun forbidden(
    msg: String,
    field: String? = null,
        doc: String = "",
) : Nothing = throw ApiError(403, msg, field, "$DEFAULT_DOC_STR$doc")

fun notFound(
    msg: String,
    field: String? = null,
        doc: String = "",
) : Nothing = throw ApiError(404, msg, field, "$DEFAULT_DOC_STR$doc")

fun conflict(
    msg: String,
    field: String? = null,
        doc: String = "",
) : Nothing = throw ApiError(409, msg, field, "$DEFAULT_DOC_STR$doc")

fun unprocessable(
    msg: String,
    field: String? = null,
        doc: String = "",
) : Nothing = throw ApiError(422, msg, field, "$DEFAULT_DOC_STR$doc")

fun locked(
    msg: String, 
    field: String? = null,
        doc: String = "",
) : Nothing = throw ApiError(423, msg, field, "$DEFAULT_DOC_STR$doc")

fun internalServerError(
    msg: String, 
    field: String? = null,
        doc: String = "",
) : Nothing = throw ApiError(500, msg, field, "$DEFAULT_DOC_STR$doc")

fun notImplemented(
    msg: String,
    field: String? = null,
        doc: String = "",
) : Nothing = throw ApiError(501, msg, field, "$DEFAULT_DOC_STR$doc")

fun unavailable(
    msg: String, 
    field: String? = null,
        doc: String = "",
) : Nothing = throw ApiError(503, msg, field, "$DEFAULT_DOC_STR$doc")
