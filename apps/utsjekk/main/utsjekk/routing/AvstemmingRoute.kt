package utsjekk.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.tilFagsystem
import utsjekk.BadRequest

fun Routing.avstemming() {

    route("/intern/avstemming") {

        post("start/{fagsystem}") {

        }
    }
}

@Suppress("NAME_SHADOWING")
private fun ApplicationCall.fagsystem(): Fagsystem {
    val param = parameters["fagsystem"] ?: throw BadRequest("parameter 'fagsystem' mangler i url")
    return param.tilFagsystem()
}


