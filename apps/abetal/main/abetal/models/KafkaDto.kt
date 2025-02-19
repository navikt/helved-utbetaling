package abetal.models


enum class Action {
    CREATE,
    UPDATE,
    DELETE
}

data class AapUtbetaling(
    val action: Action,
    val data: Utbetaling,
)

data class SakIdWrapper(val sakId: String, val uids: Set<UtbetalingId>)

data class UtbetalingRequest(
    val action: Action,
    val data: Utbetaling,
)
