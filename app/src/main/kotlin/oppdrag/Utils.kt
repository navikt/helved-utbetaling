package oppdrag

import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun envVar(variable: String) =
    System.getenv(variable)
        ?: error("missing envVar $variable")


val logger: Logger = LoggerFactory.getLogger("oppdrag")