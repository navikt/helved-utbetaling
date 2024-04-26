package utsjekk.routing

import io.ktor.server.routing.*

fun Routing.task() {
    route("/api/task") {

        get("/{id}") {
            TODO("not implemented")
        }

        get("/v2") {
            TODO("not implemented")
        }

        get("/callId/{callId}") {
            TODO("not implemented")
        }

        get("/ferdigNaaFeiletFoer") {
            TODO("not implemented")
        }

        get("/antall-til-oppfolging") {
            TODO("not implemented")
        }

        get("antall-feilet-og-manuell-oppfolging") {
            TODO("not implemented")
        }

        get("/logg/{id}") {
            TODO("not implemented")
        }

        put("/rekjor") {
            TODO("not implemented")
        }

        put("/rekjorAlle") {
            TODO("not implemented")
        }

        put("/avvikshaandter") {
            TODO("not implemented")
        }

        put("/kommenter") {
            TODO("not implemented")
        }
    }
}
