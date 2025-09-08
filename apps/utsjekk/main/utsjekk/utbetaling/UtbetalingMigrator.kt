package utsjekk.utbetaling

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.kafka.Streams
import models.AapUtbetaling
import models.AapUtbetalingsdag
import utsjekk.*
import java.util.*

class UtbetalingMigrator(config: Config, kafka: Streams): AutoCloseable {
    val utbetalingProducer = kafka.createProducer(config.kafka, Topics.utbetalingAap)

    fun route(route: Route) {
        route.route("/utbetalinger/{uid}/migrate") {
            post {
                val uid = call.parameters["uid"]
                    ?.let(::uuid)
                    ?.let(::UtbetalingId)
                    ?: badRequest("parameter mangler", "uid")

                val meldeperiode = call.receive<String>()
                transfer(uid, meldeperiode)
                call.respond(HttpStatusCode.OK)
            }
        }
    }

    suspend fun transfer(uid: UtbetalingId, meldeperiode: String) {
        withContext(Jdbc.context) {
            transaction { 
                val utbet = UtbetalingDao.findOrNull(uid) ?: notFound("utbetaling $uid")
                val aapUtbet = aapUtbetaling(uid, meldeperiode, utbet)
                val key = uid.id.toString()
                utbetalingProducer.send(key, aapUtbet, partition(key))
            }
        }
    }

    override fun close() {
        utbetalingProducer.close()
    }

    private fun aapUtbetaling(
        uid: UtbetalingId,
        meldeperiode: String,
        dao: UtbetalingDao,
    ) = AapUtbetaling(
        dryrun = false,
        sakId = dao.data.sakId.id,
        behandlingId = dao.data.behandlingId.id,
        ident = dao.data.personident.ident,
        utbetalinger = dao.data.perioder.map { aapUtbetalingsdag(meldeperiode, it) },
        vedtakstidspunktet = dao.data.vedtakstidspunkt,
    )

    private fun aapUtbetalingsdag(
        meldeperiode: String,
        periode: Utbetalingsperiode,
    ) = AapUtbetalingsdag(
        meldeperiode = meldeperiode,
        dato = periode.fom, // er periode.fom == periode.tom ?
        utbetaltBeløp = periode.beløp,
        sats = periode.fastsattDagsats ?: periode.beløp,
    )
}

private fun uuid(str: String): UUID {
    return try {
        UUID.fromString(str)
    } catch (e: Exception) {
        badRequest("path param 'uid' must be UUIDv4")
    }
}

