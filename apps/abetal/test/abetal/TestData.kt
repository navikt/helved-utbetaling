package abetal

import abetal.models.*
import java.time.LocalDate
import java.time.LocalDateTime

object TestData {
    var nextSakId: Int = 0
        get() = field++

    fun aapUtbetaling(
        action: Action,
        stønad: StønadTypeAAP,
        periodetype: Periodetype,
        perioder: List<Utbetalingsperiode>,
        sakId: SakId = SakId("$nextSakId"),
        behandlingId: BehandlingId = BehandlingId(""),
        personident: Personident = Personident(""),
        vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        beslutterId: Navident = Navident(""),
        saksbehandlerId: Navident = Navident(""),
    ) = AapUtbetaling(
        action = action,
        sakId = sakId,
        behandlingId = behandlingId,
        personident = personident,
        vedtakstidspunkt = vedtakstidspunkt,
        stønad = stønad,
        beslutterId = beslutterId,
        saksbehandlerId = saksbehandlerId,
        periodetype = periodetype,
        perioder = perioder
    )

    fun utbetalingsperiode(
        fom: LocalDate,
        tom: LocalDate,
        beløp: UInt = 123u,
        fastsattDagsats: UInt? = null
    ) = Utbetalingsperiode(
        fom = fom,
        tom = tom,
        beløp = beløp,
        fastsattDagsats = fastsattDagsats,
    )

    fun dag(dato: LocalDate, beløp: UInt = 123u, fastsattDagsats: UInt? = 123u) =
        utbetalingsperiode(dato, dato, beløp, fastsattDagsats)
}
