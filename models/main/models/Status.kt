package models

data class StatusReply(
    val status: Status, 
    val error: ApiError? = null,
)

enum class Status {
    OK,
    FEILET,
    MOTTATT,
    HOS_OPPDRAG,
}

