package peisschtappern

import libs.auth.Jwt
import org.http4k.core.Request
import org.http4k.lens.RequestKey

internal val auditClaims = RequestKey.required<Jwt.Claims>("auditClaims")

data class Audit(
    val ident: String,
    val name: String,
    val email: String,
    val reason: String?,
) {
    companion object {
        fun from(request: Request, reason: String? = null): Audit {
            val claims = auditClaims(request)
            return Audit(
                name = claims.claim("name") ?: "test",
                ident = claims.claim("NAVident") ?: "test",
                email = claims.claim("preferred_username") ?: "test",
                reason = reason
            )
        }
    }

    override fun toString(): String =
        if (reason != null) {
            "name:$name email:$email ident:$ident reason:$reason"
        } else {
            "name:$name email:$email ident:$ident"
        }
}
