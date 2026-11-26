package peisschtappern

import libs.kotlinx.KotlinxJson
import org.http4k.core.ContentType
import org.http4k.core.Request
import org.http4k.core.Response

fun Request.bearerAuth(token: String): Request = header("Authorization", "Bearer $token")

fun Request.acceptJson(): Request = header("Accept", ContentType.APPLICATION_JSON.value)

inline fun <reified T> Request.json(body: T): Request =
    header("Content-Type", ContentType.APPLICATION_JSON.value).body(KotlinxJson.encodeToString(body))

inline fun <reified T> Response.bodyAs(): T = KotlinxJson.decodeFromString(bodyString())
