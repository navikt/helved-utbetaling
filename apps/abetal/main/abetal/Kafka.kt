package abetal

import libs.kafka.JsonSerde
import libs.kafka.Topic
import libs.kafka.Topology
import libs.kafka.topology
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

object Topics {
    val dagpenger = Topic<UtbetalingApi>("dp.utbetalinger.v1", JsonSerde.jackson())
    val utbetalinger = Topic<Utbetaling>("helved.utbetalinger.v1", JsonSerde.jackson())
    val oppdrag = Topic<UtbetalingsoppdragDto>("helved.oppdrag.v1", JsonSerde.jackson())
    val status = Topic<UtbetalingStatusApi>("helved.status.v1", JsonSerde.jackson())
}

fun createTopology(): Topology = topology {
    val dagpenger = consume(Topics.dagpenger)
        .map { it -> Utbetaling.from(it) }

    dagpenger
        .produce(Topics.utbetalinger)

    dagpenger
        .map { uid, domain -> UtbetalingsoppdragDto.from(uid, domain) }
        .produce(Topics.oppdrag)

    dagpenger
        .map { _ -> UtbetalingStatusApi(Status.IKKE_PÅBEGYNT) }
        .produce(Topics.status)
}


fun UtbetalingsoppdragDto.Companion.from(uid: String, utbetaling: Utbetaling) =
    UtbetalingsoppdragDto(
        uid = UtbetalingId(UUID.fromString(uid)),
        erFørsteUtbetalingPåSak = true, // TODO: må vi gjøre sql select på sakid for fagområde?
        fagsystem = FagsystemDto.from(utbetaling.stønad),
        saksnummer = utbetaling.sakId.id,
        aktør = utbetaling.personident.ident,
        saksbehandlerId = utbetaling.saksbehandlerId.ident,
        beslutterId = utbetaling.beslutterId.ident,
        avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
        brukersNavKontor = utbetaling.periode.betalendeEnhet?.enhet,
        utbetalingsperiode = UtbetalingsperiodeDto(
            erEndringPåEksisterendePeriode = false,
            opphør = null,
            id = utbetaling.periode.id,
            vedtaksdato = utbetaling.vedtakstidspunkt.toLocalDate(),
            klassekode = klassekode(utbetaling.stønad),
            fom = utbetaling.periode.fom,
            tom = utbetaling.periode.tom,
            sats = utbetaling.periode.beløp,
            satstype = utbetaling.periode.satstype,
            utbetalesTil = utbetaling.personident.ident,
            behandlingId = utbetaling.behandlingId.id,
        )
    )