package abetal

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import libs.kafka.KafkaProducer
import models.*
import no.trygdeetaten.skjema.oppdrag.ObjectFactory
import no.trygdeetaten.skjema.oppdrag.Oppdrag

class FeilregisterAvvent(
    private val oppdragProducer: KafkaProducer<String, Oppdrag>,
) {
    private val objectFactory = ObjectFactory()

    fun route(route: Route) {
        route.post("/utbetalinger/avvent") {
            val request = call.receive<Request>()
            request.validate()

            oppdragProducer.send(
                key = request.key,
                value = request.toOppdrag(),
                headers = mapOf("source" to "abetal-avvent"),
            )
            call.respond(HttpStatusCode.Created)
        }
    }

    private fun Request.validate() {
        if (key.isBlank()) badRequest("key må være satt")
        if (!avvent.feilregistrering) badRequest("feilregistrering må være satt til true")
        if (avvent.overføres == null) badRequest("overføres må være satt")
    }

    private fun Request.toOppdrag(): Oppdrag {
        val fagsystem = stønad.fagsystem()
        val oppdrag110 = objectFactory.createOppdrag110().apply {
            kodeAksjon = "1"
            kodeEndring = "ENDR"
            kodeFagomraade = fagsystem.fagområde
            fagsystemId = sakId
            utbetFrekvens = fagsystem.utbetFrekvens()
            oppdragGjelderId = personident
            datoOppdragGjelderFom = java.time.LocalDate.of(2000, 1, 1).toXMLDate()
            saksbehId = saksbehandlerId
            avvent118 = objectFactory.createAvvent118().apply {
                datoAvventFom = avvent.fom.toXMLDate()
                datoAvventTom = avvent.tom.toXMLDate()
                datoOverfores = avvent.overføres?.toXMLDate()
                avvent.årsak?.let { årsak -> kodeArsak = årsak.kode }
                feilreg = if (avvent.feilregistrering) "J" else "N"
            }
        }
        return objectFactory.createOppdrag().apply {
            this.oppdrag110 = oppdrag110
        }
    }

    private fun Stønadstype.fagsystem(): Fagsystem = when (this) {
        is StønadTypeAAP -> Fagsystem.AAP
        is StønadTypeDagpenger -> Fagsystem.DAGPENGER
        is StønadTypeHistorisk -> Fagsystem.HISTORISK
        is StønadTypeTilleggsstønader -> Fagsystem.TILLEGGSSTØNADER
        is StønadTypeTiltakspenger -> Fagsystem.TILTAKSPENGER
        is StønadTypeValp -> badRequest("$this tilhører et ukjent fagsystem")
    }

    @Serializable
    data class Request(
        val key: String,
        val stønad: Stønadstype,
        val sakId: String,
        val personident: String,
        val saksbehandlerId: String,
        val avvent: Avvent,
    )
}
