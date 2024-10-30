package utsjekk

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.runBlocking

private val CALL_START_TIME = AttributeKey<Long>("CallStartTime")

class CallLogConfig {
    var clock: () -> Long = { getTimeMillis() }
    var exclusion: (ApplicationCall) -> Boolean = { false }
    var action: (ApplicationCall) -> Unit = {}

    fun exclude(filter: (ApplicationCall) -> Boolean) {
        exclusion = filter
    }

    fun log(block: (ApplicationCall) -> Unit) {
        action = block
    }
}

val CallLog = createApplicationPlugin("CallLog", ::CallLogConfig) {
    on(CallSetup) { call ->
        call.attributes.put(CALL_START_TIME, pluginConfig.clock())
    }

    on(ResponseSent) { call ->
        if (!pluginConfig.exclusion(call)) {
            pluginConfig.action(call)
        }
    }
}

fun ApplicationCall.processingTimeMs(clock: () -> Long = { getTimeMillis() }): Long {
    val startTime = attributes[CALL_START_TIME]
    return clock() - startTime
}

fun ApplicationCall.bodyAsText(): String = runBlocking { receiveText() }
