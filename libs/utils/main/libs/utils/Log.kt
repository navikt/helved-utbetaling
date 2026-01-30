package libs.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun logger(name: String): Logger = LoggerFactory.getLogger(name)

val secureLog: Logger = logger("secureLog")
val appLog: Logger = logger("appLog")
val jdbcLog: Logger = logger("jdbc")
