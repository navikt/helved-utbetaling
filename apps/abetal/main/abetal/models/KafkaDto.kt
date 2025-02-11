package abetal.models

import abetal.ApiError


enum class Action {
    CREATE,
    UPDATE,
    DELETE
}

data class AapUtbetaling(
    val action: Action,
    val data: Utbetaling,
    var error: ApiError? = null,
)