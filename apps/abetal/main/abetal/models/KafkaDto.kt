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
)

data class SakIdWrapper(val sakId: String, val uids: List<UtbetalingId>)

data class UtbetalingRequest(
    val action: Action,
    val data: Utbetaling,
)
