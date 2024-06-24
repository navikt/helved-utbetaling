package utsjekk.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.Satstype
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsperiode
import utsjekk.badRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

fun Route.utbetalinger() {
    route("/api/utbetalinger?sakid=1&behandling=1") {
        post {
            val sakId = call.parameters["sakId"] ?: badRequest("Mangler required query param 'sakId'")
            val behId = call.parameters["behandlingId"] ?: badRequest("Mangler required query param 'behandlingId'")

            val uid = UID(sakId, behId).also {
                call.response.header("location", "$it")
            }

            val utbetaling = call.receive<Utbetaling>()
            if (utbetaling.id != null) badRequest("field 'id' in body can not be set.")

            when (utbetaling.ref == null) {
                true -> Utbetalinger.new(uid, utbetaling)
                false -> Utbetalinger.chain(uid, utbetaling)
            }

            call.respond(HttpStatusCode.Created)
        }
    }
}

object Utbetalinger {
    fun new(uid: UID, utbetaling: Utbetaling) {
        utbetaling.copy(id = uid).let {
            Utbetalingsoppdrag(
                erFørsteUtbetalingPåSak = true,
                fagsystem = utbetaling.fagsystem(),
                saksnummer = uid.sakId,
                iverksettingId = null, // todo: erstattes av uid
                aktør = utbetaling.personident,
                saksbehandlerId = utbetaling.saksbehandler,
                beslutterId = utbetaling.beslutter,
                avstemmingstidspunkt = LocalDateTime.now(),
                utbetalingsperiode = utbetaling.stønader.mapIndexed { idx, stønad ->
                    Utbetalingsperiode(
                        erEndringPåEksisterendePeriode = false,
                        opphør = null,
                        periodeId = idx.toLong(),
                        forrigePeriodeId = null,
                        vedtaksdato = stønad.vedtaksdato,
                        klassifisering = stønad.type.klassekode(),
                        fom = stønad.fom,
                        tom = stønad.tom,
                        sats = stønad.sats.toBigDecimal(),
                        satstype = stønad.satstype(),
                        utbetalesTil = utbetaling.personident,
                        behandlingId = uid.behandlingId,
                        utbetalingsgrad = null,
                    )
                },
                brukersNavKontor = null,
            )
        }
    }

    fun chain(uid: UID, utbetaling: Utbetaling) {

    }
}

class UID private constructor(
    private val id: UUID,
    val sakId: String,
    val behandlingId: String
) {
    constructor(sakId: String, behandlingId: String) : this(
        id = UUID.randomUUID(),
        sakId = sakId,
        behandlingId = behandlingId
    )

    override fun toString(): String {
        return id.toString()
    }
}

typealias NavIdent = String

data class Utbetaling(
    val id: UID? = null,
    val ref: UID? = null,
    val personident: String,
    val saksbehandler: NavIdent,
    val beslutter: NavIdent,
    val stønader: List<Stønad>,
    val utbetales: LocalDate,
)

sealed class Stønad(
    open val type: StønadType,
    open val sats: Long,
    open val vedtaksdato: LocalDate,
    open val fom: LocalDate,
    open val tom: LocalDate,
) {
    abstract fun satstype(): Satstype

    data class Dag(
        override val type: StønadType,
        override val sats: Long,
        override val vedtaksdato: LocalDate,
        override val fom: LocalDate,
        override val tom: LocalDate,
    ) : Stønad(type, sats, vedtaksdato, fom, tom) {
        override fun satstype(): Satstype = Satstype.DAGLIG
    }

    data class Måned(
        override val type: StønadType,
        override val sats: Long,
        override val vedtaksdato: LocalDate,
        override val fom: LocalDate,
        override val tom: LocalDate,
    ) : Stønad(type, sats, vedtaksdato, fom, tom) {
        override fun satstype(): Satstype = Satstype.MÅNEDLIG
    }

    data class Enkelt(
        override val type: StønadType,
        override val sats: Long,
        override val vedtaksdato: LocalDate,
        override val fom: LocalDate,
        override val tom: LocalDate,
    ) : Stønad(type, sats, vedtaksdato, fom, tom) {
        override fun satstype(): Satstype = Satstype.ENGANGS
    }
}

fun Utbetaling.fagsystem(): Fagsystem =
    when (stønader.first().type) {
        is StønadType.Tilleggstønader -> Fagsystem.TILLEGGSSTØNADER
        is StønadType.Dagpenger -> Fagsystem.DAGPENGER
//        is StønadType.Tiltakspenger -> Fagsystem.TILTAKSPENGER
    }

sealed interface StønadType {
    fun klassekode(): String = when (this) {
        is Tilleggstønader -> klassekode
        is Dagpenger -> klassekode
    }

    enum class Tilleggstønader(val klassekode: String) : StønadType {
        TS_BARN_ENSLIG_FORSØRGER("TSTBASISP2-OP"),
        TS_BARN_AAP("TSTBASISP4-OP"),
        TS_BARN_ETTERLATTE("TSTBASISP5-OP"),
    }

    enum class Dagpenger(val klassekode: String) : StønadType {
        DP_ARBSØKER("DPORAS"),
        DP_ARBSØKER_FERIE("DPORASFE"),
        DP_ARBSØKER_FERIE_AVDØD("DPORASFE-IOP"),
        DP_PERMIT("DPPEAS"),
        DP_PERMIT_FERIE("DPPEASFE1"),
        DP_PERMIT_AVDØD("DPPEASFE1-IOP"),
        DP_PERMIT_FISK("DPPEFI"),
        DP_PERMIT_FISK_FERIE("DPPEFIFE1"),
        DP_PERMIT_FISK_AVDØD("DPPEFIFE1-IOP"),
        DP_EØS("DPDPASISP1"),
        DP_EØS_FERIE("DPFEASISP"),
    }
}


