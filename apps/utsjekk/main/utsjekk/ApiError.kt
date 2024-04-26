package utsjekk

import io.ktor.http.*

sealed class ApiError(
    val code: HttpStatusCode,
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException()

class BadRequest(msg: String) : ApiError(HttpStatusCode.BadRequest, msg)
