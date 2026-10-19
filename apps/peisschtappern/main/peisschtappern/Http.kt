package peisschtappern

import kotlinx.serialization.SerializationException
import libs.auth.JwtVerifier
import libs.kotlinx.KotlinxJson
import libs.utils.Log
import models.ApiError
import org.http4k.core.*

inline fun <reified T> Request.receive(): T = KotlinxJson.decodeFromString(bodyString())

inline fun <reified T> jsonResponse(value: T, status: Status = Status.OK): Response =
    Response(status).header("Content-Type", "application/json; charset=utf-8").body(KotlinxJson.encodeToString(value))

internal val errorFilter = Filter { next ->
    { request ->
        try {
            next(request)
        } catch (e: ApiError) {
            jsonResponse(e, Status(e.statusCode, ""))
        } catch (e: SerializationException) {
            val msg = "Klarte ikke lese json meldingen. Sjekk at formatet på meldingen din er korrekt, f.eks navn på felter, påkrevde felter, e.l."
            Log.debug(msg, e)
            jsonResponse(ApiError(statusCode = 400, msg = msg), Status.BAD_REQUEST)
        } catch (e: Throwable) {
            val msg = "Ukjent feil, helved er varslet."
            Log.error(msg, e)
            jsonResponse(ApiError(statusCode = 500, msg = msg), Status.INTERNAL_SERVER_ERROR)
        }
    }
}

internal fun azureAuthFilter(verifier: JwtVerifier): Filter = Filter { next ->
    { request ->
        val authorization = request.header("Authorization")
        val token = authorization?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)
        val jwt = token?.let {
            try {
                verifier.verify(it)
            } catch (e: Exception) {
                Log.warn("Token validation failed", e)
                null
            }
        }
        if (jwt == null) {
            Response(Status.UNAUTHORIZED).header("WWW-Authenticate", "Bearer realm=\"azure\"")
        } else {
            next(request.with(auditClaims of jwt.claims))
        }
    }
}