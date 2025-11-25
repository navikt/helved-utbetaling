package models

import com.fasterxml.jackson.annotation.JsonIgnore
import models.kontrakter.felles.GyldigBehandlingId
import models.kontrakter.felles.GyldigSakId

object DocumentedErrors {
    const val BASE = "https://helved-docs.ansatt.dev.nav.no"
    const val URL = "$BASE/async"

    sealed interface Async {
        val msg: String
        val doc: String

        enum class Utbetaling(override val msg: String, override val doc: String) : Async {
            PERSONEN_FINNES_IKKE(
                "Personen finnes ikke",
                "$URL/kom_i_gang/opprett_utbetaling#personen-finnes-ikke"
            ),
            MANGLER_PERIODER(
                "Mangler perioder",
                "$URL/kom_i_gang/opprett_utbetaling#mangler-perioder"
            ),
            ENGANGS_OVER_ÅRSSKIFTE(
                "Engangsutbetalinger kan ikke strekke seg over årsskifte",
                "$URL/kom_i_gang/opprett_utbetaling#engangs-aarsskifte"
            ),
            DUPLIKATE_PERIODER(
                "Kan ikke sende inn duplikate perioder",
                "$URL/kom_i_gang/opprett_utbetaling#duplikate-perioder"
            ),
            UGYLDIG_PERIODE(
                "Tom må være >= fom",
                "$URL/kom_i_gang/opprett_utbetaling#ugyldig-periode"
            ),
            UGYLDIG_SAK_ID(
                "Sak-ID må være mellom 1 og ${GyldigSakId.MAKSLENGDE} tegn",
                "$URL/kom_i_gang/opprett_utbetaling#ugyldig-sak-id"
            ),
            UGYLDIG_BEHANDLING_ID(
                "Behandling-ID må være mellom 1 og ${GyldigBehandlingId.MAKSLENGDE} tegn",
                "$URL/kom_i_gang/opprett_utbetaling#ugyldig-behandling-id"
            ),
            UGYLDIG_BELØP(
                "Beløp må være > 0",
                "$URL/kom_i_gang/opprett_utbetaling#ugyldig-belop"
            ),
            FREMTIDIG_UTBETALING(
                "Fremtidige utbetalinger er ikke støttet for dag/ukedag",
                "$URL/kom_i_gang/opprett_utbetaling#fremtidig-utbetaling"
            ),
            FOR_LANG_UTBETALING(
                "Utbetalinger kan ikke strekke seg over 1000 dager",
                "$URL/kom_i_gang/opprett_utbetaling#for-lang-utbetaling"
            ),
            IMMUTABLE_FIELD_SAK_ID(
                "Kan ikke endre 'sakId'",
                "$URL/kom_i_gang/endre_utbetaling#immutable-field"
            ),
            IMMUTABLE_FIELD_PERSONIDENT(
                "Kan ikke endre 'personident'",
                "$URL/kom_i_gang/endre_utbetaling#immutable-field"
            ),
            IMMUTABLE_FIELD_STØNAD(
                "Kan ikke endre 'stønad'",
                "$URL/kom_i_gang/endre_utbetaling#immutable-field"
            ),
            IMMUTABLE_FIELD_PERIODETYPE(
                "Kan ikke endre 'periodetype'",
                "$URL/kom_i_gang/endre_utbetaling#immutable-field"
            ),
            MINIMUM_CHANGES(
                "Ingen reell endring",
                "$URL/kom_i_gang/endre_utbetaling#minimum-changes"
            )
        }

        enum class Simulering(override val msg: String, override val doc: String) : Async {
            SIMULERING_ER_STENGT(
                "Simulering er stengt",
                "$URL/kom_i_gang/simuler_en_utbetaling#simulering-er-stengt"
            ),
            PERSONEN_FINNES_IKKE(
                "Personen finnes ikke",
                "$URL/kom_i_gang/simuler_en_utbetaling#personen-finnes-ikke"
            )
        }
    }
}

enum class System {
    HELVED,
    OSUR
}

data class ApiError(
    val statusCode: Int,
    val msg: String,
    val doc: String? = DocumentedErrors.BASE,
    val system: System? = System.HELVED,
) : RuntimeException(msg) {
    @JsonIgnore
    override fun getStackTrace(): Array<StackTraceElement> = super.getStackTrace()

    @JsonIgnore
    override val cause: Throwable? = super.cause

    @JsonIgnore
    override fun getLocalizedMessage(): String = super.getLocalizedMessage()

    @JsonIgnore
    override val message: String? = super.message
}

fun badRequest(msg: String, doc: String? = null): Nothing = throw ApiError(400, msg, doc ?: DocumentedErrors.BASE)
fun badRequest(error: DocumentedErrors.Async): Nothing = throw ApiError(400, error.msg, error.doc)

fun forbidden(msg: String, doc: String? = null): Nothing = throw ApiError(403, msg, doc ?: DocumentedErrors.BASE)

fun notFound(msg: String, doc: String? = null): Nothing = throw ApiError(404, msg, doc ?: DocumentedErrors.BASE)
fun notFound(error: DocumentedErrors.Async): Nothing = throw ApiError(404, error.msg, error.doc)

fun conflict(msg: String, doc: String? = null): Nothing = throw ApiError(409, msg, doc ?: DocumentedErrors.BASE)
fun conflict(error: DocumentedErrors.Async): Nothing = throw ApiError(409, error.msg, error.doc)

fun unprocessable(msg: String, doc: String? = null): Nothing = throw ApiError(422, msg, doc ?: DocumentedErrors.BASE)
fun locked(msg: String, doc: String? = null): Nothing = throw ApiError(423, msg, doc ?: DocumentedErrors.BASE)
fun internalServerError(msg: String, doc: String? = null): Nothing = throw ApiError(500, msg, doc ?: DocumentedErrors.BASE)
fun notImplemented(msg: String, doc: String? = null): Nothing = throw ApiError(501, msg, doc ?: DocumentedErrors.BASE)
fun badGateway(msg: String, doc: String? = null): Nothing = throw ApiError(502, msg, doc ?: DocumentedErrors.BASE)

fun unavailable(msg: String, doc: String? = null): Nothing = throw ApiError(503, msg, doc ?: DocumentedErrors.BASE)

fun unauthorized(msg: String): Nothing = throw ApiError(
    statusCode = 401,
    msg = msg,
    doc = DocumentedErrors.BASE + "async/kom_i_gang/oppsett",
)
