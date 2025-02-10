package abetal.models

import abetal.ApiError

data class StatusReply(
    val status: Status = Status.MOTTATT, 
    val error: ApiError? = null,
)

enum class Status {
    OK,
    FEILET,
    MOTTATT,
    HOS_OPPDRAG,
}
